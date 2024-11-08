package com.digitalpetri.modbus.serial.client;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.Accumulated;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.ParserState;
import com.digitalpetri.modbus.client.ModbusRtuClientTransport;
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

  private final SerialPort serialPort;

  private final SerialPortTransportConfig config;

  public SerialPortClientTransport(SerialPortTransportConfig config) {
    this.config = config;

    serialPort = SerialPort.getCommPort(config.serialPort());

    serialPort.setComPortParameters(
        config.baudRate(),
        config.dataBits(),
        config.stopBits(),
        config.parity(),
        config.rs485Mode()
    );

    executionQueue = new ExecutionQueue(config.executor());
  }

  @Override
  public synchronized CompletableFuture<Void> connect() {
    if (serialPort.isOpen()) {
      return CompletableFuture.completedFuture(null);
    } else {
      if (serialPort.openPort()) {
        frameParser.reset();

        // note: no-op if already added from previous connect()
        serialPort.addDataListener(new ModbusRtuDataListener());

        return CompletableFuture.completedFuture(null);
      } else {
        return CompletableFuture.failedFuture(
            new Exception(
                "failed to open port '%s', lastErrorCode=%d"
                    .formatted(config.serialPort(), serialPort.getLastErrorCode()))
        );
      }
    }
  }

  @Override
  public synchronized CompletableFuture<Void> disconnect() {
    if (serialPort.isOpen()) {
      if (serialPort.closePort()) {
        frameParser.reset();

        return CompletableFuture.completedFuture(null);
      } else {
        return CompletableFuture.failedFuture(
            new Exception(
                "failed to close port '%s', lastErrorCode=%d"
                    .formatted(config.serialPort(), serialPort.getLastErrorCode()))
        );
      }
    } else {
      return CompletableFuture.completedFuture(null);
    }
  }

  @Override
  public boolean isConnected() {
    return serialPort.isOpen();
  }

  @Override
  public CompletionStage<Void> send(ModbusRtuFrame frame) {
    ByteBuffer buffer = ByteBuffer.allocate(256);

    try {
      buffer.put((byte) frame.unitId());
      buffer.put(frame.pdu());
      buffer.put(frame.crc());

      byte[] data = new byte[buffer.position()];
      buffer.flip();
      buffer.get(data);

      int totalWritten = 0;
      while (totalWritten < data.length) {
        int written = serialPort.writeBytes(data, data.length - totalWritten, totalWritten);
        if (written == -1) {
          int errorCode = serialPort.getLastErrorCode();
          throw new Exception(
              "failed to write to port '%s', lastErrorCode=%d"
                  .formatted(config.serialPort(), errorCode));
        }
        totalWritten += written;
      }

      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
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

    /**
     * Bit mask indicating what events we're interested in.
     */
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
   * @param configure a {@link Consumer} that accepts a
   *     {@link SerialPortTransportConfig.Builder} instance to configure.
   * @return a new {@link SerialPortClientTransport}.
   */
  public static SerialPortClientTransport create(
      Consumer<SerialPortTransportConfig.Builder> configure
  ) {

    var builder = new SerialPortTransportConfig.Builder();
    configure.accept(builder);
    return new SerialPortClientTransport(builder.build());
  }

}
