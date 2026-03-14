package com.digitalpetri.modbus.serial;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.serial.client.SerialPortClientTransport;
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
 * @param rs485RtsActiveHigh whether to set the RTS line to 1 when transmitting. Effective only on
 *     Linux.
 * @param rs485Termination whether to enable RS-485 bus termination, on systems that support this
 *     feature. Effective only on Linux.
 * @param rs485RxDuringTx whether to receive data while transmitting. This usually means that data
 *     sent will be read back. Effective only on Linux.
 * @param rs485DelayBefore the time to wait in microseconds after enabling transmit mode before
 *     sending the first data bit. Effective only on Linux.
 * @param rs485DelayAfter the time to wait in microseconds after sending the last data bit before
 *     disabling transmit mode. Effective only on Linux.
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
    boolean rs485RtsActiveHigh,
    boolean rs485Termination,
    boolean rs485RxDuringTx,
    int rs485DelayBefore,
    int rs485DelayAfter,
    ExecutorService executor) {

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

    /** The desired baud rate to operate at. */
    public int baudRate = 9600;

    /** The number of data bits per word to use. */
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
     * Enable RS-485 mode, i.e., transmit/receive mode signaling using the RTS pin.
     *
     * <p>This requires support from the underlying driver and may not work with all RS-485 devices.
     */
    public boolean rs485Mode = false;

    /**
     * Whether to set the RTS line to 1 when transmitting.
     *
     * <p>Effective only on Linux.
     */
    public boolean rs485RtsActiveHigh = true;

    /**
     * Whether to enable RS-485 bus termination on systems that support this feature.
     *
     * <p>Effective only on Linux.
     */
    public boolean rs485Termination = false;

    /**
     * Whether to receive data while transmitting. This usually means that data sent will be read
     * back.
     *
     * <p>Effective only on Linux.
     */
    public boolean rs485RxDuringTx = false;

    /**
     * The time to wait in microseconds after enabling transmit mode before sending the first data
     * bit.
     *
     * <p>Effective only on Linux.
     */
    public int rs485DelayBefore = 0;

    /**
     * The time to wait in microseconds after sending the last data bit before disabling transmit
     * mode.
     *
     * <p>Effective only on Linux.
     */
    public int rs485DelayAfter = 0;

    /**
     * The {@link ExecutorService} to use when delivering frame received callbacks.
     *
     * <p>Defaults to {@link Modbus#sharedExecutor()} if not set explicitly.
     */
    public ExecutorService executor;

    /**
     * Set the OS/system-dependent serial port descriptor.
     *
     * @param serialPort the serial port descriptor.
     * @return this {@link Builder}.
     */
    public Builder setSerialPort(String serialPort) {
      this.serialPort = serialPort;
      return this;
    }

    /**
     * Set the desired baud rate to operate at.
     *
     * @param baudRate the baud rate.
     * @return this {@link Builder}.
     */
    public Builder setBaudRate(int baudRate) {
      this.baudRate = baudRate;
      return this;
    }

    /**
     * Set the number of data bits per word to use.
     *
     * @param dataBits the number of data bits.
     * @return this {@link Builder}.
     */
    public Builder setDataBits(int dataBits) {
      this.dataBits = dataBits;
      return this;
    }

    /**
     * Set the number of stop bits to use.
     *
     * @param stopBits the number of stop bits.
     * @return this {@link Builder}.
     * @see SerialPort#ONE_STOP_BIT
     * @see SerialPort#ONE_POINT_FIVE_STOP_BITS
     * @see SerialPort#TWO_STOP_BITS
     */
    public Builder setStopBits(int stopBits) {
      this.stopBits = stopBits;
      return this;
    }

    /**
     * Set the type of parity error-checking to use.
     *
     * @param parity the type of parity.
     * @return this {@link Builder}.
     * @see SerialPort#NO_PARITY
     * @see SerialPort#ODD_PARITY
     * @see SerialPort#EVEN_PARITY
     * @see SerialPort#MARK_PARITY
     * @see SerialPort#SPACE_PARITY
     */
    public Builder setParity(int parity) {
      this.parity = parity;
      return this;
    }

    /**
     * Enable or disable RS-485 mode, i.e. transmit/receive mode signaling using the RTS pin.
     *
     * @param rs485Mode true to enable RS-485 mode, false to disable.
     * @return this {@link Builder}.
     */
    public Builder setRs485Mode(boolean rs485Mode) {
      this.rs485Mode = rs485Mode;
      return this;
    }

    /**
     * Set whether to set the RTS line to 1 when transmitting.
     *
     * <p>Effective only on Linux.
     *
     * @param rs485RtsActiveHigh true to set the RTS line to 1 when transmitting.
     * @return this {@link Builder}.
     */
    public Builder setRs485RtsActiveHigh(boolean rs485RtsActiveHigh) {
      this.rs485RtsActiveHigh = rs485RtsActiveHigh;
      return this;
    }

    /**
     * Set whether to enable RS-485 bus termination on systems that support this feature.
     *
     * <p>Effective only on Linux.
     *
     * @param rs485Termination true to enable bus termination.
     * @return this {@link Builder}.
     */
    public Builder setRs485Termination(boolean rs485Termination) {
      this.rs485Termination = rs485Termination;
      return this;
    }

    /**
     * Set whether to receive data while transmitting. This usually means that data sent will be
     * read back.
     *
     * <p>Effective only on Linux.
     *
     * @param rs485RxDuringTx true to receive data while transmitting.
     * @return this {@link Builder}.
     */
    public Builder setRs485RxDuringTx(boolean rs485RxDuringTx) {
      this.rs485RxDuringTx = rs485RxDuringTx;
      return this;
    }

    /**
     * Set the time to wait in microseconds after enabling transmit mode before sending the first
     * data bit.
     *
     * <p>Effective only on Linux.
     *
     * @param rs485DelayBefore the delay in microseconds before sending.
     * @return this {@link Builder}.
     */
    public Builder setRs485DelayBefore(int rs485DelayBefore) {
      this.rs485DelayBefore = rs485DelayBefore;
      return this;
    }

    /**
     * Set the time to wait in microseconds after sending the last data bit before disabling
     * transmit mode.
     *
     * <p>Effective only on Linux.
     *
     * @param rs485DelayAfter the delay in microseconds after sending.
     * @return this {@link Builder}.
     */
    public Builder setRs485DelayAfter(int rs485DelayAfter) {
      this.rs485DelayAfter = rs485DelayAfter;
      return this;
    }

    /**
     * Set the {@link ExecutorService} to use when delivering frame received callbacks.
     *
     * @param executor the executor service.
     * @return this {@link Builder}.
     */
    public Builder setExecutor(ExecutorService executor) {
      this.executor = executor;
      return this;
    }

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
          rs485RtsActiveHigh,
          rs485Termination,
          rs485RxDuringTx,
          rs485DelayBefore,
          rs485DelayAfter,
          executor);
    }
  }
}
