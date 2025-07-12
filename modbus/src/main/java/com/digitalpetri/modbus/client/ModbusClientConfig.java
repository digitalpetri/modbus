package com.digitalpetri.modbus.client;

import static com.digitalpetri.modbus.ModbusPduSerializer.DefaultRequestSerializer;
import static com.digitalpetri.modbus.ModbusPduSerializer.DefaultResponseSerializer;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.ModbusPduSerializer;
import com.digitalpetri.modbus.TimeoutScheduler;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Configuration for a {@link ModbusClient}.
 *
 * @param requestTimeout the timeout duration for requests.
 * @param timeoutScheduler the {@link TimeoutScheduler} used to schedule request timeouts.
 * @param requestSerializer the {@link ModbusPduSerializer} used to encode requests.
 * @param responseSerializer the {@link ModbusPduSerializer} used to decode responses.
 */
public record ModbusClientConfig(
    Duration requestTimeout,
    TimeoutScheduler timeoutScheduler,
    ModbusPduSerializer requestSerializer,
    ModbusPduSerializer responseSerializer) {

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

    /** The {@link TimeoutScheduler} used to schedule request timeouts. */
    public TimeoutScheduler timeoutScheduler;

    /** The {@link ModbusPduSerializer} used to encode outgoing requests. */
    public ModbusPduSerializer requestSerializer = DefaultRequestSerializer.INSTANCE;

    /** The {@link ModbusPduSerializer} used to decode incoming responses. */
    public ModbusPduSerializer responseSerializer = DefaultResponseSerializer.INSTANCE;

    /**
     * Set the timeout duration for requests.
     *
     * @param requestTimeout the request timeout
     * @return this {@link Builder}
     */
    public Builder setRequestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
    }

    /**
     * Set the {@link TimeoutScheduler} used to schedule request timeouts.
     *
     * @param timeoutScheduler the timeout scheduler
     * @return this {@link Builder}
     */
    public Builder setTimeoutScheduler(TimeoutScheduler timeoutScheduler) {
      this.timeoutScheduler = timeoutScheduler;
      return this;
    }

    /**
     * Set the {@link ModbusPduSerializer} used to encode outgoing requests.
     *
     * @param requestSerializer the request serializer
     * @return this {@link Builder}
     */
    public Builder setRequestSerializer(ModbusPduSerializer requestSerializer) {
      this.requestSerializer = requestSerializer;
      return this;
    }

    /**
     * Set the {@link ModbusPduSerializer} used to decode incoming responses.
     *
     * @param responseSerializer the response serializer
     * @return this {@link Builder}
     */
    public Builder setResponseSerializer(ModbusPduSerializer responseSerializer) {
      this.responseSerializer = responseSerializer;
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
          requestTimeout, timeoutScheduler, requestSerializer, responseSerializer);
    }
  }
}
