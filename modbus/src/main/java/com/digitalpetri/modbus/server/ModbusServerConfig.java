package com.digitalpetri.modbus.server;

import static com.digitalpetri.modbus.ModbusPduSerializer.DefaultRequestSerializer;
import static com.digitalpetri.modbus.ModbusPduSerializer.DefaultResponseSerializer;

import com.digitalpetri.modbus.ModbusPduSerializer;
import java.util.function.Consumer;

/**
 * Configuration for a {@link ModbusTcpServer}.
 *
 * @param requestSerializer the {@link ModbusPduSerializer} used to decode incoming requests.
 * @param responseSerializer the {@link ModbusPduSerializer} used to encode outgoing responses.
 */
public record ModbusServerConfig(
    ModbusPduSerializer requestSerializer, ModbusPduSerializer responseSerializer) {

  /**
   * Create a new {@link ModbusServerConfig} instance.
   *
   * @param configure a callback that accepts a {@link Builder} used to configure the new instance.
   * @return a new {@link ModbusServerConfig} instance.
   */
  public static ModbusServerConfig create(Consumer<Builder> configure) {
    var builder = new Builder();
    configure.accept(builder);
    return builder.build();
  }

  public static class Builder {

    /** The {@link ModbusPduSerializer} used to decode incoming requests. */
    public ModbusPduSerializer requestSerializer = DefaultRequestSerializer.INSTANCE;

    /** The {@link ModbusPduSerializer} used to encode outgoing responses. */
    public ModbusPduSerializer responseSerializer = DefaultResponseSerializer.INSTANCE;

    /**
     * Set the {@link ModbusPduSerializer} used to decode incoming requests.
     *
     * @param requestSerializer the request serializer
     * @return this {@link Builder}
     */
    public Builder setRequestSerializer(ModbusPduSerializer requestSerializer) {
      this.requestSerializer = requestSerializer;
      return this;
    }

    /**
     * Set the {@link ModbusPduSerializer} used to encode outgoing responses.
     *
     * @param responseSerializer the response serializer
     * @return this {@link Builder}
     */
    public Builder setResponseSerializer(ModbusPduSerializer responseSerializer) {
      this.responseSerializer = responseSerializer;
      return this;
    }

    /**
     * @return a new {@link ModbusServerConfig} instance.
     */
    public ModbusServerConfig build() {
      return new ModbusServerConfig(requestSerializer, responseSerializer);
    }
  }
}
