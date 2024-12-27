package com.digitalpetri.modbus.exceptions;

import java.io.Serial;

public class ModbusException extends Exception {

  @Serial private static final long serialVersionUID = 5355236996267676988L;

  public ModbusException(String message) {
    super(message);
  }

  public ModbusException(Throwable cause) {
    super(cause);
  }

  public ModbusException(String message, Throwable cause) {
    super(message, cause);
  }
}
