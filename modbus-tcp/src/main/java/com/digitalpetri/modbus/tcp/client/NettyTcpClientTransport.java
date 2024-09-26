package com.digitalpetri.modbus.tcp.client;

import com.digitalpetri.fsm.FsmContext;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.client.ModbusTcpClientTransport;
import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import com.digitalpetri.modbus.tcp.ModbusTcpCodec;
import com.digitalpetri.netty.fsm.ChannelActions;
import com.digitalpetri.netty.fsm.ChannelFsm;
import com.digitalpetri.netty.fsm.ChannelFsmConfig;
import com.digitalpetri.netty.fsm.ChannelFsmFactory;
import com.digitalpetri.netty.fsm.Event;
import com.digitalpetri.netty.fsm.State;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus/TCP client transport; a {@link ModbusTcpClientTransport} that sends and receives
 * {@link ModbusTcpFrame}s over TCP.
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

    channelFsm = ChannelFsmFactory.newChannelFsm(
        ChannelFsmConfig.newBuilder()
            .setExecutor(config.executor())
            .setLazy(config.reconnectLazy())
            .setPersistent(config.connectPersistent())
            .setChannelActions(new ModbusTcpChannelActions())
            .setLoggerName("com.digitalpetri.modbus.client.ChannelFsm")
            .build()
    );

    executionQueue = new ExecutionQueue(config.executor());

    channelFsm.addTransitionListener(
        (from, to, via) -> {
          logger.debug("onStateTransition: {} -> {} via {}", from, to, via);

          executionQueue.submit(() -> handleStateTransition(from, to, via));
        }
    );
  }

  private void handleStateTransition(State from, State to, Event via) {
    if (from != State.Connected && to == State.Connected) {
      connectionListeners.forEach(ConnectionListener::onConnection);
    } else if (from == State.Connected && to != State.Connected) {
      connectionListeners.forEach(ConnectionListener::onConnectionLost);
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
    return channelFsm.getChannel().thenCompose(channel -> {
      var future = new CompletableFuture<Void>();

      channel.writeAndFlush(frame).addListener((ChannelFutureListener) channelFuture -> {
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
  }

  private class ModbusTcpChannelActions implements ChannelActions {

    @Override
    public CompletableFuture<Channel> connect(FsmContext<State, Event> fsmContext) {
      var bootstrap = new Bootstrap()
          .channel(NioSocketChannel.class)
          .group(config.eventLoopGroup())
          .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis())
          .option(ChannelOption.TCP_NODELAY, Boolean.TRUE)
          .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel channel) {
              channel.pipeline().addLast(new ModbusTcpCodec());
              channel.pipeline().addLast(new ModbusTcpFrameHandler());

              config.pipelineCustomizer().accept(channel.pipeline());
            }
          });

      config.bootstrapCustomizer().accept(bootstrap);

      var future = new CompletableFuture<Channel>();

      bootstrap.connect(config.hostname(), config.port()).addListener(
          (ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
              future.complete(channelFuture.channel());
            } else {
              future.completeExceptionally(channelFuture.cause());
            }
          }
      );

      return future;
    }

    @Override
    public CompletableFuture<Void> disconnect(
        FsmContext<State, Event> fsmContext, Channel channel) {

      var future = new CompletableFuture<Void>();

      channel.close().addListener(
          (ChannelFutureListener) channelFuture ->
              future.complete(null)
      );

      return future;
    }

  }

  /**
   * Create a new {@link NettyTcpClientTransport} with a callback that allows customizing the
   * configuration.
   *
   * @param configure a {@link Consumer} that accepts a
   *     {@link NettyClientTransportConfig.Builder} instance to configure.
   * @return a new {@link NettyTcpClientTransport}.
   */
  public static NettyTcpClientTransport create(
      Consumer<NettyClientTransportConfig.Builder> configure
  ) {

    var config = NettyClientTransportConfig.create(configure);

    return new NettyTcpClientTransport(config);
  }

  public interface ConnectionListener {

    /**
     * Callback invoked when the transport has connected.
     */
    void onConnection();

    /**
     * Callback invoked when the transport has disconnected.
     *
     * <p> Note that implementations do not need to initiate a reconnect, as this is handled
     * automatically by {@link NettyTcpClientTransport}.
     */
    void onConnectionLost();

  }

}
