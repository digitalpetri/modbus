package com.digitalpetri.modbus.tcp.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.digitalpetri.modbus.server.ModbusServerTransport;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.SelectStrategy;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;

/** Socket release guarantees for both Netty server transports. */
@Timeout(20)
class NettyServerUnbindTest {

  @TestFactory
  List<DynamicTest> unbindWaitsForSocketRelease() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Protocol protocol : Protocol.values()) {
      for (int ioRatio : new int[] {50, 100}) {
        tests.add(
            dynamicTest(
                protocol + " ioRatio=" + ioRatio,
                () -> {
                  var handler = new BlockingCloseHandler();
                  try (var server = new TestServer(protocol, new NioEventLoopGroup(1), handler)) {
                    server.group.setIoRatio(ioRatio);
                    try (var gate = new EventLoopGate(server.channel.eventLoop())) {
                      CompletableFuture<Void> unbound =
                          server.transport.unbind().toCompletableFuture();
                      final CompletableFuture<Void> verified =
                          unbound.thenRun(() -> assertConnectionRefused(server.port()));
                      gate.close();
                      handler.closed.get(5, TimeUnit.SECONDS);

                      // A second caller must wait for the same listener to be released.
                      CompletableFuture<Void> secondUnbind =
                          server.transport.unbind().toCompletableFuture();
                      assertFalse(
                          unbound.isDone(),
                          "unbind completed before the selector could deregister the socket");
                      assertFalse(
                          secondUnbind.isDone(),
                          "overlapping unbind completed before socket release");
                      handler.release.countDown();

                      verified.get(5, TimeUnit.SECONDS);
                      secondUnbind.get(5, TimeUnit.SECONDS);
                      assertPortReusable(server.port());
                      server.transport.unbind().toCompletableFuture().get(5, TimeUnit.SECONDS);
                    } finally {
                      handler.release.countDown();
                    }
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> reentrantUnbindWaitsForSocketRelease() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Protocol protocol : Protocol.values()) {
      tests.add(
          dynamicTest(
              protocol.toString(),
              () -> {
                var transport = new AtomicReference<ModbusServerTransport<?, ?>>();
                var reentrant = new CompletableFuture<CompletableFuture<Void>>();
                BlockingCloseHandler handler =
                    new BlockingCloseHandler() {
                      @Override
                      public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
                        reentrant.complete(transport.get().unbind().toCompletableFuture());
                        super.close(ctx, promise);
                      }
                    };
                try (var server = new TestServer(protocol, new NioEventLoopGroup(1), handler)) {
                  transport.set(server.transport);
                  var unbound = new CompletableFuture<Void>();
                  server
                      .channel
                      .eventLoop()
                      .execute(
                          () ->
                              server
                                  .transport
                                  .unbind()
                                  .whenComplete(
                                      (ignored, error) -> {
                                        if (error == null) {
                                          unbound.complete(null);
                                        } else {
                                          unbound.completeExceptionally(error);
                                        }
                                      }));
                  try {
                    CompletableFuture<Void> nested = reentrant.get(5, TimeUnit.SECONDS);
                    handler.closed.get(5, TimeUnit.SECONDS);
                    assertFalse(
                        nested.isDone(), "reentrant unbind completed before socket release");
                    final CompletableFuture<Void> verified =
                        nested.thenRun(() -> assertConnectionRefused(server.port()));
                    handler.release.countDown();
                    verified.get(5, TimeUnit.SECONDS);
                    unbound.get(5, TimeUnit.SECONDS);
                  } finally {
                    handler.release.countDown();
                  }
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> callersCannotCompleteOtherUnbinds() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Protocol protocol : Protocol.values()) {
      tests.add(
          dynamicTest(
              protocol.toString(),
              () -> {
                var clientClosed = new CompletableFuture<Void>();
                var handler = new BlockingCloseHandler();
                try (var server =
                        new TestServer(
                            protocol,
                            new NioEventLoopGroup(1),
                            handler,
                            new ChannelInboundHandlerAdapter() {
                              @Override
                              public void channelInactive(ChannelHandlerContext ctx) {
                                clientClosed.complete(null);
                                ctx.fireChannelInactive();
                              }
                            });
                    var client = new Socket("127.0.0.1", server.port())) {
                  // Wait until the transport has accepted and recorded the connection.
                  server.clientRegistered.get(5, TimeUnit.SECONDS);
                  try (var gate = new EventLoopGate(server.channel.eventLoop())) {
                    CompletableFuture<Void> cancelled =
                        server.transport.unbind().toCompletableFuture();
                    gate.close();
                    handler.closed.get(5, TimeUnit.SECONDS);
                    assertTrue(cancelled.cancel(false));
                    CompletableFuture<Void> completed =
                        server.transport.unbind().toCompletableFuture();
                    assertFalse(completed.isDone(), "one caller cancelled the shared unbind");
                    assertTrue(completed.complete(null));
                    final CompletableFuture<Void> pending =
                        server.transport.unbind().toCompletableFuture();
                    assertFalse(pending.isDone(), "one caller completed the shared unbind");
                    handler.release.countDown();
                    pending.get(5, TimeUnit.SECONDS);
                    clientClosed.get(5, TimeUnit.SECONDS);
                    assertConnectionRefused(server.port());
                  } finally {
                    handler.release.countDown();
                  }
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> unbindWaitsWhenSelectionIsSkipped() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Protocol protocol : Protocol.values()) {
      for (int ioRatio : new int[] {50, 100}) {
        tests.add(
            dynamicTest(
                protocol + " ioRatio=" + ioRatio,
                () -> {
                  var skipSelections = new AtomicInteger();
                  var group =
                      new NioEventLoopGroup(
                          1,
                          (ThreadFactory) null,
                          SelectorProvider.provider(),
                          () ->
                              (select, hasTasks) -> {
                                if (skipSelections.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                                  return 0;
                                }
                                return hasTasks ? select.get() : SelectStrategy.SELECT;
                              });
                  try (var server =
                      new TestServer(protocol, group, new ChannelOutboundHandlerAdapter())) {
                    group.setIoRatio(ioRatio);
                    server.channel.closeFuture().addListener(f -> skipSelections.set(8));
                    try (var gate = new EventLoopGate(server.channel.eventLoop())) {
                      CompletableFuture<Void> verified =
                          server
                              .transport
                              .unbind()
                              .toCompletableFuture()
                              .thenRun(() -> assertConnectionRefused(server.port()));
                      gate.close();
                      verified.get(5, TimeUnit.SECONDS);
                      assertPortReusable(server.port());
                    }
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> unbindDuringEventLoopShutdown() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Protocol protocol : Protocol.values()) {
      tests.add(
          dynamicTest(
              protocol.toString(),
              () -> {
                try (var server =
                    new TestServer(
                        protocol, new NioEventLoopGroup(1), new ChannelOutboundHandlerAdapter())) {
                  var verified = new CompletableFuture<Void>();
                  // Shutdown closes the listener before draining the last tasks and closing the
                  // selector.
                  server
                      .channel
                      .closeFuture()
                      .addListener(
                          f ->
                              server
                                  .transport
                                  .unbind()
                                  .toCompletableFuture()
                                  .thenRun(() -> assertConnectionRefused(server.port()))
                                  .whenComplete(
                                      (ignored, error) -> {
                                        if (error == null) {
                                          verified.complete(null);
                                        } else {
                                          verified.completeExceptionally(error);
                                        }
                                      }));
                  server.group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
                  verified.get(5, TimeUnit.SECONDS);
                  assertPortReusable(server.port());
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> unbindAfterEventLoopTermination() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Protocol protocol : Protocol.values()) {
      tests.add(
          dynamicTest(
              protocol.toString(),
              () -> {
                try (var server =
                    new TestServer(
                        protocol, new NioEventLoopGroup(1), new ChannelOutboundHandlerAdapter())) {
                  server.group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
                  server.transport.unbind().toCompletableFuture().get(5, TimeUnit.SECONDS);
                  assertConnectionRefused(server.port());
                  assertPortReusable(server.port());
                }
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> unbindReportsCloseFailure() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Protocol protocol : Protocol.values()) {
      tests.add(
          dynamicTest(
              protocol.toString(),
              () -> {
                var failure = new IllegalStateException("close failed");
                ChannelOutboundHandlerAdapter handler =
                    new ChannelOutboundHandlerAdapter() {
                      @Override
                      public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
                        promise.setFailure(failure);
                      }
                    };
                try (var server = new TestServer(protocol, new NioEventLoopGroup(1), handler)) {
                  ExecutionException error =
                      assertThrows(
                          ExecutionException.class,
                          () ->
                              server
                                  .transport
                                  .unbind()
                                  .toCompletableFuture()
                                  .get(5, TimeUnit.SECONDS));
                  assertSame(failure, error.getCause());
                }
              }));
    }
    return tests;
  }

  private static void assertConnectionRefused(int port) {
    assertThrows(
        ConnectException.class,
        () -> {
          try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
          }
        });
  }

  private static void assertPortReusable(int port) throws Exception {
    try (var socket = new ServerSocket()) {
      socket.setReuseAddress(true);
      socket.bind(new InetSocketAddress("127.0.0.1", port));
    }
  }

  private enum Protocol {
    TCP,
    RTU
  }

  private static class TestServer implements AutoCloseable {
    final NioEventLoopGroup group;
    final ModbusServerTransport<?, ?> transport;
    final Channel channel;
    final CompletableFuture<Void> clientRegistered = new CompletableFuture<>();
    private final int port;

    TestServer(Protocol protocol, NioEventLoopGroup group, ChannelHandler handler)
        throws Exception {
      this(protocol, group, handler, new ChannelInboundHandlerAdapter());
    }

    TestServer(
        Protocol protocol,
        NioEventLoopGroup group,
        ChannelHandler handler,
        ChannelHandler clientHandler)
        throws Exception {
      this.group = group;
      var registered = new CompletableFuture<Channel>();
      NettyServerTransportConfig config =
          NettyServerTransportConfig.create(
              b ->
                  b.setBindAddress("127.0.0.1")
                      .setPort(0)
                      .setEventLoopGroup(group)
                      .setPipelineCustomizer(
                          pipeline -> {
                            pipeline.addLast(clientHandler);
                            clientRegistered.complete(null);
                          })
                      .setBootstrapCustomizer(
                          bootstrap ->
                              bootstrap.handler(
                                  new ChannelInitializer<Channel>() {
                                    @Override
                                    protected void initChannel(Channel ch) {
                                      ch.pipeline().addLast(handler);
                                      registered.complete(ch);
                                    }
                                  })));
      transport =
          protocol == Protocol.TCP
              ? new NettyTcpServerTransport(config)
              : new NettyRtuServerTransport(config);
      transport.bind().toCompletableFuture().get(5, TimeUnit.SECONDS);
      channel = registered.get(5, TimeUnit.SECONDS);
      port = ((InetSocketAddress) channel.localAddress()).getPort();
    }

    int port() {
      return port;
    }

    @Override
    public void close() throws Exception {
      group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
    }
  }

  private static class EventLoopGate implements AutoCloseable {
    private final CountDownLatch release = new CountDownLatch(1);

    EventLoopGate(EventLoop loop) throws Exception {
      var entered = new CompletableFuture<Void>();
      loop.execute(
          () -> {
            entered.complete(null);
            await(release);
          });
      entered.get(5, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
      release.countDown();
    }
  }

  private static class BlockingCloseHandler extends ChannelOutboundHandlerAdapter {
    final CompletableFuture<Void> closed = new CompletableFuture<>();
    final CountDownLatch release = new CountDownLatch(1);

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
      ctx.close(promise);
      closed.complete(null);
      await(release);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(5, TimeUnit.SECONDS), "event loop gate timed out");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
