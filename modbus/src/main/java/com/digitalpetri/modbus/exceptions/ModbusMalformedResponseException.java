package com.digitalpetri.modbus.exceptions;

import java.io.Serial;

/**
 * A received response could not be decoded, did not match its request, or failed validation.
 *
 * <p>Typed asynchronous client calls fail with this exception. Synchronous calls wrap it in {@link
 * ModbusExecutionException}. A valid Modbus exception response is reported separately as {@link
 * ModbusResponseException}.
 */
public class ModbusMalformedResponseException extends ModbusException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Create an exception describing an unusable response.
   *
   * @param message the response diagnostic.
   */
  public ModbusMalformedResponseException(String message) {
    super(message);
  }

  /**
   * Create an exception preserving the response decoding or validation failure.
   *
   * @param cause the original failure.
   */
  public ModbusMalformedResponseException(Throwable cause) {
    super(cause);
  }

  /**
   * Create an exception describing and preserving the response failure.
   *
   * @param message the response diagnostic.
   * @param cause the original failure.
   */
  public ModbusMalformedResponseException(String message, Throwable cause) {
    super(message, cause);
  }
}
