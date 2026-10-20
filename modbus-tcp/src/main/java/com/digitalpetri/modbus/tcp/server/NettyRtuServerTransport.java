package com.digitalpetri.modbus.tcp.server;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.Accumulated;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.Idle;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.ParseError;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.ParserState;
import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusRtuRequestContext;
import com.digitalpetri.modbus.server.ModbusRtuServerTransport;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus RTU/TCP server transport; a {@link ModbusRtuServerTransport} that sends and receives
 * {@link ModbusRtuFrame}s over TCP.
 *
 * <p>Unbinding completes after the listening socket is released.
 */
public class NettyRtuServerTransport implements ModbusRtuServerTransport {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AtomicReference<FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame>>
      frameReceiver = new AtomicReference<>();

  private final AtomicReference<ListeningSocketChannel> serverChannel = new AtomicReference<>();

  private final AtomicReference<Channel> clientChannel = new AtomicReference<>();
  private CompletableFuture<Void> unbindFuture = CompletableFuture.completedFuture(null);

  private final ExecutionQueue executionQueue;
  private final NettyServerTransportConfig config;
  private final LongSupplier nanoTime;

  public NettyRtuServerTransport(NettyServerTransportConfig config) {
    this(config, System::nanoTime);
  }

  /**
   * Create a transport that measures partial frame silence with {@code nanoTime}.
   *
   * @param config the transport configuration.
   * @param nanoTime the source of monotonic time, in nanoseconds.
   */
  NettyRtuServerTransport(NettyServerTransportConfig config, LongSupplier nanoTime) {
    this.config = config;
    this.nanoTime = nanoTime;

    executionQueue = new ExecutionQueue(config.executor(), 1);
  }

  @Override
  public CompletionStage<Void> bind() {
    final var future = new CompletableFuture<Void>();

    var bootstrap = new ServerBootstrap();

    bootstrap
        .channelFactory(ListeningSocketChannel::new)
        .childHandler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel channel) throws Exception {
                if (clientChannel.compareAndSet(null, channel)) {
                  channel
                      .closeFuture()
                      .addListener(f -> clientChannel.compareAndSet(channel, null));
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

                  channel.pipeline().addLast(new ModbusRtuServerFrameReceiver());

                  config.pipelineCustomizer().accept(channel.pipeline());
                } else {
                  channel.close();
                }
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
  public synchronized CompletionStage<Void> unbind() {
    ListeningSocketChannel channel = serverChannel.getAndSet(null);

    if (channel != null) {
      var future = new CompletableFuture<Void>();
      // Install the shared completion before close can reenter unbind() through a handler.
      unbindFuture = future;
      channel
          .closeSocket()
          .whenComplete(
              (ignored, error) -> {
                Channel ch = clientChannel.getAndSet(null);
                if (ch != null) {
                  ch.close();
                }
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

  @Override
  public void receive(FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame> frameReceiver) {
    this.frameReceiver.set(frameReceiver);
  }

  /**
   * Receives request frames for one connection.
   *
   * <p>Each connection has its own parser. When bytes arrive after at least the configured partial
   * frame timeout of silence, the incomplete frame is discarded and the new bytes start a new
   * frame.
   */
  private class ModbusRtuServerFrameReceiver extends SimpleChannelInboundHandler<ByteBuf> {

    private final ModbusRtuRequestFrameParser frameParser = new ModbusRtuRequestFrameParser();
    private final long partialFrameTimeoutNanos = config.partialFrameTimeout().toNanos();

    private long lastReceivedNanos;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) {
      byte[] data = new byte[buffer.readableBytes()];
      buffer.readBytes(data);

      long now = nanoTime.getAsLong();
      if (!(frameParser.getState() instanceof Idle)
          && now - lastReceivedNanos >= partialFrameTimeoutNanos) {

        logger.debug("Discarding an incomplete request frame after silence");
        frameParser.reset();
      }
      lastReceivedNanos = now;

      ParserState state = frameParser.parse(data);

      if (state instanceof Accumulated a) {
        try {
          onFrameReceived(ctx, a.frame());
        } finally {
          frameParser.reset();
        }
      } else if (state instanceof ParseError e) {
        logger.error("Error parsing frame: {}", e.message());

        frameParser.reset();
        ctx.close();
      }
    }

    private void onFrameReceived(ChannelHandlerContext ctx, ModbusRtuFrame requestFrame) {
      FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame> frameReceiver =
          NettyRtuServerTransport.this.frameReceiver.get();

      if (frameReceiver != null) {
        executionQueue.submit(
            () -> {
              try {
                ModbusRtuFrame responseFrame =
                    frameReceiver.receive(new NettyRequestContext(ctx), requestFrame);

                if (requestFrame.unitId() == 0 || responseFrame == null) {
                  return;
                }

                ByteBuf buffer = Unpooled.buffer();
                buffer.writeByte(responseFrame.unitId());
                buffer.writeBytes(responseFrame.pdu());
                buffer.writeBytes(responseFrame.crc());

                ctx.channel().writeAndFlush(buffer);
              } catch (UnknownUnitIdException e) {
                logger.debug("Ignoring request for unknown unit id: {}", requestFrame.unitId());
              } catch (Exception e) {
                logger.error("Error handling frame: {}", e.getMessage(), e);

                ctx.close();
              }
            });
      }
    }
  }

  /**
   * Create a new {@link NettyRtuServerTransport} with a callback that allows customizing the
   * configuration.
   *
   * @param configure a {@link Consumer} that accepts a {@link NettyServerTransportConfig.Builder}
   *     instance to configure.
   * @return a new {@link NettyRtuServerTransport}.
   */
  public static NettyRtuServerTransport create(
      Consumer<NettyServerTransportConfig.Builder> configure) {

    var builder = new NettyServerTransportConfig.Builder();
    configure.accept(builder);
    return new NettyRtuServerTransport(builder.build());
  }
}
