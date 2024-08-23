package com.digitalpetri.modbus.exceptions;

import java.io.Serial;

public class UnknownUnitIdException extends ModbusException {

  @Serial
  private static final long serialVersionUID = 58792353863854093L;

  public UnknownUnitIdException(int unitId) {
    super("unknown unit id: " + unitId);
  }

  public UnknownUnitIdException(int unitId, Throwable cause) {
    super("unknown unit id: " + unitId, cause);
  }

}
