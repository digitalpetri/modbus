package com.digitalpetri.modbus.serial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SerialPortTransportConfigTest {

  @Test
  void partialFrameTimeoutDefaultsToThreeAndAHalfCharacterTimes() {
    // 3.5 characters of 11 bits, rounded up to whole nanoseconds.
    assertEquals(Duration.ofNanos(32_083_334), defaultTimeout(1200));
    assertEquals(Duration.ofNanos(4_010_417), defaultTimeout(9600));
    assertEquals(Duration.ofNanos(2_005_209), defaultTimeout(19200));
  }

  @Test
  void partialFrameTimeoutDefaultsToFixedDelayAbove19200Baud() {
    assertEquals(Duration.ofNanos(1_750_000), defaultTimeout(19201));
    assertEquals(Duration.ofNanos(1_750_000), defaultTimeout(38400));
    assertEquals(Duration.ofNanos(1_750_000), defaultTimeout(115200));
  }

  @Test
  void explicitPartialFrameTimeoutReplacesDefault() {
    SerialPortTransportConfig config =
        SerialPortTransportConfig.create(
            b -> b.setSerialPort("test").setPartialFrameTimeout(Duration.ofMillis(50)));

    assertEquals(Duration.ofMillis(50), config.partialFrameTimeout());
  }

  @Test
  void nonPositivePartialFrameTimeoutIsRejected() {
    for (Duration timeout : new Duration[] {Duration.ZERO, Duration.ofMillis(-1)}) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              SerialPortTransportConfig.create(
                  b -> b.setSerialPort("test").setPartialFrameTimeout(timeout)));
    }
  }

  @Test
  void nonPositiveBaudRateIsRejectedWithoutExplicitTimeout() {
    assertThrows(IllegalArgumentException.class, () -> defaultTimeout(0));
  }

  private static Duration defaultTimeout(int baudRate) {
    return SerialPortTransportConfig.create(b -> b.setSerialPort("test").setBaudRate(baudRate))
        .partialFrameTimeout();
  }
}
