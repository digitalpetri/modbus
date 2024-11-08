package com.digitalpetri.modbus.tcp.server;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.tcp.Netty;
import io.netty.channel.EventLoopGroup;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Configuration for a {@link NettyRtuServerTransport}.
 *
 * @param bindAddress the address to bind to.
 * @param port the port to bind to.
 * @param eventLoopGroup the {@link EventLoopGroup} to use.
 * @param executor the {@link ExecutorService} to use.
 */
public record NettyServerTransportConfig(
    String bindAddress,
    int port,
    EventLoopGroup eventLoopGroup,
    ExecutorService executor
) {

  /**
   * Create a new {@link NettyServerTransportConfig} with a callback that allows customizing the
   * configuration.
   *
   * @param configure a {@link Consumer} that accepts a {@link Builder} instance to configure.
   * @return a new {@link NettyServerTransportConfig}.
   */
  public static NettyServerTransportConfig create(Consumer<Builder> configure) {
    var builder = new Builder();
    configure.accept(builder);
    return builder.build();
  }

  public static class Builder {

    /**
     * The address to bind to.
     */
    public String bindAddress = "0.0.0.0";

    /**
     * The port to bind to.
     */
    public int port = 502;

    /**
     * The {@link EventLoopGroup} to use.
     */
    public EventLoopGroup eventLoopGroup;

    /**
     * The {@link ExecutorService} to use.
     */
    public ExecutorService executor;

    public NettyServerTransportConfig build() {
      if (eventLoopGroup == null) {
        eventLoopGroup = Netty.sharedEventLoop();
      }
      if (executor == null) {
        executor = Modbus.sharedExecutor();
      }

      return new NettyServerTransportConfig(
          bindAddress,
          port,
          eventLoopGroup,
          executor
      );
    }
  }

}
