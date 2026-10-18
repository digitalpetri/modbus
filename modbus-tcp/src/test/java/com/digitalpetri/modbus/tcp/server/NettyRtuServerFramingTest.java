package com.digitalpetri.modbus.tcp.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.server.ModbusRtuServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Partial request frame recovery and connection isolation for the RTU over TCP server. */
@Timeout(20)
class NettyRtuServerFramingTest {

  private static final Duration TIMEOUT = Duration.ofMillis(100);

  /** Read holding register 0 of unit 1, and its response. */
  private static final byte[] READ_0 = hex("010300000001840a");

  private static final byte[] READ_0_RESPONSE = hex("0103020000b844");

  /** Read holding register 1 of unit 1, and its response. */
  private static final byte[] READ_1 = hex("010300010001d5ca");

  private static final byte[] READ_1_RESPONSE = hex("0103021234b533");

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);
  private final BlockingQueue<Integer> boundPorts = new LinkedBlockingQueue<>();
  private final Semaphore closedChannels = new Semaphore(0);
  private final FakeNanoTime nanoTime = new FakeNanoTime();
  private final List<Socket> sockets = new ArrayList<>();
  private ModbusRtuServer server;
  private int port;

  @AfterEach
  void tearDown() throws Exception {
    try {
      for (Socket socket : sockets) {
        socket.close();
      }
      if (server != null) {
        server.stop();
      }
    } finally {
      executor.shutdownNow();
      eventLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
    }
  }

  @Test
  void requestSplitByLessSilenceThanTimeoutIsAnswered() throws Exception {
    startServer(new NettyRtuServerTransport(config(), nanoTime));
    Socket socket = connect();

    send(socket, Arrays.copyOfRange(READ_0, 0, 4));
    nanoTime.advance(TIMEOUT.minusNanos(1));
    send(socket, Arrays.copyOfRange(READ_0, 4, 8));

    assertResponse(socket, READ_0_RESPONSE);
  }

  @Test
  void partialRequestIsDiscardedAfterTimeoutAndNextRequestIsAnswered() throws Exception {
    startServer(new NettyRtuServerTransport(config(), nanoTime));
    Socket socket = connect();

    send(socket, Arrays.copyOfRange(READ_0, 0, 4));
    nanoTime.advance(TIMEOUT);
    send(socket, READ_1);

    assertResponse(socket, READ_1_RESPONSE);
    assertSilent(socket);
  }

  @Test
  void silenceIsMeasuredFromLastReceivedBytes() throws Exception {
    startServer(new NettyRtuServerTransport(config(), nanoTime));
    Socket socket = connect();

    send(socket, Arrays.copyOfRange(READ_1, 0, 3));
    nanoTime.advance(Duration.ofMillis(60));
    send(socket, Arrays.copyOfRange(READ_1, 3, 6));
    nanoTime.advance(Duration.ofMillis(60));
    send(socket, Arrays.copyOfRange(READ_1, 6, 8));

    assertResponse(socket, READ_1_RESPONSE);
  }

  @Test
  void partialRequestDoesNotAffectNextConnection() throws Exception {
    startServer(new NettyRtuServerTransport(config(), nanoTime));
    Socket a = connect();
    send(a, Arrays.copyOfRange(READ_0, 0, 4));
    a.close();
    awaitChannelClosed();

    Socket b = connect();
    send(b, READ_1);
    assertResponse(b, READ_1_RESPONSE);
  }

  @Test
  void rejectedConnectionDoesNotAffectActiveConnection() throws Exception {
    startServer(new NettyRtuServerTransport(config(), nanoTime));
    Socket a = connect();
    send(a, Arrays.copyOfRange(READ_0, 0, 4));

    // The server accepts one client at a time and closes the second connection.
    Socket b = connect();
    b.getOutputStream().write(READ_1);
    assertClosedByServer(b);

    send(a, Arrays.copyOfRange(READ_0, 4, 8));
    assertResponse(a, READ_0_RESPONSE);
    assertSilent(a);
  }

  @Test
  void restartedServerDoesNotKeepPartialRequest() throws Exception {
    startServer(new NettyRtuServerTransport(config(), nanoTime));
    Socket a = connect();
    send(a, Arrays.copyOfRange(READ_0, 0, 4));

    server.stop();
    awaitChannelClosed();
    server.start();
    port = nextBoundPort();

    Socket b = connect();
    send(b, READ_1);
    assertResponse(b, READ_1_RESPONSE);
  }

  @Test
  void defaultTimeoutRecoversWithSystemTime() throws Exception {
    startServer(new NettyRtuServerTransport(config()));
    Socket socket = connect();

    socket.getOutputStream().write(READ_0, 0, 4);
    Thread.sleep(20);
    socket.getOutputStream().write(READ_0, 4, 4);
    assertResponse(socket, READ_0_RESPONSE);

    socket.getOutputStream().write(READ_0, 0, 4);
    Thread.sleep(300);
    socket.getOutputStream().write(READ_1);
    assertResponse(socket, READ_1_RESPONSE);
    assertSilent(socket);
  }

  private NettyServerTransportConfig config() {
    return NettyServerTransportConfig.create(
        b ->
            b.setBindAddress("127.0.0.1")
                .setPort(0)
                .setExecutor(executor)
                .setEventLoopGroup(eventLoop)
                .setPipelineCustomizer(
                    pipeline ->
                        pipeline.addLast(
                            new ChannelInboundHandlerAdapter() {
                              @Override
                              public void channelInactive(ChannelHandlerContext ctx) {
                                closedChannels.release();
                                ctx.fireChannelInactive();
                              }
                            }))
                .setBootstrapCustomizer(
                    bootstrap ->
                        bootstrap.handler(
                            new ChannelInboundHandlerAdapter() {
                              @Override
                              public void channelActive(ChannelHandlerContext ctx) {
                                boundPorts.add(
                                    ((InetSocketAddress) ctx.channel().localAddress()).getPort());
                                ctx.fireChannelActive();
                              }
                            })));
  }

  private void startServer(NettyRtuServerTransport transport) throws Exception {
    var image = new ProcessImage();
    image.with(tx -> tx.writeHoldingRegisters(m -> m.put(1, new byte[] {0x12, 0x34})));
    var services =
        new ReadWriteModbusServices() {
          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            return Optional.of(image);
          }
        };
    server = ModbusRtuServer.create(transport, services);
    server.start();
    port = nextBoundPort();
  }

  private int nextBoundPort() throws InterruptedException {
    Integer boundPort = boundPorts.poll(5, TimeUnit.SECONDS);
    assertNotNull(boundPort, "server did not bind");
    return boundPort;
  }

  private Socket connect() throws Exception {
    var socket = new Socket("127.0.0.1", port);
    socket.setSoTimeout(2000);
    sockets.add(socket);
    return socket;
  }

  /** Wait until the server has released the client channel it accepted. */
  private void awaitChannelClosed() throws InterruptedException {
    if (!closedChannels.tryAcquire(5, TimeUnit.SECONDS)) {
      throw new AssertionError("server did not close the client channel");
    }
  }

  /** Write {@code data} and wait until the server receives it. */
  private void send(Socket socket, byte[] data) throws Exception {
    socket.getOutputStream().write(data);
    nanoTime.awaitRead();
  }

  private static void assertResponse(Socket socket, byte[] expected) throws Exception {
    assertArrayEquals(expected, socket.getInputStream().readNBytes(expected.length));
  }

  private static void assertClosedByServer(Socket socket) throws Exception {
    try {
      assertEquals(-1, socket.getInputStream().read());
    } catch (SocketException e) {
      // The server reset the connection because it closed with unread data.
    }
  }

  private static void assertSilent(Socket socket) throws Exception {
    socket.setSoTimeout(150);
    try {
      assertThrows(SocketTimeoutException.class, () -> socket.getInputStream().read());
    } finally {
      socket.setSoTimeout(2000);
    }
  }

  private static byte[] hex(String value) {
    return HexFormat.of().parseHex(value);
  }
}
