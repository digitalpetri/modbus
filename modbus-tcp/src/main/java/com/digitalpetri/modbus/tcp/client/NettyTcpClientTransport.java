package com.digitalpetri.modbus.tcp.client;

import com.digitalpetri.fsm.FsmContext;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.client.ModbusTcpClientTransport;
import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import com.digitalpetri.modbus.tcp.ModbusTcpCodec;
import com.digitalpetri.netty.fsm.*;
import com.digitalpetri.netty.fsm.ChannelFsm.TransitionListener;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProtocols;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus/TCP client transport; a {@link ModbusTcpClientTransport} that sends and receives {@link
 * ModbusTcpFrame}s over TCP.
 */
public class NettyTcpClientTransport implements ModbusTcpClientTransport {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AtomicReference<Consumer<ModbusTcpFrame>> frameReceiver = new AtomicReference<>();

  private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

  private final ChannelFsm channelFsm;
  private final ExecutionQueue executionQueue;

  private final NettyClientTransportConfig config;

  public NettyTcpClientTransport(NettyClientTransportConfig config) {
    this.config = config;

    ChannelFsmConfigBuilder channelFsmConfigBuilder =
        ChannelFsmConfig.newBuilder()
            .setExecutor(config.executor())
            .setLazy(config.reconnectLazy())
            .setPersistent(config.connectPersistent())
            .setChannelActions(new ModbusTcpChannelActions())
            .setLoggerName("com.digitalpetri.modbus.client.ChannelFsm");

    config.channelFsmCustomizer().accept(channelFsmConfigBuilder);

    channelFsm = ChannelFsmFactory.newChannelFsm(channelFsmConfigBuilder.build());

    executionQueue = new ExecutionQueue(config.executor());

    channelFsm.addTransitionListener(
        (from, to, via) -> {
          logger.debug("onStateTransition: {} -> {} via {}", from, to, via);

          maybeNotifyConnectionListeners(from, to);
        });
  }

  @SuppressWarnings("DuplicatedCode")
  private void maybeNotifyConnectionListeners(State from, State to) {
    if (connectionListeners.isEmpty()) {
      return;
    }

    if (from != State.Connected && to == State.Connected) {
      executionQueue.submit(() -> connectionListeners.forEach(ConnectionListener::onConnection));
    } else if (from == State.Connected && to != State.Connected) {
      executionQueue.submit(
          () -> connectionListeners.forEach(ConnectionListener::onConnectionLost));
    }
  }

  @Override
  public CompletableFuture<Void> connect() {
    return channelFsm.connect().thenApply(c -> null);
  }

  @Override
  public CompletableFuture<Void> disconnect() {
    return channelFsm.disconnect();
  }

  @Override
  public CompletionStage<Void> send(ModbusTcpFrame frame) {
    return channelFsm
        .getChannel()
        .thenCompose(
            channel -> {
              var future = new CompletableFuture<Void>();

              channel
                  .writeAndFlush(frame)
                  .addListener(
                      (ChannelFutureListener)
                          channelFuture -> {
                            if (channelFuture.isSuccess()) {
                              future.complete(null);
                            } else {
                              future.completeExceptionally(channelFuture.cause());
                            }
                          });

              return future;
            });
  }

  @Override
  public void receive(Consumer<ModbusTcpFrame> frameReceiver) {
    this.frameReceiver.set(frameReceiver);
  }

  @Override
  public boolean isConnected() {
    return channelFsm.getState() == State.Connected;
  }

  /**
   * Get the {@link ChannelFsm} used by this transport.
   *
   * <p>This should not generally be used by client code except perhaps to add a {@link
   * TransitionListener} to receive more detailed callbacks about the connection status.
   *
   * @return the {@link ChannelFsm} used by this transport.
   */
  public ChannelFsm getChannelFsm() {
    return channelFsm;
  }

  /**
   * Add a {@link ConnectionListener} to this transport.
   *
   * @param listener the listener to add.
   */
  public void addConnectionListener(ConnectionListener listener) {
    connectionListeners.add(listener);
  }

