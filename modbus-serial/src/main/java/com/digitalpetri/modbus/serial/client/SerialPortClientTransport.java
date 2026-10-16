package com.digitalpetri.modbus.serial.client;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.Accumulated;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.ParserState;
import com.digitalpetri.modbus.client.ModbusRtuClientTransport;
import com.digitalpetri.modbus.exceptions.ModbusConnectException;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.exceptions.ModbusNotConnectedException;
import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import com.digitalpetri.modbus.serial.SerialPortTransportConfig;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus RTU/Serial client transport; a {@link ModbusRtuClientTransport} that sends and receives
 * {@link ModbusRtuFrame}s over a serial port.
 *
 * <p>The transport reports connection loss through {@link #onConnectionLost(Supplier)} when the
 * open port goes away, e.g. a USB serial adapter is unplugged, and when {@link #disconnect()}
 * closes it.
 */
public class SerialPortClientTransport implements ModbusRtuClientTransport {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ModbusRtuResponseFrameParser frameParser = new ModbusRtuResponseFrameParser();
  private final AtomicReference<Consumer<ModbusRtuFrame>> frameReceiver = new AtomicReference<>();

  private final AtomicReference<Supplier<Runnable>> connectionLostCallback =
      new AtomicReference<>();

  private final ExecutionQueue executionQueue;
  private final ExecutionQueue writeQueue;

  private volatile SerialPort serialPort;

  /**
   * The port jSerialComm reported disconnected, until it's closed and detached from this transport.
   *
   * <p>jSerialComm keeps reporting a port open until it's closed, so a port that went away still
   * answers {@code isOpen()} with {@code true}. This transport treats it as not connected as soon
   * as the loss is reported, and closes it on the executor rather than on jSerialComm's event
   * thread.
   */
  private volatile SerialPort lostPort;

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
    SerialPort lost = this.lostPort;
    if (lost != null) {
      // The port went away and hasn't been closed yet. Close it now so a new one is opened.
      closeLostPort(lost);
    }

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
   * <p>Closing an open port reports connection loss through the {@link #onConnectionLost(Supplier)}
   * callback, so requests waiting for a response are failed rather than left to time out.
   *
   * <p>The returned {@link CompletionStage} may complete exceptionally with a {@link
   * ModbusException} if the serial port could not be closed.
   */
  @Override
  public synchronized CompletableFuture<Void> disconnect() {
    SerialPort sp = this.serialPort;
    if (sp == null) {
      return CompletableFuture.completedFuture(null);
    }

    if (sp == lostPort) {
      // Loss was already reported when the port went away; just make sure it's closed.
      closeLostPort(sp);
      return CompletableFuture.completedFuture(null);
    }

    if (sp.isOpen()) {
      if (sp.closePort()) {
        frameParser.reset();

        // Create a new SerialPort on the next connect(). Writes still queued for this one then
        // fail instead of being sent on the reopened port.
        this.serialPort = null;

        notifyConnectionLost();

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
    return isConnected(this.serialPort);
  }

  @Override
  public void onConnectionLost(Supplier<Runnable> callback) {
    connectionLostCallback.set(callback);
  }

  /**
   * Handle jSerialComm reporting that {@code port} went away.
   *
   * <p>Called on jSerialComm's event thread. It must not close the port or synchronize on this
   * transport: {@link SerialPort#closePort()} waits for the event thread to return, so doing either
   * here while another thread is in {@link #disconnect()} would deadlock. Instead the port is
   * marked lost, so sends fail at once, and closed on the executor.
   */
  private void onPortDisconnected(SerialPort port) {
    if (port != this.serialPort) {
      return;
    }

    lostPort = port;

    notifyConnectionLost();

    try {
      config.executor().execute(() -> closeLostPort(port));
    } catch (Exception e) {
      // e.g. RejectedExecutionException if the executor has been shut down
      logger.warn("Failed to schedule closing disconnected port '{}'", config.serialPort(), e);
    }
  }

  /**
   * Close {@code lost} and detach it from this transport, if it's still this transport's port.
   *
   * <p>A write stalled on the port is released by closing it.
   */
  private synchronized void closeLostPort(SerialPort lost) {
    if (this.serialPort == lost) {
      if (!lost.closePort()) {
        logger.warn(
            "Failed to close disconnected port '{}', lastErrorCode={}",
            config.serialPort(),
            lost.getLastErrorCode());
      }
      frameParser.reset();
      this.serialPort = null;
    }
    if (this.lostPort == lost) {
      this.lostPort = null;
    }
  }

  /**
   * Capture the requests affected by the loss of the current connection and queue their cleanup to
   * run in order with frame delivery.
   */
  private void notifyConnectionLost() {
    Runnable notification = captureConnectionLoss();
    try {
      executionQueue.submit(
          () -> {
            try {
              notification.run();
            } catch (Exception e) {
              logger.warn("Connection-loss notification failed", e);
            }
          });
    } catch (Exception e) {
      // e.g. RejectedExecutionException if the executor has been shut down
      logger.warn("Failed to queue connection-loss notification", e);
    }
  }

  private Runnable captureConnectionLoss() {
    Supplier<Runnable> callback = connectionLostCallback.get();
    if (callback != null) {
      try {
        return callback.get();
      } catch (Exception e) {
        logger.warn("Connection-loss capture failed", e);
      }
    }
    return () -> {};
  }

  /**
   * {@inheritDoc}
   *
   * <p>The frame is written to the serial port on the configured executor, so this method does not
   * block the caller if the write stalls. Writes are performed serially, in the order submitted.
   *
   * <p>If the returned {@link CompletionStage} is cancelled before its write starts, the frame is
   * not written. A write that has started can't be stopped, so cancelling it fails, and the {@link
   * CompletionStage} completes when the write does. A write that stalls indefinitely is released by
   * {@link #disconnect()}.
   *
   * <p>The returned {@link CompletionStage} completes exceptionally with a {@link
   * ModbusNotConnectedException} if the transport is not connected when the frame is sent or when
   * its write would start, and with a {@link ModbusException} if writing to the serial port fails.
   */
  @Override
  public CompletionStage<Void> send(ModbusRtuFrame frame) {
    SerialPort sp = this.serialPort;
    if (!isConnected(sp)) {
      return CompletableFuture.failedFuture(new ModbusNotConnectedException());
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

    var future = new WriteFuture();

    try {
      writeQueue.submit(
          () -> {
            if (!future.startWrite()) {
              // Cancelled, e.g. the request timed out while this write was queued.
              return;
            }
            if (!isConnected(sp)) {
              // The port was closed or went away while this write was queued.
              future.completeExceptionally(new ModbusNotConnectedException());
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

  /** Whether {@code sp} is open and hasn't been reported disconnected. */
  private boolean isConnected(SerialPort sp) {
    return sp != null && sp != lostPort && sp.isOpen();
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
    private static final int LISTENING_EVENTS =
        SerialPort.LISTENING_EVENT_DATA_RECEIVED | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;

    /**
     * Set when the port's disconnection has been reported. jSerialComm repeats the event until the
     * port is closed.
     */
    private final AtomicBoolean disconnected = new AtomicBoolean(false);

    @Override
    public int getListeningEvents() {
      return LISTENING_EVENTS;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
      int eventType = event.getEventType();

      if ((eventType & SerialPort.LISTENING_EVENT_DATA_RECEIVED) != 0) {
        onDataReceived(event);
      }

      if ((eventType & SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) != 0
          && disconnected.compareAndSet(false, true)) {
        onPortDisconnected(event.getSerialPort());
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
   * A send future that can only be cancelled before its write starts.
   *
   * <p>A write in progress can't be stopped. If cancelling it succeeded, the frame would still be
   * written after the future reported it cancelled, and the caller couldn't tell when the write
   * finished. Instead, cancelling fails, and the future completes when the write does. Unlike
   * {@link CompletableFuture#cancel(boolean)}, a cancel that fails leaves this future incomplete.
   */
  private static final class WriteFuture extends CompletableFuture<Void> {

    /** Set by whichever comes first: the write starting, or this future being cancelled. */
    private final AtomicBoolean claimed = new AtomicBoolean(false);

    /**
     * Claim this future for its write.
     *
     * @return {@code true} if the write may start, or {@code false} if this future was cancelled or
     *     otherwise completed first.
     */
    boolean startWrite() {
      return claimed.compareAndSet(false, true) && !isDone();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (claimed.compareAndSet(false, true)) {
        return super.cancel(mayInterruptIfRunning);
      } else {
        // The write has started, or this future was already cancelled.
        return isCancelled();
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
