package com.digitalpetri.modbus.serial.client;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.Accumulated;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.ParserState;
import com.digitalpetri.modbus.client.ModbusRtuClientTransport;
import com.digitalpetri.modbus.exceptions.ModbusConnectException;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import com.digitalpetri.modbus.serial.SerialPortTransportConfig;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Modbus RTU/Serial client transport; a {@link ModbusRtuClientTransport} that sends and receives
 * {@link ModbusRtuFrame}s over a serial port.
 */
public class SerialPortClientTransport implements ModbusRtuClientTransport {

  private final ModbusRtuResponseFrameParser frameParser = new ModbusRtuResponseFrameParser();
  private final AtomicReference<Consumer<ModbusRtuFrame>> frameReceiver = new AtomicReference<>();

  private final ExecutionQueue executionQueue;
  private final ExecutionQueue writeQueue;

  private volatile SerialPort serialPort;

  private final SerialPortTransportConfig config;

  public SerialPortClientTransport(SerialPortTransportConfig config) {
    this.config = config;

    executionQueue = new ExecutionQueue(config.executor());
    writeQueue = new ExecutionQueue(config.executor());
  }

  /**
   * Return the underlying {@link SerialPort} used by this transport.
   *
   * <p>The serial port is lazily instantiated on first access. After {@link #disconnect()} closes
   * the port, the next access creates a new instance, so changes made to the previous instance are
   * not carried over.
   *
   * @return the configured {@link SerialPort} instance.
   * @throws ModbusException if the serial port could not be created.
   */
  public SerialPort getSerialPort() throws ModbusException {
    SerialPort sp = this.serialPort;
    if (sp == null) {
      synchronized (this) {
        sp = this.serialPort;
        if (sp == null) {
          try {
            sp = SerialPort.getCommPort(config.serialPort());

            sp.setComPortParameters(
                config.baudRate(),
                config.dataBits(),
                config.stopBits(),
                config.parity(),
                config.rs485Mode());

            if (config.rs485Mode()) {
              sp.setRs485ModeParameters(
                  true,
                  config.rs485RtsActiveHigh(),
                  config.rs485Termination(),
                  config.rs485RxDuringTx(),
                  config.rs485DelayBefore(),
                  config.rs485DelayAfter());
            }

            this.serialPort = sp;
          } catch (Exception e) {
            throw new ModbusException(
                "failed to get comm port '%s'".formatted(config.serialPort()), e);
          }
        }
      }
    }
    return sp;
  }