  /**
   * Remove a {@link ConnectionListener} from this transport.
   *
   * @param listener the listener to remove.
   */
  public void removeConnectionListener(ConnectionListener listener) {
    connectionListeners.remove(listener);
  }

  private class ModbusTcpFrameHandler extends SimpleChannelInboundHandler<ModbusTcpFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ModbusTcpFrame frame) {
      Consumer<ModbusTcpFrame> frameReceiver = NettyTcpClientTransport.this.frameReceiver.get();
      if (frameReceiver != null) {
        executionQueue.submit(() -> frameReceiver.accept(frame));
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      logger.error("Exception caught", cause);
      ctx.close();
    }
  }

  private class ModbusTcpChannelActions implements ChannelActions {

    @Override
    public CompletableFuture<Channel> connect(FsmContext<State, Event> fsmContext) {
      var bootstrap =
          new Bootstrap()
              .channel(NioSocketChannel.class)
              .group(config.eventLoopGroup())
              .option(
                  ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis())
              .option(ChannelOption.TCP_NODELAY, Boolean.TRUE)
              .handler(newChannelInitializer());

      config.bootstrapCustomizer().accept(bootstrap);

      var future = new CompletableFuture<Channel>();

      bootstrap
          .connect(config.hostname(), config.port())
          .addListener(
              (ChannelFutureListener)
                  channelFuture -> {
                    if (channelFuture.isSuccess()) {
                      Channel channel = channelFuture.channel();

                      if (config.tlsEnabled()) {
                        channel
                            .pipeline()
                            .get(SslHandler.class)
                            .handshakeFuture()
                            .addListener(
                                handshakeFuture -> {
                                  if (handshakeFuture.isSuccess()) {
                                    future.complete(channel);
                                  } else {
                                    future.completeExceptionally(handshakeFuture.cause());
                                  }
                                });
                      } else {
                        future.complete(channel);
                      }
                    } else {
                      future.completeExceptionally(channelFuture.cause());
                    }
                  });

      return future;
    }

    private ChannelInitializer<SocketChannel> newChannelInitializer() {
      return new ChannelInitializer<>() {
        @Override
        protected void initChannel(SocketChannel channel) throws Exception {
          if (config.tlsEnabled()) {
            SslContext sslContext =
                SslContextBuilder.forClient()
                    .clientAuth(ClientAuth.REQUIRE)
                    .keyManager(config.keyManagerFactory().orElseThrow())
                    .trustManager(config.trustManagerFactory().orElseThrow())
                    .protocols(SslProtocols.TLS_v1_2, SslProtocols.TLS_v1_3)
                    .build();

            channel
                .pipeline()
                .addLast(sslContext.newHandler(channel.alloc(), config.hostname(), config.port()));
          }

          channel.pipeline().addLast(new ModbusTcpCodec());
          channel.pipeline().addLast(new ModbusTcpFrameHandler());

          config.pipelineCustomizer().accept(channel.pipeline());
        }
      };
    }

    @Override
    public CompletableFuture<Void> disconnect(
        FsmContext<State, Event> fsmContext, Channel channel) {

      var future = new CompletableFuture<Void>();

      channel.close().addListener((ChannelFutureListener) channelFuture -> future.complete(null));

      return future;
    }
  }

  /**
   * Create a new {@link NettyTcpClientTransport} with a callback that allows customizing the
   * configuration.
   *
   * @param configure a {@link Consumer} that accepts a {@link NettyClientTransportConfig.Builder}
   *     instance to configure.
   * @return a new {@link NettyTcpClientTransport}.
   */
  public static NettyTcpClientTransport create(
      Consumer<NettyClientTransportConfig.Builder> configure) {

    var config = NettyClientTransportConfig.create(configure);

    return new NettyTcpClientTransport(config);
  }

  public interface ConnectionListener {

    /** Callback invoked when the transport has connected. */
    void onConnection();

    /**
     * Callback invoked when the transport has disconnected.
     *
     * <p>Note that implementations do not need to initiate a reconnect, as this is handled
     * automatically by {@link NettyTcpClientTransport}.
     */
    void onConnectionLost();
  }
}
