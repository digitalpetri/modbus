package com.digitalpetri.modbus;

import java.util.Optional;

public enum FunctionCode {

  /**
   * Function Code 0x01 - Read Coils.
   */
  READ_COILS(0x01),

  /**
   * Function Code 0x02 - Read Discrete Inputs.
   */
  READ_DISCRETE_INPUTS(0x02),

  /**
   * Function Code 0x03 - Read Holding Registers.
   */
  READ_HOLDING_REGISTERS(0x03),

  /**
   * Function Code 0x04 - Read Input Registers.
   */
  READ_INPUT_REGISTERS(0x04),

  /**
   * Function Code 0x05 - Write Single Coil.
   */
  WRITE_SINGLE_COIL(0x05),

  /**
   * Function Code 0x06 - Write Single Register.
   */
  WRITE_SINGLE_REGISTER(0x06),

  /**
   * Function Code 0x07 - Read Exception Status.
   */
  READ_EXCEPTION_STATUS(0x07),

  /**
   * Function Code 0x08 - Diagnostics.
   */
  DIAGNOSTICS(0x08),

  /**
   * Function Code 0x0B - Get Comm Event Counter.
   */
  GET_COMM_EVENT_COUNTER(0x0B),

  /**
   * Function Code 0x0C - Get Comm Event Log.
   */
  GET_COMM_EVENT_LOG(0x0C),

  /**
   * Function Code 0x0F - Write Multiple Coils.
   */
  WRITE_MULTIPLE_COILS(0x0F),

  /**
   * Function Code 0x10 - Write Multiple Registers.
   */
  WRITE_MULTIPLE_REGISTERS(0x10),

  /**
   * Function Code 0x11 - Report Slave Id.
   */
  REPORT_SLAVE_ID(0x11),

  /**
   * Function Code 0x14 - Read File Record.
   */
  READ_FILE_RECORD(0x14),

  /**
   * Function Code 0x15 - Write File Record.
   */
  WRITE_FILE_RECORD(0x15),

  /**
   * Function Code 0x16 - Mask Write Register.
   */
  MASK_WRITE_REGISTER(0x16),

  /**
   * Function Code 0x17 - Read/Write Multiple Registers.
   */
  READ_WRITE_MULTIPLE_REGISTERS(0x17),

  /**
   * Function Code 0x18 - Read FIFO Queue.
   */
  READ_FIFO_QUEUE(0x18),

  /**
   * Function Code 0x2B - Encapsulated Interface Transport.
   */
  ENCAPSULATED_INTERFACE_TRANSPORT(0x2B);

  FunctionCode(int code) {
    this.code = code;
  }

  private final int code;

  public int getCode() {
    return code;
  }

  /**
   * Look up the corresponding {@link FunctionCode} for {@code code}.
   *
   * @param code the function code to look up.
   * @return the corresponding {@link FunctionCode} for {@code code}.
   */
  public static Optional<FunctionCode> from(int code) {
    FunctionCode fc = switch (code) {
      case 0x01 -> READ_COILS;
      case 0x02 -> READ_DISCRETE_INPUTS;
      case 0x03 -> READ_HOLDING_REGISTERS;
      case 0x04 -> READ_INPUT_REGISTERS;
      case 0x05 -> WRITE_SINGLE_COIL;
      case 0x06 -> WRITE_SINGLE_REGISTER;
      case 0x07 -> READ_EXCEPTION_STATUS;
      case 0x08 -> DIAGNOSTICS;
      case 0x0B -> GET_COMM_EVENT_COUNTER;
      case 0x0C -> GET_COMM_EVENT_LOG;
      case 0x0F -> WRITE_MULTIPLE_COILS;
      case 0x10 -> WRITE_MULTIPLE_REGISTERS;
      case 0x11 -> REPORT_SLAVE_ID;
      case 0x14 -> READ_FILE_RECORD;
      case 0x15 -> WRITE_FILE_RECORD;
      case 0x16 -> MASK_WRITE_REGISTER;
      case 0x17 -> READ_WRITE_MULTIPLE_REGISTERS;
      case 0x18 -> READ_FIFO_QUEUE;
      case 0x2B -> ENCAPSULATED_INTERFACE_TRANSPORT;
      default -> null;
    };

    return Optional.ofNullable(fc);
  }

}
