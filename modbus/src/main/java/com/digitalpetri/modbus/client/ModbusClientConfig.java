package com.digitalpetri.modbus.client;

import static com.digitalpetri.modbus.ModbusPduSerializer.DefaultRequestSerializer;
import static com.digitalpetri.modbus.ModbusPduSerializer.DefaultResponseSerializer;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.ModbusPduSerializer;
import com.digitalpetri.modbus.ModbusResponseValidator;
import com.digitalpetri.modbus.ModbusResponseValidator.DefaultResponseValidator;
import com.digitalpetri.modbus.TimeoutScheduler;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Configuration for a {@link ModbusClient}.
 *
 * @param requestTimeout the timeout duration for requests. Must not be negative or longer than
 *     {@link Long#MAX_VALUE} nanoseconds.
 * @param timeoutScheduler the {@link TimeoutScheduler} used to schedule request timeouts and the
 *     broadcast turnaround delay.
 * @param requestSerializer the {@link ModbusPduSerializer} used to encode requests.
 * @param responseSerializer the {@link ModbusPduSerializer} used to decode responses.
 * @param broadcastTurnaroundDelay how long {@link ModbusRtuClient} waits after writing a broadcast
 *     before it sends the next request. The wait doesn't count toward the request timeout of the
 *     requests it holds back. Other clients ignore it. Must not be negative or longer than {@link
 *     Long#MAX_VALUE} nanoseconds.
 * @param responseValidator the validator applied after decoding normal responses.
 */
public record ModbusClientConfig(
    Duration requestTimeout,
    TimeoutScheduler timeoutScheduler,
    ModbusPduSerializer requestSerializer,
    ModbusPduSerializer responseSerializer,
    Duration broadcastTurnaroundDelay,
    ModbusResponseValidator responseValidator) {

  /**
   * Create a new {@link ModbusClientConfig} instance.
   *
   * @throws NullPointerException if {@code requestTimeout}, {@code broadcastTurnaroundDelay}, or
   *     {@code responseValidator} is {@code null}.
   * @throws IllegalArgumentException if {@code requestTimeout} or {@code broadcastTurnaroundDelay}
   *     is negative or longer than {@link Long#MAX_VALUE} nanoseconds.
   */
  public ModbusClientConfig {
    Objects.requireNonNull(requestTimeout, "requestTimeout");
    Objects.requireNonNull(broadcastTurnaroundDelay, "broadcastTurnaroundDelay");
    Objects.requireNonNull(responseValidator, "responseValidator");

    // ModbusRtuClient tracks request deadlines and schedules the delay in nanoseconds.
    if (requestTimeout.isNegative()
        || requestTimeout.compareTo(Duration.ofNanos(Long.MAX_VALUE)) > 0) {
      throw new IllegalArgumentException("requestTimeout out of range: " + requestTimeout);
    }
    if (broadcastTurnaroundDelay.isNegative()
        || broadcastTurnaroundDelay.compareTo(Duration.ofNanos(Long.MAX_VALUE)) > 0) {
      throw new IllegalArgumentException(
          "broadcastTurnaroundDelay out of range: " + broadcastTurnaroundDelay);
    }
  }

  /**
   * Create a configuration using standard request-relative response validation.
   *
   * <p>This overload preserves the constructor used before the response validator was configurable.
   *
   * @param requestTimeout the timeout duration for requests.
   * @param timeoutScheduler the request timeout and broadcast delay scheduler.
   * @param requestSerializer the request serializer.
   * @param responseSerializer the response serializer.
   * @param broadcastTurnaroundDelay the RTU broadcast turnaround delay.
   */
  public ModbusClientConfig(
      Duration requestTimeout,
      TimeoutScheduler timeoutScheduler,
      ModbusPduSerializer requestSerializer,
      ModbusPduSerializer responseSerializer,
      Duration broadcastTurnaroundDelay) {
    this(
        requestTimeout,
        timeoutScheduler,
        requestSerializer,
        responseSerializer,
        broadcastTurnaroundDelay,
        DefaultResponseValidator.INSTANCE);
  }

  /**
   * Create a new {@link ModbusClientConfig} instance.
   *
   * @param configure a callback that accepts a {@link Builder} used to configure the new instance.
   * @return a new {@link ModbusClientConfig} instance.
   */
  public static ModbusClientConfig create(Consumer<Builder> configure) {
    var builder = new Builder();
    configure.accept(builder);
    return builder.build();
  }

  public static class Builder {

    /** The timeout duration for requests. */
    public Duration requestTimeout = Duration.ofSeconds(5);

    /**
     * The {@link TimeoutScheduler} used to schedule request timeouts and the broadcast turnaround
     * delay.
     */
    public TimeoutScheduler timeoutScheduler;

    /** The {@link ModbusPduSerializer} used to encode outgoing requests. */
    public ModbusPduSerializer requestSerializer = DefaultRequestSerializer.INSTANCE;

    /** The {@link ModbusPduSerializer} used to decode incoming responses. */
    public ModbusPduSerializer responseSerializer = DefaultResponseSerializer.INSTANCE;

    /** The request-relative validator applied after decoding normal responses. */
    public ModbusResponseValidator responseValidator = DefaultResponseValidator.INSTANCE;

    /**
     * How long {@link ModbusRtuClient} waits after writing a broadcast before it sends the next
     * request.
     */
    public Duration broadcastTurnaroundDelay = Duration.ZERO;

    /**
     * Set the timeout duration for requests.
     *
     * @param requestTimeout the request timeout.
     * @return this {@link Builder}.
     */
    public Builder setRequestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
    }

    /**
     * Set the {@link TimeoutScheduler} used to schedule request timeouts and the broadcast
     * turnaround delay.
     *
     * @param timeoutScheduler the timeout scheduler.
     * @return this {@link Builder}.
     */
    public Builder setTimeoutScheduler(TimeoutScheduler timeoutScheduler) {
      this.timeoutScheduler = timeoutScheduler;
      return this;
    }

    /**
     * Set the {@link ModbusPduSerializer} used to encode outgoing requests.
     *
     * @param requestSerializer the request serializer.
     * @return this {@link Builder}.
     */
    public Builder setRequestSerializer(ModbusPduSerializer requestSerializer) {
      this.requestSerializer = requestSerializer;
      return this;
    }

    /**
     * Set the {@link ModbusPduSerializer} used to decode incoming responses.
     *
     * @param responseSerializer the response serializer.
     * @return this {@link Builder}.
     */
    public Builder setResponseSerializer(ModbusPduSerializer responseSerializer) {
      this.responseSerializer = responseSerializer;
      return this;
    }

    /**
     * Set the request-relative validator applied after decoding normal responses.
     *
     * <p>Replace this independently of the response serializer to customize semantic checks. Custom
     * protocols using built-in records with different register widths must replace both the codec
     * and the validator. Codecs own format checks and complete-PDU consumption. Raw requests bypass
     * the validator.
     *
     * @param responseValidator the response validator, which must not be {@code null}.
     * @return this {@link Builder}.
     */
    public Builder setResponseValidator(ModbusResponseValidator responseValidator) {
      this.responseValidator = responseValidator;
      return this;
    }

    /**
     * Set how long {@link ModbusRtuClient} waits after writing a broadcast before it sends the next
     * request.
     *
     * <p>Slaves don't respond to a broadcast, so this delay gives them time to process it before
     * the next request goes out. The Modbus over Serial Line specification calls this the
     * turnaround delay. The default is zero, so the next request is sent as soon as the broadcast
     * is written. Other clients ignore this setting.
     *
     * <p>{@link #build()} throws if the delay is {@code null}, negative, or longer than {@link
     * Long#MAX_VALUE} nanoseconds.
     *
     * @param broadcastTurnaroundDelay the broadcast turnaround delay.
     * @return this {@link Builder}.
     */
    public Builder setBroadcastTurnaroundDelay(Duration broadcastTurnaroundDelay) {
      this.broadcastTurnaroundDelay = broadcastTurnaroundDelay;
      return this;
    }

    /**
     * @return a new {@link ModbusClientConfig} instance.
     */
    public ModbusClientConfig build() {
      if (timeoutScheduler == null) {
        timeoutScheduler =
            TimeoutScheduler.create(Modbus.sharedExecutor(), Modbus.sharedScheduledExecutor());
      }

      return new ModbusClientConfig(
          requestTimeout,
          timeoutScheduler,
          requestSerializer,
          responseSerializer,
          broadcastTurnaroundDelay,
          responseValidator);
    }
  }
}
