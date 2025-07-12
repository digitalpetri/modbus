package com.digitalpetri.modbus.tcp.server;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.tcp.Netty;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Configuration for a {@link NettyRtuServerTransport}.
 *
 * @param bindAddress the address to bind to.
 * @param port the port to bind to.
 * @param eventLoopGroup the {@link EventLoopGroup} to use.
 * @param executor the {@link ExecutorService} to use.
 * @param bootstrapCustomizer a {@link Consumer} that can be used to customize the Netty {@link
 *     ServerBootstrap}.
 * @param pipelineCustomizer a {@link Consumer} that can be used to customize the Netty {@link
 *     ChannelPipeline}.
 */
public record NettyServerTransportConfig(
    String bindAddress,
    int port,
    EventLoopGroup eventLoopGroup,
    ExecutorService executor,
    Consumer<ServerBootstrap> bootstrapCustomizer,
    Consumer<ChannelPipeline> pipelineCustomizer,
    boolean tlsEnabled,
    Optional<KeyManagerFactory> keyManagerFactory,
    Optional<TrustManagerFactory> trustManagerFactory) {

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

    /** The address to bind to. */
    public String bindAddress = "0.0.0.0";

    /** The port to bind to. */
    public int port = -1;

    /** The {@link EventLoopGroup} to use. */
    public EventLoopGroup eventLoopGroup;

    /** The {@link ExecutorService} to use. */
    public ExecutorService executor;

    /** A {@link Consumer} that can be used to customize the Netty {@link ServerBootstrap}. */
    public Consumer<ServerBootstrap> bootstrapCustomizer = b -> {};

    /** A {@link Consumer} that can be used to customize the Netty {@link ChannelPipeline}. */
    public Consumer<ChannelPipeline> pipelineCustomizer = p -> {};

    public boolean tlsEnabled = false;
    public KeyManagerFactory keyManagerFactory = null;
    public TrustManagerFactory trustManagerFactory = null;

    /**
     * Set the address to bind to.
     *
     * @param bindAddress the address to bind to.
     * @return this {@link Builder}.
     */
    public Builder setBindAddress(String bindAddress) {
      this.bindAddress = bindAddress;
      return this;
    }

    /**
     * Set the port to bind to.
     *
     * @param port the port to bind to.
     * @return this {@link Builder}.
     */
    public Builder setPort(int port) {
      this.port = port;
      return this;
    }

    /**
     * Set the {@link EventLoopGroup} to use.
     *
     * @param eventLoopGroup the {@link EventLoopGroup} to use.
     * @return this {@link Builder}.
     */
    public Builder setEventLoopGroup(EventLoopGroup eventLoopGroup) {
      this.eventLoopGroup = eventLoopGroup;
      return this;
    }

    /**
     * Set the {@link ExecutorService} to use.
     *
     * @param executor the {@link ExecutorService} to use.
     * @return this {@link Builder}.
     */
    public Builder setExecutor(ExecutorService executor) {
      this.executor = executor;
      return this;
    }

    /**
     * Set the {@link Consumer} that can be used to customize the Netty {@link ServerBootstrap}.
     *
     * @param bootstrapCustomizer the {@link Consumer} to use.
     * @return this {@link Builder}.
     */
    public Builder setBootstrapCustomizer(Consumer<ServerBootstrap> bootstrapCustomizer) {
      this.bootstrapCustomizer = bootstrapCustomizer;
      return this;
    }

    /**
     * Set the {@link Consumer} that can be used to customize the Netty {@link ChannelPipeline}.
     *
     * @param pipelineCustomizer the {@link Consumer} to use.
     * @return this {@link Builder}.
     */
    public Builder setPipelineCustomizer(Consumer<ChannelPipeline> pipelineCustomizer) {
      this.pipelineCustomizer = pipelineCustomizer;
      return this;
    }

    /**
     * Set whether TLS is enabled.
     *
     * @param tlsEnabled whether TLS is enabled.
     * @return this {@link Builder}.
     */
    public Builder setTlsEnabled(boolean tlsEnabled) {
      this.tlsEnabled = tlsEnabled;
      return this;
    }

    /**
     * Set the {@link KeyManagerFactory} to use.
     *
     * @param keyManagerFactory the {@link KeyManagerFactory} to use.
     * @return this {@link Builder}.
     */
    public Builder setKeyManagerFactory(KeyManagerFactory keyManagerFactory) {
      this.keyManagerFactory = keyManagerFactory;
      return this;
    }

    /**
     * Set the {@link TrustManagerFactory} to use.
     *
     * @param trustManagerFactory the {@link TrustManagerFactory} to use.
     * @return this {@link Builder}.
     */
    public Builder setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
      this.trustManagerFactory = trustManagerFactory;
      return this;
    }

    public NettyServerTransportConfig build() {
      if (port == -1) {
        port = tlsEnabled ? 802 : 502;
      }
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
          executor,
          bootstrapCustomizer,
          pipelineCustomizer,
          tlsEnabled,
          Optional.ofNullable(keyManagerFactory),
          Optional.ofNullable(trustManagerFactory));
    }
  }
}
