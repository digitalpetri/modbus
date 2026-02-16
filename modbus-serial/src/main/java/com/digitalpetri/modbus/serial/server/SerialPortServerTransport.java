package com.digitalpetri.modbus.serial.server;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.Accumulated;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.ParserState;
import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import com.digitalpetri.modbus.serial.SerialPortTransportConfig;
import com.digitalpetri.modbus.serial.SerialPortTransportConfig.Builder;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusRtuRequestContext;
import com.digitalpetri.modbus.server.ModbusRtuServerTransport;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus RTU/Serial server transport; a {@link ModbusRtuServerTransport} that sends and receives
 * {@link ModbusRtuFrame}s over a serial port.
 */
public class SerialPortServerTransport implements ModbusRtuServerTransport {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ModbusRtuRequestFrameParser frameParser = new ModbusRtuRequestFrameParser();
  private final AtomicReference<FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame>>
      frameReceiver = new AtomicReference<>();

  private final ExecutionQueue executionQueue;

  private final SerialPort serialPort;

  private final SerialPortTransportConfig config;

  public SerialPortServerTransport(SerialPortTransportConfig config) {
    this.config = config;

    serialPort = SerialPort.getCommPort(config.serialPort());

    serialPort.setComPortParameters(
        config.baudRate(),
        config.dataBits(),
        config.stopBits(),
        config.parity(),
        config.rs485Mode());

    executionQueue = new ExecutionQueue(config.executor());
  }

  /**
   * Return the underlying {@link SerialPort} used by this transport.
   *
   * @return the configured {@link SerialPort} instance.
   */
  public SerialPort getSerialPort() {
    return serialPort;
  }

  @Override
  public CompletionStage<Void> bind() {
    if (serialPort.isOpen()) {
      return CompletableFuture.completedFuture(null);
    } else {
      if (serialPort.openPort()) {
        frameParser.reset();

        serialPort.addDataListener(new ModbusRtuDataListener());

        return CompletableFuture.completedFuture(null);
      } else {
        return CompletableFuture.failedFuture(
            new Exception(
                "failed to open port '%s', lastErrorCode=%d"
                    .formatted(config.serialPort(), serialPort.getLastErrorCode())));
      }
    }
  }

  @Override
  public CompletionStage<Void> unbind() {
    if (serialPort.isOpen()) {
      if (serialPort.closePort()) {
        frameParser.reset();

        return CompletableFuture.completedFuture(null);
      } else {
        return CompletableFuture.failedFuture(
            new Exception(
                "failed to close port '%s', lastErrorCode=%d"
                    .formatted(config.serialPort(), serialPort.getLastErrorCode())));
      }
    } else {
      return CompletableFuture.completedFuture(null);
    }
  }

  @Override
  public void receive(FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame> frameReceiver) {
    this.frameReceiver.set(frameReceiver);
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

    private void onFrameReceived(ModbusRtuFrame requestFrame) {
      FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame> frameReceiver =
          SerialPortServerTransport.this.frameReceiver.get();

      if (frameReceiver != null) {
        executionQueue.submit(
            () -> {
              try {
                ModbusRtuFrame responseFrame =
                    frameReceiver.receive(new ModbusRtuRequestContext() {}, requestFrame);

                int unitId = responseFrame.unitId();
                ByteBuffer pdu = responseFrame.pdu();
                ByteBuffer crc = responseFrame.crc();

                byte[] data = new byte[1 + pdu.remaining() + crc.remaining()];
                data[0] = (byte) unitId;
                pdu.get(data, 1, pdu.remaining());
                crc.get(data, data.length - 2, crc.remaining());

                int totalWritten = 0;
                while (totalWritten < data.length) {
                  int written =
                      serialPort.writeBytes(data, data.length - totalWritten, totalWritten);
                  if (written == -1) {
                    logger.error("Error writing frame to serial port");

                    return;
                  }
                  totalWritten += written;
                }
              } catch (UnknownUnitIdException e) {
                logger.debug("Ignoring request for unknown unit id: {}", requestFrame.unitId());
              } catch (Exception e) {
                logger.error("Error handling frame: {}", e.getMessage(), e);
              }
            });
      }
    }
  }

  /**
   * Create a new {@link SerialPortServerTransport} with a callback that allows customizing the
   * configuration.
   *
   * @param configure a {@link Consumer} that accepts a {@link SerialPortTransportConfig.Builder}
   *     instance to configure.
   * @return a new {@link SerialPortServerTransport}.
   */
  public static SerialPortServerTransport create(Consumer<Builder> configure) {

    var builder = new SerialPortTransportConfig.Builder();
    configure.accept(builder);
    return new SerialPortServerTransport(builder.build());
  }
}
