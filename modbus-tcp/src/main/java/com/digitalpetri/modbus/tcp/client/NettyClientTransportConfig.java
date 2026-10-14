package com.digitalpetri.modbus.tcp.client;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.tcp.Netty;
import com.digitalpetri.netty.fsm.ChannelFsmConfigBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContextBuilder;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Configuration for {@link NettyTcpClientTransport} and {@link NettyRtuClientTransport}.
 *
 * <p>See {@link Builder#sslContextCustomizer} for TLS callback timing, ownership, and mutual
 * authentication requirements.
 *
 * @param hostname the hostname or IP address to connect to.
 * @param port the port to connect to.
 * @param connectTimeout the connect timeout.
 * @param connectPersistent whether to connect persistently.
 * @param reconnectLazy whether to reconnect lazily.
 * @param eventLoopGroup the {@link EventLoopGroup} to use.
 * @param executor the {@link ExecutorService} to use.
 * @param bootstrapCustomizer a {@link Consumer} that can be used to customize the Netty {@link
 *     Bootstrap}.
 * @param pipelineCustomizer a {@link Consumer} that can be used to customize the Netty {@link
 *     ChannelPipeline}.
 * @param channelFsmCustomizer a {@link Consumer} that can be used to customize the {@link
 *     ChannelFsmConfigBuilder}.
 * @param tlsEnabled whether to enable TLS (Modbus/TCP Security).
 * @param keyManagerFactory the {@link KeyManagerFactory} to use if TLS is enabled.
 * @param trustManagerFactory the {@link TrustManagerFactory} to use if TLS is enabled.
 * @param sslContextCustomizer the callback that customizes TLS context construction.
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
    Consumer<ChannelPipeline> pipelineCustomizer,
    Consumer<ChannelFsmConfigBuilder> channelFsmCustomizer,
    boolean tlsEnabled,
    Optional<KeyManagerFactory> keyManagerFactory,
    Optional<TrustManagerFactory> trustManagerFactory,
    Consumer<SslContextBuilder> sslContextCustomizer) {

  private static final Consumer<SslContextBuilder> DEFAULT_SSL_CONTEXT_CUSTOMIZER = b -> {};

  public NettyClientTransportConfig {
    Objects.requireNonNull(sslContextCustomizer, "sslContextCustomizer must not be null");
  }

  /**
   * Create a configuration with default TLS context construction.
   *
   * @param hostname the hostname or IP address to connect to.
   * @param port the transport port.
   * @param connectTimeout the connect timeout.
   * @param connectPersistent whether to connect persistently.
   * @param reconnectLazy whether to reconnect lazily.
   * @param eventLoopGroup the event loop group to use.
   * @param executor the executor to use.
   * @param bootstrapCustomizer the bootstrap customizer.
   * @param pipelineCustomizer the pipeline customizer.
   * @param channelFsmCustomizer the channel FSM customizer.
   * @param tlsEnabled whether to enable TLS.
   * @param keyManagerFactory the key manager factory to use if TLS is enabled.
   * @param trustManagerFactory the trust manager factory to use if TLS is enabled.
   */
  public NettyClientTransportConfig(
      String hostname,
      int port,
      Duration connectTimeout,
      boolean connectPersistent,
      boolean reconnectLazy,
      EventLoopGroup eventLoopGroup,
      ExecutorService executor,
      Consumer<Bootstrap> bootstrapCustomizer,
      Consumer<ChannelPipeline> pipelineCustomizer,
      Consumer<ChannelFsmConfigBuilder> channelFsmCustomizer,
      boolean tlsEnabled,
      Optional<KeyManagerFactory> keyManagerFactory,
      Optional<TrustManagerFactory> trustManagerFactory) {
    this(
        hostname,
        port,
        connectTimeout,
        connectPersistent,
        reconnectLazy,
        eventLoopGroup,
        executor,
        bootstrapCustomizer,
        pipelineCustomizer,
        channelFsmCustomizer,
        tlsEnabled,
        keyManagerFactory,
        trustManagerFactory,
        DEFAULT_SSL_CONTEXT_CUSTOMIZER);
  }

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

    /** The hostname or IP address to connect to. */
    public String hostname;

    /** The port to connect to. */
    public int port = -1;

    /** The connect timeout. */
    public Duration connectTimeout = Duration.ofSeconds(5);

    /** Whether to connect persistently. */
    public boolean connectPersistent = true;

    /** Whether to reconnect lazily. */
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

    /** A {@link Consumer} that can be used to customize the Netty {@link Bootstrap}. */
    public Consumer<Bootstrap> bootstrapCustomizer = b -> {};

    /** A {@link Consumer} that can be used to customize the Netty {@link ChannelPipeline}. */
    public Consumer<ChannelPipeline> pipelineCustomizer = p -> {};

    /** A {@link Consumer} that can be used to customize the {@link ChannelFsmConfigBuilder}. */
    public Consumer<ChannelFsmConfigBuilder> channelFsmCustomizer = c -> {};

    /** Whether to enable TLS (Modbus/TCP Security). */
    public boolean tlsEnabled = false;

    /** The {@link KeyManagerFactory} to use if TLS is enabled. */
    public KeyManagerFactory keyManagerFactory = null;

    /** The {@link TrustManagerFactory} to use if TLS is enabled. */
    public TrustManagerFactory trustManagerFactory = null;

    /**
     * Customizes the TLS context builder for each connection attempt, including reconnects when TLS
     * is enabled.
     *
     * <p>The configured key and trust managers, required client authentication, and TLS 1.2/1.3
     * protocols are applied before this callback. Overrides must preserve mutual authentication,
     * including trusted peer certificates and a client identity. Servers must require client
     * certificates.
     *
     * <p>Only configure the supplied builder; do not retain it or call its build method. The
     * transport builds and owns a context per connection and releases its native reference after
     * creating the TLS handler. Netty releases the handler's engine reference when the handler is
     * removed.
     */
    public Consumer<SslContextBuilder> sslContextCustomizer = DEFAULT_SSL_CONTEXT_CUSTOMIZER;

    /**
     * Set the hostname or IP address to connect to.
     *
     * @param hostname the hostname or IP address to connect to.
     * @return this Builder.
     */
    public Builder setHostname(String hostname) {
      this.hostname = hostname;
      return this;
    }

    /**
     * Set the port to connect to.
     *
     * @param port the port to connect to.
     * @return this Builder.
     */
    public Builder setPort(int port) {
      this.port = port;
      return this;
    }

    /**
     * Set the connect timeout.
     *
     * @param connectTimeout the connect timeout.
     * @return this Builder.
     */
    public Builder setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    /**
     * Set whether to connect persistently.
     *
     * @param connectPersistent whether to connect persistently.
     * @return this Builder.
     */
    public Builder setConnectPersistent(boolean connectPersistent) {
      this.connectPersistent = connectPersistent;
      return this;
    }

    /**
     * Set whether to reconnect lazily.
     *
     * @param reconnectLazy whether to reconnect lazily.
     * @return this Builder.
     */
    public Builder setReconnectLazy(boolean reconnectLazy) {
      this.reconnectLazy = reconnectLazy;
      return this;
    }

    /**
     * Set the {@link EventLoopGroup} to use.
     *
     * @param eventLoopGroup the {@link EventLoopGroup} to use.
     * @return this Builder.
     * @see Netty#sharedEventLoop()
     */
    public Builder setEventLoopGroup(EventLoopGroup eventLoopGroup) {
      this.eventLoopGroup = eventLoopGroup;
      return this;
    }

    /**
     * Set the {@link ExecutorService} to use.
     *
     * @param executor the {@link ExecutorService} to use.
     * @return this Builder.
     * @see Modbus#sharedExecutor()
     */
    public Builder setExecutor(ExecutorService executor) {
      this.executor = executor;
      return this;
    }

    /**
     * Set the {@link Consumer} that can be used to customize the Netty {@link Bootstrap}.
     *
     * @param bootstrapCustomizer the {@link Consumer} that can be used to customize the Netty
     *     {@link Bootstrap}.
     * @return this Builder.
     */
    public Builder setBootstrapCustomizer(Consumer<Bootstrap> bootstrapCustomizer) {
      this.bootstrapCustomizer = bootstrapCustomizer;
      return this;
    }

    /**
     * Set the {@link Consumer} that can be used to customize the Netty {@link ChannelPipeline}.
     *
     * @param pipelineCustomizer the {@link Consumer} that can be used to customize the Netty {@link
     *     ChannelPipeline}.
     * @return this Builder.
     */
    public Builder setPipelineCustomizer(Consumer<ChannelPipeline> pipelineCustomizer) {
      this.pipelineCustomizer = pipelineCustomizer;
      return this;
    }

    /**
     * Set the {@link Consumer} that can be used to customize the {@link ChannelFsmConfigBuilder}.
     *
     * @param channelFsmCustomizer the {@link Consumer} that can be used to customize the {@link
     *     ChannelFsmConfigBuilder}.
     * @return this Builder.
     */
    public Builder setChannelFsmCustomizer(Consumer<ChannelFsmConfigBuilder> channelFsmCustomizer) {
      this.channelFsmCustomizer = channelFsmCustomizer;
      return this;
    }

    /**
     * Set whether to enable TLS (Modbus/TCP Security).
     *
     * @param tlsEnabled whether to enable TLS (Modbus/TCP Security).
     * @return this Builder.
     */
    public Builder setTlsEnabled(boolean tlsEnabled) {
      this.tlsEnabled = tlsEnabled;
      return this;
    }

    /**
     * Set the {@link KeyManagerFactory} to use if TLS is enabled.
     *
     * @param keyManagerFactory the {@link KeyManagerFactory} to use if TLS is enabled.
     * @return this Builder.
     */
    public Builder setKeyManagerFactory(KeyManagerFactory keyManagerFactory) {
      this.keyManagerFactory = keyManagerFactory;
      return this;
    }

    /**
     * Set the {@link TrustManagerFactory} to use if TLS is enabled.
     *
     * @param trustManagerFactory the {@link TrustManagerFactory} to use if TLS is enabled.
     * @return this Builder.
     */
    public Builder setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
      this.trustManagerFactory = trustManagerFactory;
      return this;
    }

    /**
     * Set the callback that customizes TLS context construction after the transport's defaults.
     *
     * @param sslContextCustomizer the TLS context builder customizer.
     * @return this Builder.
     * @see #sslContextCustomizer
     */
    public Builder setSslContextCustomizer(Consumer<SslContextBuilder> sslContextCustomizer) {
      this.sslContextCustomizer = sslContextCustomizer;
      return this;
    }

    public NettyClientTransportConfig build() {
      if (hostname == null) {
        throw new NullPointerException("hostname must not be null");
      }
      if (port == -1) {
        port = tlsEnabled ? 802 : 502;
      }
      if (eventLoopGroup == null) {
        eventLoopGroup = Netty.sharedEventLoop();
      }
      if (executor == null) {
        executor = Modbus.sharedExecutor();
      }
      if (tlsEnabled) {
        if (keyManagerFactory == null) {
          throw new NullPointerException("keyManagerFactory must not be null");
        }
        if (trustManagerFactory == null) {
          throw new NullPointerException("trustManagerFactory must not be null");
        }
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
          pipelineCustomizer,
          channelFsmCustomizer,
          tlsEnabled,
          Optional.ofNullable(keyManagerFactory),
          Optional.ofNullable(trustManagerFactory),
          sslContextCustomizer);
    }
  }
}
