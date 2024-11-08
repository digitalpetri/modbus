package com.digitalpetri.modbus.tcp.server;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.Accumulated;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.ParseError;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.ParserState;
import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import com.digitalpetri.modbus.server.ModbusRtuServerTransport;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus RTU/TCP server transport; a {@link ModbusRtuServerTransport} that sends and receives
 * {@link ModbusRtuFrame}s over TCP.
 */
public class NettyRtuServerTransport implements ModbusRtuServerTransport {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AtomicReference<FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame>>
      frameReceiver = new AtomicReference<>();
  private final ModbusRtuRequestFrameParser frameParser = new ModbusRtuRequestFrameParser();

  private final AtomicReference<ServerSocketChannel> serverChannel = new AtomicReference<>();

  private final AtomicReference<Channel> clientChannel = new AtomicReference<>();

  private final ExecutionQueue executionQueue;
  private final NettyServerTransportConfig config;

  public NettyRtuServerTransport(NettyServerTransportConfig config) {
    this.config = config;

    executionQueue = new ExecutionQueue(config.executor(), 1);
  }

  @Override
  public CompletionStage<Void> bind() {
    final var future = new CompletableFuture<Void>();

    var bootstrap = new ServerBootstrap();

    bootstrap.channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            if (clientChannel.compareAndSet(null, ch)) {
              ch.pipeline()
                  .addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                      clientChannel.set(null);
                    }
                  })
                  .addLast(new ModbusRtuServerFrameReceiver());
            } else {
              ch.close();
            }
          }
        });

    bootstrap.group(config.eventLoopGroup());
    bootstrap.option(ChannelOption.SO_REUSEADDR, Boolean.TRUE);
    bootstrap.childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE);

    bootstrap.bind(config.bindAddress(), config.port())
        .addListener((ChannelFutureListener) channelFuture -> {
          if (channelFuture.isSuccess()) {
            serverChannel.set((ServerSocketChannel) channelFuture.channel());

            future.complete(null);
          } else {
            future.completeExceptionally(channelFuture.cause());
          }
        });

    return future;
  }

  @Override
  public CompletionStage<Void> unbind() {
    ServerSocketChannel channel = serverChannel.getAndSet(null);

    if (channel != null) {
      var future = new CompletableFuture<Void>();
      channel.close().addListener((ChannelFutureListener) cf -> {
        Channel ch = clientChannel.getAndSet(null);
        if (ch != null) {
          ch.close();
        }

        if (cf.isSuccess()) {
          future.complete(null);
        } else {
          future.completeExceptionally(cf.cause());
        }
      });
      return future;
    } else {
      return CompletableFuture.completedFuture(null);
    }
  }

  @Override
  public void receive(FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame> frameReceiver) {
    this.frameReceiver.set(frameReceiver);
  }

  private class ModbusRtuServerFrameReceiver extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) {
      byte[] data = new byte[buffer.readableBytes()];
      buffer.readBytes(data);

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
        executionQueue.submit(() -> {
          try {
            ModbusRtuFrame responseFrame =
                frameReceiver.receive(requestContext(ctx), requestFrame);

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

    private static ModbusRtuTcpRequestContext requestContext(ChannelHandlerContext ctx) {
      return new ModbusRtuTcpRequestContext() {
        @Override
        public SocketAddress localAddress() {
          return ctx.channel().localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
          return ctx.channel().remoteAddress();
        }
      };
    }

  }

  /**
   * Create a new {@link NettyRtuServerTransport} with a callback that allows customizing the
   * configuration.
   *
   * @param configure a {@link Consumer} that accepts a
   *     {@link NettyServerTransportConfig.Builder} instance to configure.
   * @return a new {@link NettyRtuServerTransport}.
   */
  public static NettyRtuServerTransport create(
      Consumer<NettyServerTransportConfig.Builder> configure
  ) {

    var builder = new NettyServerTransportConfig.Builder();
    configure.accept(builder);
    return new NettyRtuServerTransport(builder.build());
  }

}
