package com.digitalpetri.modbus.exceptions;

import java.io.Serial;

/**
 * A request could not be sent because the transport was not connected.
 *
 * <p>The request was never written to the connection, so it did not reach the device. Unlike {@link
 * ModbusConnectionLostException}, the outcome of a write is known: it was not applied.
 */
public class ModbusNotConnectedException extends ModbusException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates an exception indicating that the transport was not connected. */
  public ModbusNotConnectedException() {
    super("transport not connected");
  }

  /**
   * Creates an exception indicating that the transport was not connected.
   *
   * @param cause the transport's own failure, e.g. a closed-channel exception.
   */
  public ModbusNotConnectedException(Throwable cause) {
    super("transport not connected", cause);
  }
}
