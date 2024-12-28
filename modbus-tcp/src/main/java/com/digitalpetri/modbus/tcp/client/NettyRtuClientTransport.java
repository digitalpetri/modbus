package com.digitalpetri.modbus.tcp.client;

import com.digitalpetri.fsm.FsmContext;
import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.Accumulated;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.ParseError;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.ParserState;
import com.digitalpetri.modbus.client.ModbusRtuClientTransport;
import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import com.digitalpetri.netty.fsm.ChannelActions;
import com.digitalpetri.netty.fsm.ChannelFsm;
import com.digitalpetri.netty.fsm.ChannelFsmConfig;
import com.digitalpetri.netty.fsm.ChannelFsmFactory;
import com.digitalpetri.netty.fsm.Event;
import com.digitalpetri.netty.fsm.State;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import io.netty.handler.ssl.SslProtocols;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus RTU/TCP client transport; a {@link ModbusRtuClientTransport} that sends and receives
 * {@link ModbusRtuFrame}s over TCP.
 */
public class NettyRtuClientTransport implements ModbusRtuClientTransport {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ModbusRtuResponseFrameParser frameParser = new ModbusRtuResponseFrameParser();
  private final AtomicReference<Consumer<ModbusRtuFrame>> frameReceiver = new AtomicReference<>();

  private final ChannelFsm channelFsm;
  private final ExecutionQueue executionQueue;

  private final NettyClientTransportConfig config;

  public NettyRtuClientTransport(NettyClientTransportConfig config) {
    this.config = config;

    channelFsm =
        ChannelFsmFactory.newChannelFsm(
            ChannelFsmConfig.newBuilder()
                .setExecutor(config.executor())
                .setLazy(config.reconnectLazy())
                .setPersistent(config.connectPersistent())
                .setChannelActions(new ModbusRtuChannelActions())
                .build());

    channelFsm.addTransitionListener(
        (from, to, via) -> logger.debug("onStateTransition: {} -> {} via {}", from, to, via));

    executionQueue = new ExecutionQueue(config.executor());
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
  public boolean isConnected() {
    return channelFsm.getState() == State.Connected;
  }

  @Override
  public CompletionStage<Void> send(ModbusRtuFrame frame) {
    return channelFsm
        .getChannel()
        .thenCompose(
            channel -> {
              ByteBuf buffer = Unpooled.buffer();
              buffer.writeByte(frame.unitId());
              buffer.writeBytes(frame.pdu());
              buffer.writeBytes(frame.crc());

              var future = new CompletableFuture<Void>();

              channel
                  .writeAndFlush(buffer)
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
  public void receive(Consumer<ModbusRtuFrame> frameReceiver) {
    this.frameReceiver.set(frameReceiver);
  }

  @Override
  public void resetFrameParser() {
    frameParser.reset();
  }

  private class ModbusRtuClientFrameReceiver extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) {
      byte[] data = new byte[buffer.readableBytes()];
      buffer.readBytes(data);

      ParserState state = frameParser.parse(data);

      if (state instanceof Accumulated a) {
        try {
          onFrameReceived(a.frame());
        } finally {
          frameParser.reset();
        }
      } else if (state instanceof ParseError e) {
        logger.error("Error parsing frame: {}", e.error());

        frameParser.reset();
        ctx.close();
      }
    }

    private void onFrameReceived(ModbusRtuFrame frame) {
      Consumer<ModbusRtuFrame> frameReceiver = NettyRtuClientTransport.this.frameReceiver.get();
      if (frameReceiver != null) {
        executionQueue.submit(() -> frameReceiver.accept(frame));
      }
    }
  }

  private class ModbusRtuChannelActions implements ChannelActions {

    @Override
    public CompletableFuture<Channel> connect(FsmContext<State, Event> fsmContext) {
      var bootstrap =
          new Bootstrap()
              .channel(NioSocketChannel.class)
              .group(config.eventLoopGroup())
              .option(
                  ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis())
              .option(ChannelOption.TCP_NODELAY, Boolean.TRUE)
              .handler(
                  new ChannelInitializer<SocketChannel>() {
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
                            .addLast(
                                sslContext.newHandler(
                                    channel.alloc(), config.hostname(), config.port()));
                      }

                      channel.pipeline().addLast(new ModbusRtuClientFrameReceiver());

                      config.pipelineCustomizer().accept(channel.pipeline());
                    }
                  });

      config.bootstrapCustomizer().accept(bootstrap);

      var future = new CompletableFuture<Channel>();

      bootstrap
          .connect(config.hostname(), config.port())
          .addListener(
              (ChannelFutureListener)
                  channelFuture -> {
                    if (channelFuture.isSuccess()) {
                      future.complete(channelFuture.channel());
                    } else {
                      future.completeExceptionally(channelFuture.cause());
                    }
                  });

      return future;
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
   * Create a new {@link NettyRtuClientTransport} with a callback that allows customizing the
   * configuration.
   *
   * @param configure a {@link Consumer} that accepts a {@link NettyClientTransportConfig.Builder}
   *     instance to configure.
   * @return a new {@link NettyRtuClientTransport}.
   */
  public static NettyRtuClientTransport create(
      Consumer<NettyClientTransportConfig.Builder> configure) {

    var config = NettyClientTransportConfig.create(configure);

    return new NettyRtuClientTransport(config);
  }
}
