package com.digitalpetri.modbus.client;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.Netty;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Configuration for a {@link NettyTcpClientTransport}.
 *
 * @param hostname the hostname or IP address to connect to.
 * @param port the port to connect to.
 * @param connectTimeout the connect timeout.
 * @param connectPersistent whether to connect persistently.
 * @param reconnectLazy whether to reconnect lazily.
 * @param eventLoopGroup the {@link EventLoopGroup} to use.
 * @param executor the {@link ExecutorService} to use.
 * @param bootstrapCustomizer a {@link Consumer} that can be used to customize the Netty
 *     {@link Bootstrap}.
 * @param pipelineCustomizer a {@link Consumer} that can be used to customize the Netty
 *     {@link ChannelPipeline}.
 */
public record NettyClientTransportConfig(
    String hostname,
    int port,
    Duration connectTimeout,
    boolean connectPersistent,
    boolean reconnectLazy,
    EventLoopGroup eventLoopGroup,
    ExecutorService executor,
    Consumer<Bootstrap> bootstrapCustomizer,
    Consumer<ChannelPipeline> pipelineCustomizer
) {

  /**
   * Create a new {@link NettyClientTransportConfig} with a callback that allows customizing the
   * configuration.
   *
   * @param configure a {@link Consumer} that accepts a {@link Builder} instance to configure.
   * @return a new {@link NettyClientTransportConfig}.
   */
  public static NettyClientTransportConfig create(Consumer<Builder> configure) {
    var builder = new Builder();
    configure.accept(builder);
    return builder.build();
  }

  public static class Builder {

    /**
     * The hostname or IP address to connect to.
     */
    public String hostname;

    /**
     * The port to connect to.
     */
    public int port = 502;

    /**
     * The connect timeout.
     */
    public Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Whether to connect persistently.
     */
    public boolean connectPersistent = true;

    /**
     * Whether to reconnect lazily.
     */
    public boolean reconnectLazy = false;

    /**
     * The {@link EventLoopGroup} to use.
     *
     * @see Netty#sharedEventLoop()
     */
    public EventLoopGroup eventLoopGroup;

    /**
     * The {@link ExecutorService} to use.
     *
     * @see Modbus#sharedExecutor()
     */
    public ExecutorService executor;

    /**
     * A {@link Consumer} that can be used to customize the Netty {@link Bootstrap}.
     */
    public Consumer<Bootstrap> bootstrapCustomizer = b -> {};

    /**
     * A {@link Consumer} that can be used to customize the Netty {@link ChannelPipeline}.
     */
    public Consumer<ChannelPipeline> pipelineCustomizer = p -> {};

    public NettyClientTransportConfig build() {
      if (hostname == null) {
        throw new NullPointerException("hostname must not be null");
      }
      if (eventLoopGroup == null) {
        eventLoopGroup = Netty.sharedEventLoop();
      }
      if (executor == null) {
        executor = Modbus.sharedExecutor();
      }

      return new NettyClientTransportConfig(
          hostname,
          port,
          connectTimeout,
          connectPersistent,
          reconnectLazy,
          eventLoopGroup,
          executor,
          bootstrapCustomizer,
          pipelineCustomizer
      );
    }

  }

}
