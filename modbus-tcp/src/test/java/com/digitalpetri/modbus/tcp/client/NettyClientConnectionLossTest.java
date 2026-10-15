package com.digitalpetri.modbus.tcp.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusClientConfig;
import com.digitalpetri.modbus.client.ModbusRtuClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.exceptions.ModbusConnectionLostException;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport.ConnectionListener;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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

  @Test
  void throwingTcpLossCallbackDoesNotPreventConnectionListenerNotification() throws Exception {
    assertThrowingCallbackDoesNotPreventListenerNotification(false, true);
  }

  @Test
  void throwingRtuLossCallbackDoesNotPreventConnectionListenerNotification() throws Exception {
    assertThrowingCallbackDoesNotPreventListenerNotification(true, true);
  }

  @Test
  void throwingTcpLossNotificationDoesNotPreventConnectionListenerNotification() throws Exception {
    assertThrowingCallbackDoesNotPreventListenerNotification(false, false);
  }

  @Test
  void throwingRtuLossNotificationDoesNotPreventConnectionListenerNotification() throws Exception {
    assertThrowingCallbackDoesNotPreventListenerNotification(true, false);
  }

  private void assertThrowingCallbackDoesNotPreventListenerNotification(
      boolean rtu, boolean duringCapture) throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ModbusClient client = createClient(server.getLocalPort(), rtu);
      var callbackInvoked = new CompletableFuture<Void>();
      var listenerNotified = new CompletableFuture<Void>();
      client
          .getTransport()
          .onConnectionLost(
              () -> {
                if (duringCapture) {
                  callbackInvoked.complete(null);
                  throw new IllegalStateException("capture failed");
                }
                return () -> {
                  callbackInvoked.complete(null);
                  throw new IllegalStateException("notification failed");
                };
              });
      var listener =
          new ConnectionListener() {
            @Override
            public void onConnection() {}

            @Override
            public void onConnectionLost() {
              listenerNotified.complete(null);
            }
          };
      if (client.getTransport() instanceof NettyTcpClientTransport tcp) {
        tcp.addConnectionListener(listener);
      } else {
        ((NettyRtuClientTransport) client.getTransport()).addConnectionListener(listener);
      }

      try (var ignored = client.open();
          Socket peer = server.accept()) {
        peer.close();
        callbackInvoked.get(2, TimeUnit.SECONDS);
        listenerNotified.get(2, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void slowConnectionListenerDoesNotFailReconnectedTcpRequest() throws Exception {
    assertSlowListenerDoesNotFailReconnectedRequest(false);
  }

  @Test
  void slowConnectionListenerDoesNotFailReconnectedRtuRequest() throws Exception {
    assertSlowListenerDoesNotFailReconnectedRequest(true);
  }

  private void assertSlowListenerDoesNotFailReconnectedRequest(boolean rtu) throws Exception {
    var listenerEntered = new CountDownLatch(1);
    var releaseListener = new CountDownLatch(1);
    var lossNotified = new CompletableFuture<Void>();
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      server.setSoTimeout(5000);
      ModbusClient client = createClient(server.getLocalPort(), rtu);
      var listener =
          new ConnectionListener() {
            @Override
            public void onConnection() {
              listenerEntered.countDown();
              try {
                if (!releaseListener.await(5, TimeUnit.SECONDS)) {
                  lossNotified.completeExceptionally(
                      new IllegalStateException("test did not release listener"));
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lossNotified.completeExceptionally(e);
              }
            }

            @Override
            public void onConnectionLost() {
              lossNotified.complete(null);
            }
          };
      if (client.getTransport() instanceof NettyTcpClientTransport tcp) {
        tcp.addConnectionListener(listener);
      } else {
        ((NettyRtuClientTransport) client.getTransport()).addConnectionListener(listener);
      }

      try (var ignored = client.open();
          Socket peer = server.accept()) {
        assertTrue(listenerEntered.await(2, TimeUnit.SECONDS));
        peer.close();
        try (Socket reconnected = server.accept()) {
          var response = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));
          receiveRequest(reconnected, rtu);
          releaseListener.countDown();
          lossNotified.get(2, TimeUnit.SECONDS);
          reconnected
              .getOutputStream()
              .write(HexFormat.of().parseHex(rtu ? "010302002a399b" : "000000000005010302002a"));
          assertArrayEquals(
              new byte[] {0, 42},
              response.toCompletableFuture().get(2, TimeUnit.SECONDS).registers());
        }
      } finally {
        releaseListener.countDown();
      }
    }
  }

  @Test
  void rtuResponseQueuedBeforeLossIsNotAppliedToNextRequest() throws Exception {
    var listenerEntered = new CountDownLatch(1);
    var releaseListener = new CountDownLatch(1);
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      server.setSoTimeout(5000);
      var client = (ModbusRtuClient) createClient(server.getLocalPort(), true);
      ((NettyRtuClientTransport) client.getTransport())
          .addConnectionListener(
              new ConnectionListener() {
                @Override
                public void onConnection() {
                  listenerEntered.countDown();
                  try {
                    if (!releaseListener.await(5, TimeUnit.SECONDS)) {
                      throw new IllegalStateException("test did not release listener");
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                  }
                }

                @Override
                public void onConnectionLost() {}
              });

      try (var ignored = client.open();
          Socket peer = server.accept()) {
        assertTrue(listenerEntered.await(2, TimeUnit.SECONDS));
        var first = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));
        receiveRequest(peer, true);
        peer.getOutputStream().write(HexFormat.of().parseHex("01030200017984"));
        peer.close();
        try (Socket reconnected = server.accept()) {
          var next = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(100, 1));
          releaseListener.countDown();
          assertArrayEquals(
              new byte[] {0, 1}, first.toCompletableFuture().get(2, TimeUnit.SECONDS).registers());
          receiveRequest(reconnected, true);
          reconnected.getOutputStream().write(HexFormat.of().parseHex("010302002a399b"));
          assertArrayEquals(
              new byte[] {0, 42}, next.toCompletableFuture().get(2, TimeUnit.SECONDS).registers());
        }
      } finally {
        releaseListener.countDown();
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
