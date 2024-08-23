package com.digitalpetri.modbus;

import com.digitalpetri.modbus.client.SerialPortClientTransport;
import com.fazecast.jSerialComm.SerialPort;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Configuration for a {@link SerialPortClientTransport}.
 *
 * @param serialPort the OS/system-dependent serial port descriptor.
 * @param baudRate the desired baud rate to operate at.
 * @param dataBits the number of data bits per word to use.
 * @param stopBits the number of stop bits to use.
 * @param parity the type of parity error-checking to use.
 * @param rs485Mode enable RS-485 mode, i.e. transmit/receive mode signaling using the RTS pin.
 * @param executor the {@link ExecutorService} to use when delivering frame received callbacks.
 * @see SerialPortTransportConfig#create(Consumer)
 */
public record SerialPortTransportConfig(
    String serialPort,
    int baudRate,
    int dataBits,
    int stopBits,
    int parity,
    boolean rs485Mode,
    ExecutorService executor
) {

  /**
   * Create a new {@link SerialPortTransportConfig}, using the callback to configure the builder as
   * required.
   *
   * @param configure a {@link Consumer} that accepts a {@link Builder} instance to configure.
   * @return a new {@link SerialPortTransportConfig}.
   */
  public static SerialPortTransportConfig create(Consumer<Builder> configure) {
    var builder = new Builder();
    configure.accept(builder);
    return builder.build();
  }

  public static class Builder {

    /**
     * The OS/system-dependent serial port descriptor.
     *
     * <p>On Windows this may look something like {@code COM1}. On Linux this may look something
     * like {@code /dev/ttyUSB0}.
     */
    public String serialPort;

    /**
     * The desired baud rate to operate at.
     */
    public int baudRate = 9600;

    /**
     * The number of data bits per word to use.
     */
    public int dataBits = 8;

    /**
     * The number of stop bits to use.
     *
     * @see SerialPort#ONE_STOP_BIT
     * @see SerialPort#ONE_POINT_FIVE_STOP_BITS
     * @see SerialPort#TWO_STOP_BITS
     */
    public int stopBits = SerialPort.ONE_STOP_BIT;

    /**
     * The type of parity error-checking to use.
     *
     * @see SerialPort#NO_PARITY
     * @see SerialPort#ODD_PARITY
     * @see SerialPort#EVEN_PARITY
     * @see SerialPort#MARK_PARITY
     * @see SerialPort#SPACE_PARITY
     */
    public int parity = SerialPort.NO_PARITY;

    /**
     * Enable RS-485 mode, i.e. transmit/receive mode signaling using the RTS pin.
     *
     * <p>This requires support from the underlying driver and may not work with all RS-485
     * devices.
     */
    public boolean rs485Mode = false;

    /**
     * The {@link ExecutorService} to use when delivering frame received callbacks.
     *
     * <p>Defaults to {@link Modbus#sharedExecutor()} if not set explicitly.
     */
    public ExecutorService executor;

    /**
     * Build a new {@link SerialPortTransportConfig} from the current state of this builder.
     *
     * @return a new {@link SerialPortTransportConfig}.
     */
    public SerialPortTransportConfig build() {
      if (serialPort == null) {
        throw new NullPointerException("serialPort must not be null");
      }
      if (executor == null) {
        executor = Modbus.sharedExecutor();
      }

      return new SerialPortTransportConfig(
          serialPort,
          baudRate,
          dataBits,
          stopBits,
          parity,
          rs485Mode,
          executor
      );
    }

  }

}
