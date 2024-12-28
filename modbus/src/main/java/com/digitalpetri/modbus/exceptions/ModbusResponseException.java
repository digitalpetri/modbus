package com.digitalpetri.modbus.exceptions;

import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.FunctionCode;
import java.io.Serial;

public class ModbusResponseException extends ModbusException {

  @Serial private static final long serialVersionUID = -4058366691447836220L;

  private final int functionCode;
  private final int exceptionCode;

  public ModbusResponseException(int functionCode, int exceptionCode) {
    super(createMessage(functionCode, exceptionCode));

    this.functionCode = functionCode;
    this.exceptionCode = exceptionCode;
  }

  public ModbusResponseException(FunctionCode functionCode, ExceptionCode exceptionCode) {
    this(functionCode.getCode(), exceptionCode.getCode());
  }

  /**
   * @return the function code that generated the exception response.
   */
  public int getFunctionCode() {
    return functionCode;
  }

  /**
   * @return the exception code indicated in the exception response.
   */
  public int getExceptionCode() {
    return exceptionCode;
  }

  private static String createMessage(int functionCode, int exceptionCode) {
    String fcs = FunctionCode.from(functionCode).map(Enum::toString).orElse("UNKNOWN");
    String ecs = ExceptionCode.from(exceptionCode).map(Enum::toString).orElse("UNKNOWN");

    return "0x%02X [%s] generated exception response 0x%02X [%s]"
        .formatted(functionCode, fcs, exceptionCode, ecs);
  }
}
