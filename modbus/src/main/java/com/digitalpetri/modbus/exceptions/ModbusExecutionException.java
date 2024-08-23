package com.digitalpetri.modbus.exceptions;

import java.io.Serial;

public class ModbusExecutionException extends ModbusException {

  @Serial
  private static final long serialVersionUID = 8407528717209895345L;

  public ModbusExecutionException(String message) {
    super(message);
  }

  public ModbusExecutionException(Throwable cause) {
    super(cause);
  }

  public ModbusExecutionException(String message, Throwable cause) {
    super(message, cause);
  }

}
