package com.digitalpetri.modbus.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.ModbusPduSerializer.DefaultRequestSerializer;
import com.digitalpetri.modbus.ModbusPduSerializer.DefaultResponseSerializer;
import com.digitalpetri.modbus.ModbusResponseValidator;
import com.digitalpetri.modbus.ModbusResponseValidator.DefaultResponseValidator;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ModbusClientConfigTest {

  @Test
  void responseValidatorDefaultsToStandardChecksInBothConstructors() {
    assertSame(
        DefaultResponseValidator.INSTANCE,
        ModbusClientConfig.create(cfg -> {}).responseValidator());
    assertSame(
        DefaultResponseValidator.INSTANCE,
        new ModbusClientConfig(
                Duration.ofSeconds(5),
                (task, delay, unit) -> null,
                DefaultRequestSerializer.INSTANCE,
                DefaultResponseSerializer.INSTANCE,
                Duration.ZERO)
            .responseValidator());
  }

  @Test
  void responseValidatorCanBeReplacedAndMustNotBeNull() {
    ModbusResponseValidator validator = (request, response) -> {};
    assertSame(
        validator,
        ModbusClientConfig.create(cfg -> cfg.setResponseValidator(validator)).responseValidator());
    assertThrows(
        NullPointerException.class,
        () -> ModbusClientConfig.create(cfg -> cfg.responseValidator = null));
    assertThrows(
        NullPointerException.class,
        () ->
            new ModbusClientConfig(
                Duration.ofSeconds(5),
                (task, delay, unit) -> null,
                DefaultRequestSerializer.INSTANCE,
                DefaultResponseSerializer.INSTANCE,
                Duration.ZERO,
                null));
  }

  @Test
  void broadcastTurnaroundDelayDefaultsToZero() {
    ModbusClientConfig config = ModbusClientConfig.create(cfg -> {});

    assertEquals(Duration.ZERO, config.broadcastTurnaroundDelay());
  }

  @Test
  void broadcastTurnaroundDelayMustNotBeNull() {
    assertThrows(
        NullPointerException.class,
        () -> ModbusClientConfig.create(cfg -> cfg.broadcastTurnaroundDelay = null));

    assertThrows(
        NullPointerException.class,
        () ->
            new ModbusClientConfig(
                Duration.ofSeconds(5),
                (task, delay, unit) -> null,
                DefaultRequestSerializer.INSTANCE,
                DefaultResponseSerializer.INSTANCE,
                null));
  }

  @Test
  void requestTimeoutMustNotBeNull() {
    assertThrows(
        NullPointerException.class,
        () -> ModbusClientConfig.create(cfg -> cfg.requestTimeout = null));
  }

  @Test
  void requestTimeoutMustNotBeNegative() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ModbusClientConfig.create(cfg -> cfg.requestTimeout = Duration.ofNanos(-1)));
  }

  @Test
  void requestTimeoutMustFitInNanoseconds() {
    Duration max = Duration.ofNanos(Long.MAX_VALUE);

    ModbusClientConfig config = ModbusClientConfig.create(cfg -> cfg.requestTimeout = max);
    assertEquals(max, config.requestTimeout());

    assertThrows(
        IllegalArgumentException.class,
        () -> ModbusClientConfig.create(cfg -> cfg.requestTimeout = max.plusNanos(1)));
  }

  @Test
  void broadcastTurnaroundDelayMustNotBeNegative() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ModbusClientConfig.create(cfg -> cfg.broadcastTurnaroundDelay = Duration.ofNanos(-1)));
  }

  @Test
  void broadcastTurnaroundDelayMustFitInNanoseconds() {
    Duration max = Duration.ofNanos(Long.MAX_VALUE);

    ModbusClientConfig config =
        ModbusClientConfig.create(cfg -> cfg.broadcastTurnaroundDelay = max);
    assertEquals(max, config.broadcastTurnaroundDelay());

    assertThrows(
        IllegalArgumentException.class,
        () -> ModbusClientConfig.create(cfg -> cfg.broadcastTurnaroundDelay = max.plusNanos(1)));
  }
}
