package com.digitalpetri.modbus.tcp.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetSocketAddress;
import java.net.Socket;
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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Partial request frame recovery and connection isolation for the Modbus TCP server. */
@Timeout(20)
class NettyTcpServerFramingTest {

  private static final Duration TIMEOUT = Duration.ofMillis(100);

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);
  private final BlockingQueue<Integer> boundPorts = new LinkedBlockingQueue<>();
  private final FakeNanoTime nanoTime = new FakeNanoTime();
  private final List<Socket> sockets = new ArrayList<>();
  private ModbusTcpServer server;
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
    startServer(new NettyTcpServerTransport(config(), nanoTime));
    Socket socket = connect();
    byte[] request = request(0x2A);

    send(socket, Arrays.copyOfRange(request, 0, 6));
    nanoTime.advance(TIMEOUT.minusNanos(1));
    send(socket, Arrays.copyOfRange(request, 6, request.length));

    assertResponse(socket, 0x2A);
  }

  @Test
  void partialRequestIsDiscardedAfterTimeoutAndNextRequestIsAnswered() throws Exception {
    startServer(new NettyTcpServerTransport(config(), nanoTime));
    Socket socket = connect();

    send(socket, Arrays.copyOfRange(request(0x2A), 0, 6));
    nanoTime.advance(TIMEOUT);
    send(socket, request(0x2B));

    assertResponse(socket, 0x2B);
    assertSilent(socket);
  }

  @Test
  void silenceIsMeasuredFromLastReceivedBytes() throws Exception {
    startServer(new NettyTcpServerTransport(config(), nanoTime));
    Socket socket = connect();
    byte[] request = request(0x2A);

    send(socket, Arrays.copyOfRange(request, 0, 4));
    nanoTime.advance(Duration.ofMillis(60));
    send(socket, Arrays.copyOfRange(request, 4, 8));
    nanoTime.advance(Duration.ofMillis(60));
    send(socket, Arrays.copyOfRange(request, 8, request.length));

    assertResponse(socket, 0x2A);
  }

  @Test
  void coalescedRequestsAreAnswered() throws Exception {
    startServer(new NettyTcpServerTransport(config(), nanoTime));
    Socket socket = connect();

    send(socket, concat(request(1), request(2)));
    assertResponse(socket, 1);
    assertResponse(socket, 2);

    send(socket, Arrays.copyOfRange(request(3), 0, 6));
    nanoTime.advance(TIMEOUT);
    send(socket, concat(request(4), request(5)));
    assertResponse(socket, 4);
    assertResponse(socket, 5);
    assertSilent(socket);
  }

  @Test
  void connectionsFrameRequestsIndependently() throws Exception {
    startServer(new NettyTcpServerTransport(config(), nanoTime));
    Socket a = connect();
    Socket b = connect();

    // Interleaved halves of two requests.
    send(a, Arrays.copyOfRange(request(1), 0, 6));
    send(b, Arrays.copyOfRange(request(2), 0, 6));
    send(a, Arrays.copyOfRange(request(1), 6, 12));
    send(b, Arrays.copyOfRange(request(2), 6, 12));
    assertResponse(a, 1);
    assertResponse(b, 2);

    // Bytes received on b don't restart a's silence.
    send(a, Arrays.copyOfRange(request(3), 0, 6));
    nanoTime.advance(Duration.ofMillis(60));
    send(b, request(4));
    assertResponse(b, 4);
    nanoTime.advance(Duration.ofMillis(40));
    send(a, request(5));
    assertResponse(a, 5);
    assertSilent(a);
  }

  @Test
  void partialRequestDoesNotAffectNextConnection() throws Exception {
    startServer(new NettyTcpServerTransport(config(), nanoTime));
    Socket a = connect();
    send(a, Arrays.copyOfRange(request(1), 0, 6));
    a.close();

    Socket b = connect();
    send(b, request(2));
    assertResponse(b, 2);
  }

  @Test
  void restartedServerDoesNotKeepPartialRequest() throws Exception {
    startServer(new NettyTcpServerTransport(config(), nanoTime));
    Socket a = connect();
    send(a, Arrays.copyOfRange(request(1), 0, 6));

    server.stop();
    server.start();
    port = nextBoundPort();

    Socket b = connect();
    send(b, request(2));
    assertResponse(b, 2);
  }

  @Test
  void recoversWithSystemTime() throws Exception {
    startServer(new NettyTcpServerTransport(config()));
    Socket socket = connect();
    byte[] request = request(0x2A);

    socket.getOutputStream().write(request, 0, 6);
    Thread.sleep(20);
    socket.getOutputStream().write(request, 6, 6);
    assertResponse(socket, 0x2A);

    socket.getOutputStream().write(request(0x2B), 0, 6);
    Thread.sleep(300);
    socket.getOutputStream().write(request(0x2C));
    assertResponse(socket, 0x2C);
    assertSilent(socket);
  }

  private NettyServerTransportConfig config() {
    return NettyServerTransportConfig.create(
        b ->
            b.setBindAddress("127.0.0.1")
                .setPort(0)
                .setPartialFrameTimeout(TIMEOUT)
                .setExecutor(executor)
                .setEventLoopGroup(eventLoop)
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

  private void startServer(NettyTcpServerTransport transport) throws Exception {
    var image = new ProcessImage();
    var services =
        new ReadWriteModbusServices() {
          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            return Optional.of(image);
          }
        };
    server = ModbusTcpServer.create(transport, services);
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
    // Send each write immediately; with Nagle, a write can wait for the server's delayed ACK.
    socket.setTcpNoDelay(true);
    socket.setSoTimeout(2000);
    sockets.add(socket);
    return socket;
  }

  /** Write {@code data} and wait until the server receives it. */
  private void send(Socket socket, byte[] data) throws Exception {
    socket.getOutputStream().write(data);
    nanoTime.awaitRead();
  }

  /** A request to read holding register 0 of unit 1. */
  private static byte[] request(int transactionId) {
    return hex("%04x00000006010300000001".formatted(transactionId));
  }

  private static void assertResponse(Socket socket, int transactionId) throws Exception {
    byte[] expected = hex("%04x000000050103020000".formatted(transactionId));

    assertArrayEquals(expected, socket.getInputStream().readNBytes(expected.length));
  }

  private static void assertSilent(Socket socket) throws Exception {
    socket.setSoTimeout(150);
    try {
      assertThrows(SocketTimeoutException.class, () -> socket.getInputStream().read());
    } finally {
      socket.setSoTimeout(2000);
    }
  }

  private static byte[] concat(byte[] first, byte[] second) {
    byte[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static byte[] hex(String value) {
    return HexFormat.of().parseHex(value);
  }
}
