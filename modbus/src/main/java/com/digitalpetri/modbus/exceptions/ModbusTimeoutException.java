package com.digitalpetri.modbus.exceptions;

import java.io.Serial;

public class ModbusTimeoutException extends ModbusException {

  @Serial
  private static final long serialVersionUID = -8643809775979891078L;

  public ModbusTimeoutException(String message) {
    super(message);
  }

  public ModbusTimeoutException(Throwable cause) {
    super(cause);
  }

  public ModbusTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }

}
