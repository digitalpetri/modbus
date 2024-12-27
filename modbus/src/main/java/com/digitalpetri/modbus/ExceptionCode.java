package com.digitalpetri.modbus;

import java.util.Optional;

public enum ExceptionCode {

  /**
   * Exception Code 0x01 - Illegal Function.
   *
   * <p>The function code received in the query is not an allowable action for the server.
   */
  ILLEGAL_FUNCTION(0x01),

  /**
   * Exception Code 0x02 - Illegal Data Address.
   *
   * <p>The data address received in the query is not an allowable address for the server. More
   * specifically, the combination of reference number and transfer length is invalid.
   */
  ILLEGAL_DATA_ADDRESS(0x02),

  /**
   * Exception Code 0x03 - Illegal Data Value.
   *
   * <p>A value contained in the query data field is not an allowable value for server.
   *
   * <p>This indicates a fault in the structure of the remainder of a complex request, such as that
   * the implied length is incorrect. It specifically does NOT mean that a data item submitted for
   * storage in a register has a value outside the expectation of the application program.
   */
  ILLEGAL_DATA_VALUE(0x03),

  /**
   * Exception Code 0x04 - Slave Device Failure.
   *
   * <p>An unrecoverable error occurred while the server was attempting to perform the requested
   * action.
   */
  SLAVE_DEVICE_FAILURE(0x04),

  /**
   * Exception Code 0x05 - Acknowledge.
   *
   * <p>Specialized use in conjunction with programming commands.
   *
   * <p>The server has accepted the request and is processing it, but a long duration of time will
   * be required to do so. This response is returned to prevent a timeout error from occurring in
   * the client.
   */
  ACKNOWLEDGE(0x05),

  /**
   * Exception Code 0x06 - Slave Device Busy.
   *
   * <p>Specialized use in conjunction with programming commands.
   *
   * <p>The server is engaged in processing a long–duration program command. The client should
   * retransmit the message later when the server is free.
   */
  SLAVE_DEVICE_BUSY(0x06),

  /**
   * Exception Code 0x08 - Memory Parity Error.
   *
   * <p>Specialized use in conjunction with function codes 20 and 21 and reference type 6, to
   * indicate that the extended file area failed to pass a consistency check.
   */
  MEMORY_PARITY_ERROR(0x08),

  /**
   * Exception Code 0x0A - Gateway Path Unavailable.
   *
   * <p>Specialized use in conjunction with gateways, indicates that the gateway was unable to
   * allocate an internal communication path from the input port to the output port for processing
   * the request. Usually means that the gateway is misconfigured or overloaded.
   */
  GATEWAY_PATH_UNAVAILABLE(0x0A),

  /**
   * Exception Code 0x0B - Gateway Target Device Failed to Respond.
   *
   * <p>Specialized use in conjunction with gateways, indicates that no response was obtained from
   * the target device. Usually means that the device is not present on the network.
   */
  GATEWAY_TARGET_DEVICE_FAILED_TO_RESPONSE(0x0B);

  private final int code;

  ExceptionCode(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  /**
   * Look up the corresponding {@link ExceptionCode} for {@code code}.
   *
   * @param code the exception code to look up.
   * @return the corresponding {@link ExceptionCode} for {@code code}.
   */
  public static Optional<ExceptionCode> from(int code) {
    ExceptionCode ec =
        switch (code) {
          case 0x01 -> ILLEGAL_FUNCTION;
          case 0x02 -> ILLEGAL_DATA_ADDRESS;
          case 0x03 -> ILLEGAL_DATA_VALUE;
          case 0x04 -> SLAVE_DEVICE_FAILURE;
          case 0x05 -> ACKNOWLEDGE;
          case 0x06 -> SLAVE_DEVICE_BUSY;
          case 0x08 -> MEMORY_PARITY_ERROR;
          case 0x0A -> GATEWAY_PATH_UNAVAILABLE;
          case 0x0B -> GATEWAY_TARGET_DEVICE_FAILED_TO_RESPONSE;
          default -> null;
        };

    return Optional.ofNullable(ec);
  }
}
