package com.digitalpetri.modbus.tcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NettyServerTransportConfigTest {

  @Test
  void partialFrameTimeoutDefaultsToOneSecond() {
    NettyServerTransportConfig config = NettyServerTransportConfig.create(b -> {});

    assertEquals(Duration.ofSeconds(1), config.partialFrameTimeout());
  }

  @Test
  void legacyConstructorUsesDefaultPartialFrameTimeout() {
    NettyServerTransportConfig built = NettyServerTransportConfig.create(b -> {});
    var config =
        new NettyServerTransportConfig(
            built.bindAddress(),
            built.port(),
            built.eventLoopGroup(),
            built.executor(),
            built.bootstrapCustomizer(),
            built.pipelineCustomizer(),
            false,
            Optional.empty(),
            Optional.empty());

    assertEquals(Duration.ofSeconds(1), config.partialFrameTimeout());
  }

  @Test
  void explicitPartialFrameTimeoutReplacesDefault() {
    NettyServerTransportConfig config =
        NettyServerTransportConfig.create(b -> b.setPartialFrameTimeout(Duration.ofSeconds(2)));

    assertEquals(Duration.ofSeconds(2), config.partialFrameTimeout());
  }

  @Test
  void nonPositivePartialFrameTimeoutIsRejected() {
    for (Duration timeout : new Duration[] {Duration.ZERO, Duration.ofMillis(-1)}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> NettyServerTransportConfig.create(b -> b.setPartialFrameTimeout(timeout)));
    }
    assertThrows(
        NullPointerException.class,
        () -> NettyServerTransportConfig.create(b -> b.setPartialFrameTimeout(null)));
  }
}
