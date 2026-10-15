package com.digitalpetri.modbus.tcp.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusClientConfig;
import com.digitalpetri.modbus.client.ModbusRtuClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.exceptions.ModbusConnectionLostException;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class NettyClientConnectionLossTest {

  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);

  @AfterEach
  void tearDown() throws Exception {
    executor.shutdownNow();
    eventLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
  }

  @Test
  void droppedConnectionFailsTcpRequestBeforeItsTimeout() throws Exception {
    assertDroppedConnectionFailsRequest(false);
  }

  @Test
  void droppedConnectionFailsRtuRequestBeforeItsTimeout() throws Exception {
    assertDroppedConnectionFailsRequest(true);
  }

  private void assertDroppedConnectionFailsRequest(boolean rtu) throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ModbusClient client = createClient(server.getLocalPort(), rtu);
      try (var ignored = client.open();
          Socket peer = server.accept()) {
        var response = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));
        receiveRequest(peer, rtu);
        peer.close();

        ExecutionException failure =
            assertThrows(
                ExecutionException.class,
                () -> response.toCompletableFuture().get(2, TimeUnit.SECONDS));
        assertConnectionLost(failure.getCause());
      }
    }
  }

  @Test
  void synchronousTcpCallWrapsConnectionLoss() throws Exception {
    assertSynchronousCallWrapsConnectionLoss(false);
  }

  @Test
  void synchronousRtuCallWrapsConnectionLoss() throws Exception {
    assertSynchronousCallWrapsConnectionLoss(true);
  }

  private void assertSynchronousCallWrapsConnectionLoss(boolean rtu) throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ModbusClient client = createClient(server.getLocalPort(), rtu);
      try (var ignored = client.open();
          Socket peer = server.accept()) {
        CompletableFuture<ModbusExecutionException> failure =
            CompletableFuture.supplyAsync(
                () ->
                    assertThrows(
                        ModbusExecutionException.class,
                        () ->
                            client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 1))),
                executor);
        receiveRequest(peer, rtu);
        peer.close();

        assertConnectionLost(failure.get(2, TimeUnit.SECONDS).getCause());
      }
    }
  }

  @Test
  void explicitDisconnectFailsRawTcpRequest() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      var client = (ModbusTcpClient) createClient(server.getLocalPort(), false);
      try (var ignored = client.open();
          Socket peer = server.accept()) {
        var response = client.sendRawAsync(1, new byte[] {3, 0, 0, 0, 1});
        receiveRequest(peer, false);
        client.disconnect();

        ExecutionException failure =
            assertThrows(
                ExecutionException.class,
                () -> response.toCompletableFuture().get(2, TimeUnit.SECONDS));
        assertConnectionLost(failure.getCause());
      }
    }
  }

  @Test
  void queuedRtuRequestCompletesAfterReconnectWithPartialOldResponse() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      server.setSoTimeout(5000);
      ModbusClient client = createClient(server.getLocalPort(), true);
      try (var ignored = client.open();
          Socket peer = server.accept()) {
        final var first =
            client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));
        receiveRequest(peer, true);
        var next = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(100, 1));
        peer.getOutputStream().write(new byte[] {1, 3, 2, 0});
        peer.close();

        ExecutionException failure =
            assertThrows(
                ExecutionException.class,
                () -> first.toCompletableFuture().get(2, TimeUnit.SECONDS));
        assertConnectionLost(failure.getCause());
        try (Socket reconnected = server.accept()) {
          receiveRequest(reconnected, true);
          reconnected.getOutputStream().write(HexFormat.of().parseHex("010302002a399b"));
          assertArrayEquals(
              new byte[] {0, 42}, next.toCompletableFuture().get(2, TimeUnit.SECONDS).registers());
        }
      }
    }
  }

  private ModbusClient createClient(int port, boolean rtu) {
    var transportConfig =
        NettyClientTransportConfig.create(
            b ->
                b.setHostname(InetAddress.getLoopbackAddress().getHostAddress())
                    .setPort(port)
                    .setExecutor(executor)
                    .setEventLoopGroup(eventLoop));
    var clientConfig = ModbusClientConfig.create(b -> b.setRequestTimeout(Duration.ofSeconds(30)));
    return rtu
        ? new ModbusRtuClient(clientConfig, new NettyRtuClientTransport(transportConfig))
        : new ModbusTcpClient(clientConfig, new NettyTcpClientTransport(transportConfig));
  }

  private static void receiveRequest(Socket peer, boolean rtu) throws Exception {
    peer.setSoTimeout(2000);
    int length = rtu ? 8 : 12;
    assertEquals(length, peer.getInputStream().readNBytes(length).length);
  }

  private static void assertConnectionLost(Throwable failure) {
    assertInstanceOf(ModbusConnectionLostException.class, failure);
  }
}
