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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProtocols;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus/TCP transport; a {@link ModbusTcpServerTransport} that sends and receives
 * {@link ModbusTcpFrame}s over TCP.
 */
public class NettyTcpServerTransport implements ModbusTcpServerTransport {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AtomicReference<FrameReceiver<ModbusTcpRequestContext, ModbusTcpFrame>>
      frameReceiver = new AtomicReference<>();

  private final AtomicReference<ServerSocketChannel> serverChannel = new AtomicReference<>();
  private final List<Channel> clientChannels = new CopyOnWriteArrayList<>();

  private final ExecutionQueue executionQueue;
  private final NettyServerTransportConfig config;

  public NettyTcpServerTransport(NettyServerTransportConfig config) {
    this.config = config;

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

    bootstrap.channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel channel) throws Exception {
            clientChannels.add(channel);

            if (config.tlsEnabled()) {
              SslContext sslContext =
                  SslContextBuilder.forServer(config.keyManagerFactory().orElseThrow())
                      .clientAuth(ClientAuth.REQUIRE)
                      .trustManager(config.trustManagerFactory().orElseThrow())
                      .protocols(SslProtocols.TLS_v1_2, SslProtocols.TLS_v1_3)
                      .build();

              channel.pipeline().addLast(sslContext.newHandler(channel.alloc()));
            }

            channel.pipeline()
                .addLast(new ChannelInboundHandlerAdapter() {
                  @Override
                  public void channelInactive(ChannelHandlerContext ctx) {
                    clientChannels.remove(ctx.channel());
                  }
                })
                .addLast(new ModbusTcpCodec())
                .addLast(new ModbusTcpFrameHandler());

            config.pipelineCustomizer().accept(channel.pipeline());
          }
        });

    bootstrap.group(config.eventLoopGroup());
    bootstrap.option(ChannelOption.SO_REUSEADDR, Boolean.TRUE);
    bootstrap.childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE);

    config.bootstrapCustomizer().accept(bootstrap);

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
  public CompletableFuture<Void> unbind() {
    ServerSocketChannel channel = serverChannel.getAndSet(null);

    if (channel != null) {
      var future = new CompletableFuture<Void>();
      channel.close().addListener((ChannelFutureListener) cf -> {
        clientChannels.forEach(Channel::close);
        clientChannels.clear();

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
        executionQueue.submit(() -> {
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
   * @param configure a {@link Consumer} that accepts a
   *     {@link NettyServerTransportConfig.Builder} instance to configure.
   * @return a new {@link NettyTcpServerTransport}.
   */
  public static NettyTcpServerTransport create(
      Consumer<NettyServerTransportConfig.Builder> configure
  ) {

    var builder = new NettyServerTransportConfig.Builder();
    configure.accept(builder);
    return new NettyTcpServerTransport(builder.build());
  }

}
