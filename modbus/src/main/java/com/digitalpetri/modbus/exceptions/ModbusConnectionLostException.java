package com.digitalpetri.modbus.exceptions;

import java.io.Serial;

/**
 * An established transport connection was lost before a request completed.
 *
 * <p>The request may have reached the device. In particular, a failed write has an unknown outcome
 * and must not be assumed safe to retry.
 */
public class ModbusConnectionLostException extends ModbusException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates an exception indicating that the connection was lost. */
  public ModbusConnectionLostException() {
    super("connection lost before the request completed");
  }
}