  @Override
  public synchronized CompletableFuture<Void> connect() {
    SerialPort sp;
    try {
      sp = getSerialPort();
    } catch (ModbusException e) {
      return CompletableFuture.failedFuture(new ModbusConnectException(e.getMessage(), e));
    }

    if (sp.isOpen()) {
      return CompletableFuture.completedFuture(null);
    } else {
      if (sp.openPort()) {
        frameParser.reset();

        // note: no-op if already added from previous connect()
        sp.addDataListener(new ModbusRtuDataListener());

        return CompletableFuture.completedFuture(null);
      } else {
        return CompletableFuture.failedFuture(
            new ModbusConnectException(
                "failed to open port '%s', lastErrorCode=%d"
                    .formatted(config.serialPort(), sp.getLastErrorCode())));
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>The returned {@link CompletionStage} may complete exceptionally with a {@link
   * ModbusException} if the serial port could not be closed.
   */
  @Override
  public synchronized CompletableFuture<Void> disconnect() {
    SerialPort sp = this.serialPort;
    if (sp != null && sp.isOpen()) {
      if (sp.closePort()) {
        frameParser.reset();

        // Create a new SerialPort on the next connect(). Writes still queued for this one then
        // fail instead of being sent on the reopened port.
        this.serialPort = null;

        return CompletableFuture.completedFuture(null);
      } else {
        return CompletableFuture.failedFuture(
            new ModbusException(
                "failed to close port '%s', lastErrorCode=%d"
                    .formatted(config.serialPort(), sp.getLastErrorCode())));
      }
    } else {
      return CompletableFuture.completedFuture(null);
    }
  }

  @Override
  public boolean isConnected() {
    SerialPort sp = this.serialPort;
    return sp != null && sp.isOpen();
  }

  /**
   * {@inheritDoc}
   *
   * <p>The frame is written to the serial port on the configured executor, so this method does not
   * block the caller if the write stalls. Writes are performed serially, in the order submitted.
   *
   * <p>If the returned {@link CompletionStage} is cancelled before its write starts, the frame is
   * not written. A write that stalls indefinitely is released by {@link #disconnect()}.
   *
   * <p>The returned {@link CompletionStage} may complete exceptionally with a {@link
   * ModbusException} if the transport is not connected or if writing to the serial port fails.
   */
  @Override
  public CompletionStage<Void> send(ModbusRtuFrame frame) {
    SerialPort sp = this.serialPort;
    if (sp == null || !sp.isOpen()) {
      return CompletableFuture.failedFuture(new ModbusException("not connected"));
    }

    ByteBuffer buffer = ByteBuffer.allocate(256);

    try {
      buffer.put((byte) frame.unitId());
      buffer.put(frame.pdu());
      buffer.put(frame.crc());
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }

    byte[] data = new byte[buffer.position()];
    buffer.flip();
    buffer.get(data);

    var future = new CompletableFuture<Void>();

    try {
      writeQueue.submit(
          () -> {
            if (future.isDone()) {
              // Cancelled, e.g. the request timed out while this write was queued.
              return;
            }
            try {
              writeFully(sp, data);
              future.complete(null);
            } catch (Exception e) {
              future.completeExceptionally(e);
            }
          });
    } catch (Exception e) {
      // e.g. RejectedExecutionException if the executor has been shut down
      future.completeExceptionally(e);
    }

    return future;
  }

  private void writeFully(SerialPort sp, byte[] data) throws ModbusException {
    int totalWritten = 0;
    while (totalWritten < data.length) {
      int written = sp.writeBytes(data, data.length - totalWritten, totalWritten);
      if (written <= 0) {
        int errorCode = sp.getLastErrorCode();
        throw new ModbusException(
            "failed to write to port '%s', written=%d, lastErrorCode=%d"
                .formatted(config.serialPort(), written, errorCode));
      }
      totalWritten += written;
    }
  }

  @Override
  public void receive(Consumer<ModbusRtuFrame> frameReceiver) {
    this.frameReceiver.set(frameReceiver);
  }

  @Override
  public void resetFrameParser() {
    frameParser.reset();
  }

  private class ModbusRtuDataListener implements SerialPortDataListener {

    /** Bit mask indicating what events we're interested in. */
    private static final int LISTENING_EVENTS = SerialPort.LISTENING_EVENT_DATA_RECEIVED;

    @Override
    public int getListeningEvents() {
      return LISTENING_EVENTS;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
      if ((event.getEventType() & LISTENING_EVENTS) == LISTENING_EVENTS) {
        onDataReceived(event);
      }
    }

    private void onDataReceived(SerialPortEvent event) {
      byte[] receivedData = event.getReceivedData();

      ParserState state = frameParser.parse(receivedData);

      if (state instanceof Accumulated a) {
        try {
          onFrameReceived(a.frame());
        } finally {
          frameParser.reset();
        }
      }
    }

    private void onFrameReceived(ModbusRtuFrame frame) {
      Consumer<ModbusRtuFrame> frameReceiver = SerialPortClientTransport.this.frameReceiver.get();
      if (frameReceiver != null) {
        executionQueue.submit(() -> frameReceiver.accept(frame));
      }
    }
  }

  /**
   * Create a new {@link SerialPortClientTransport} with a callback that allows customizing the
   * configuration.
   *
   * @param configure a {@link Consumer} that accepts a {@link SerialPortTransportConfig.Builder}
   *     instance to configure.
   * @return a new {@link SerialPortClientTransport}.
   */
  public static SerialPortClientTransport create(
      Consumer<SerialPortTransportConfig.Builder> configure) {

    var builder = new SerialPortTransportConfig.Builder();
    configure.accept(builder);
    return new SerialPortClientTransport(builder.build());
  }
}
