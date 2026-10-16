package com.digitalpetri.modbus.exceptions;

import java.io.Serial;
import java.util.concurrent.TimeoutException;

/**
 * A request timed out, or could not be given a timeout, before it was handed to the transport.
 *
 * <p>The request was never sent, so the device did not receive it. A write that fails this way can
 * be retried safely. This differs from a plain {@link TimeoutException}, which means the request
 * was sent and got no response, so a write's outcome is unknown.
 *
 * <p>{@code ModbusRtuClient} sends one request at a time, so a request can time out while it is
 * still waiting in the client's queue behind other requests. Both clients also fail a request this
 * way when the configured {@code TimeoutScheduler} refuses to schedule its timeout, for example
 * because the scheduler was shut down; the scheduler's exception is the cause.
 *
 * <p>Asynchronous calls fail with this exception. Synchronous request methods wrap it in {@link
 * ModbusTimeoutException}, as they do any {@link TimeoutException}, so a caller can tell the two
 * cases apart by checking the cause. {@code ModbusRtuClient.broadcast} wraps it in {@link
 * ModbusExecutionException}, as it does every failure.
 */
public class QueuedRequestTimeoutException extends TimeoutException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Create an exception for a request that timed out before it was sent.
   *
   * @param message the timeout diagnostic.
   */
  public QueuedRequestTimeoutException(String message) {
    super(message);
  }

  /**
   * Create an exception for a request that could not be sent, preserving the underlying failure.
   *
   * @param message the diagnostic.
   * @param cause the failure that kept the request from being sent, e.g. the exception thrown by
   *     the {@code TimeoutScheduler}.
   */
  public QueuedRequestTimeoutException(String message, Throwable cause) {
    super(message);
    initCause(cause);
  }
}
