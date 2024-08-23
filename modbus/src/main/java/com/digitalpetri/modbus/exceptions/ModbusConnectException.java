package com.digitalpetri.modbus.exceptions;

import java.io.Serial;

public class ModbusConnectException extends ModbusException {

  @Serial
  private static final long serialVersionUID = -5350159787088895451L;

  public ModbusConnectException(String message) {
    super(message);
  }

  public ModbusConnectException(Throwable cause) {
    super(cause);
  }

  public ModbusConnectException(String message, Throwable cause) {
    super(message, cause);
  }

}
