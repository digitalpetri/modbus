package com.digitalpetri.modbus.tcp.server;

import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpRequestContext;
import com.digitalpetri.modbus.server.ModbusTcpServerTransport;
import com.digitalpetri.modbus.tcp.ModbusTcpCodec;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProtocols;
import io.netty.util.ReferenceCountUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus/TCP transport; a {@link ModbusTcpServerTransport} that sends and receives {@link
 * ModbusTcpFrame}s over TCP.
 *
 * <p>Unbinding completes after the listening socket is released.
 */
public class NettyTcpServerTransport implements ModbusTcpServerTransport {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AtomicReference<FrameReceiver<ModbusTcpRequestContext, ModbusTcpFrame>>
      frameReceiver = new AtomicReference<>();

  private final AtomicReference<ListeningSocketChannel> serverChannel = new AtomicReference<>();
  private final List<Channel> clientChannels = new CopyOnWriteArrayList<>();
  private CompletableFuture<Void> unbindFuture = CompletableFuture.completedFuture(null);

  private final ExecutionQueue executionQueue;
  private final NettyServerTransportConfig config;
  private final LongSupplier nanoTime;

  public NettyTcpServerTransport(NettyServerTransportConfig config) {
    this(config, System::nanoTime);
  }

  /**
   * Create a transport that measures partial frame silence with {@code nanoTime}.
   *
   * @param config the transport configuration.
   * @param nanoTime the source of monotonic time, in nanoseconds.
   */
  NettyTcpServerTransport(NettyServerTransportConfig config, LongSupplier nanoTime) {
    this.config = config;
    this.nanoTime = nanoTime;

    executionQueue = new ExecutionQueue(config.executor(), 1);
  }

  @Override
  public void receive(FrameReceiver<ModbusTcpRequestContext, ModbusTcpFrame> frameReceiver) {
    this.frameReceiver.set(frameReceiver);
  }

  @Override
  public CompletableFuture<Void> bind() {
    final var future = new CompletableFuture<Void>();

    var bootstrap = new ServerBootstrap();

    bootstrap
        .channelFactory(ListeningSocketChannel::new)
        .childHandler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel channel) throws Exception {
                clientChannels.add(channel);
                channel.closeFuture().addListener(f -> clientChannels.remove(channel));

                if (config.tlsEnabled()) {
                  SslContextBuilder sslContextBuilder =
                      SslContextBuilder.forServer(config.keyManagerFactory().orElseThrow())
                          .clientAuth(ClientAuth.REQUIRE)
                          .trustManager(config.trustManagerFactory().orElseThrow())
                          .protocols(SslProtocols.TLS_v1_2, SslProtocols.TLS_v1_3);

                  config.sslContextCustomizer().accept(sslContextBuilder);
                  SslContext sslContext = sslContextBuilder.build();

                  try {
                    channel.pipeline().addLast(sslContext.newHandler(channel.alloc()));
                  } finally {
                    // Native TLS engines retain their context until the handler releases the
                    // engine.
                    ReferenceCountUtil.release(sslContext);
                  }
                }

                // ModbusTcpCodec encodes responses; requests arrive already decoded.
                channel
                    .pipeline()
                    .addLast(new ModbusTcpRequestDecoder(config.partialFrameTimeout(), nanoTime))
                    .addLast(new ModbusTcpCodec())
                    .addLast(new ModbusTcpFrameHandler());

                config.pipelineCustomizer().accept(channel.pipeline());
              }
            });

    bootstrap.group(config.eventLoopGroup());
    bootstrap.option(ChannelOption.SO_REUSEADDR, Boolean.TRUE);
    bootstrap.childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE);

    config.bootstrapCustomizer().accept(bootstrap);

    bootstrap
        .bind(config.bindAddress(), config.port())
        .addListener(
            (ChannelFutureListener)
                channelFuture -> {
                  if (channelFuture.isSuccess()) {
                    serverChannel.set((ListeningSocketChannel) channelFuture.channel());

                    future.complete(null);
                  } else {
                    future.completeExceptionally(channelFuture.cause());
                  }
                });

    return future;
  }

  @Override
  public synchronized CompletableFuture<Void> unbind() {
    ListeningSocketChannel channel = serverChannel.getAndSet(null);

    if (channel != null) {
      var future = new CompletableFuture<Void>();
      // Install the shared completion before close can reenter unbind() through a handler.
      unbindFuture = future;
      channel
          .closeSocket()
          .whenComplete(
              (ignored, error) -> {
                clientChannels.forEach(Channel::close);
                clientChannels.clear();
              })
          .whenComplete(
              (ignored, error) -> {
                if (error == null) {
                  future.complete(null);
                } else {
                  future.completeExceptionally(error);
                }
              });
      return future.copy();
    }
    // Callers may cancel or complete their own future without affecting release or client cleanup.
    return unbindFuture.copy();
  }

  private class ModbusTcpFrameHandler extends SimpleChannelInboundHandler<ModbusTcpFrame> {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      logger.error("Exception caught", cause);
      ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ModbusTcpFrame requestFrame) {
      FrameReceiver<ModbusTcpRequestContext, ModbusTcpFrame> frameReceiver =
          NettyTcpServerTransport.this.frameReceiver.get();

      if (frameReceiver != null) {
        executionQueue.submit(
            () -> {
              try {
                ModbusTcpFrame responseFrame =
                    frameReceiver.receive(new NettyRequestContext(ctx), requestFrame);

                ctx.channel().writeAndFlush(responseFrame);
              } catch (UnknownUnitIdException e) {
                logger.debug(
                    "Ignoring request for unknown unit id: {}", requestFrame.header().unitId());
              } catch (Exception e) {
                logger.error("Error handling frame: {}", e.getMessage(), e);

                ctx.close();
              }
            });
      }
    }
  }

  /**
   * Create a new {@link NettyTcpServerTransport} with a callback that allows customizing the
   * configuration.
   *
   * @param configure a {@link Consumer} that accepts a {@link NettyServerTransportConfig.Builder}
   *     instance to configure.
   * @return a new {@link NettyTcpServerTransport}.
   */
  public static NettyTcpServerTransport create(
      Consumer<NettyServerTransportConfig.Builder> configure) {

    var builder = new NettyServerTransportConfig.Builder();
    configure.accept(builder);
    return new NettyTcpServerTransport(builder.build());
  }
}
