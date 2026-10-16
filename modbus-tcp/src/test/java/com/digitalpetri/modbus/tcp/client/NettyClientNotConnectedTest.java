package com.digitalpetri.modbus.tcp.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusClientConfig;
import com.digitalpetri.modbus.client.ModbusRtuClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusNotConnectedException;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.netty.fsm.ChannelFsm;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests that a send on a Netty transport that isn't connected fails with a typed exception. */
@Timeout(20)
class NettyClientNotConnectedTest {

  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);

  @AfterEach
  void tearDown() throws Exception {
    executor.shutdownNow();
    eventLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
  }

  @Test
  void sendBeforeConnectFailsTcpRequest() throws Exception {
    assertSendBeforeConnectFails(false);
  }

  @Test
  void sendBeforeConnectFailsRtuRequest() throws Exception {
    assertSendBeforeConnectFails(true);
  }

  private void assertSendBeforeConnectFails(boolean rtu) throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ModbusClient client = createClient(server.getLocalPort(), rtu, executor);

      var response = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));

      assertNotConnected(response.toCompletableFuture());
    }
  }

  @Test
  void synchronousTcpCallWrapsNotConnected() throws Exception {
    assertSynchronousCallWrapsNotConnected(false);
  }

  @Test
  void synchronousRtuCallWrapsNotConnected() throws Exception {
    assertSynchronousCallWrapsNotConnected(true);
  }

  private void assertSynchronousCallWrapsNotConnected(boolean rtu) throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ModbusClient client = createClient(server.getLocalPort(), rtu, executor);

      ModbusExecutionException failure =
          assertThrows(
              ModbusExecutionException.class,
              () -> client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 1)));

      assertInstanceOf(ModbusNotConnectedException.class, failure.getCause());
    }
  }

  @Test
  void sendAfterDisconnectFailsTcpRequest() throws Exception {
    assertSendAfterDisconnectFails(false);
  }

  @Test
  void sendAfterDisconnectFailsRtuRequest() throws Exception {
    assertSendAfterDisconnectFails(true);
  }

  private void assertSendAfterDisconnectFails(boolean rtu) throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ModbusClient client = createClient(server.getLocalPort(), rtu, executor);
      client.connect();
      try (Socket ignored = server.accept()) {
        client.disconnect();

        var response = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));

        assertNotConnected(response.toCompletableFuture());
      }
    }
  }

  @Test
  void writeOnClosedChannelFailsTcpRequest() throws Exception {
    assertWriteOnClosedChannelFails(false);
  }

  @Test
  void writeOnClosedChannelFailsRtuRequest() throws Exception {
    assertWriteOnClosedChannelFails(true);
  }

  /**
   * The channel FSM processes the channel's closure on the transport executor. Occupying that
   * executor's only thread keeps the FSM in its connected state after the peer closes the
   * connection, so the send's write lands on a closed channel.
   */
  private void assertWriteOnClosedChannelFails(boolean rtu) throws Exception {
    ExecutorService fsmExecutor = Executors.newSingleThreadExecutor();
    var gate = new CountDownLatch(1);
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ModbusClient client = createClient(server.getLocalPort(), rtu, fsmExecutor);
      client.connect();
      try (Socket peer = server.accept()) {
        Channel channel = channelFsm(client).getChannel().get(2, TimeUnit.SECONDS);

        fsmExecutor.execute(
            () -> {
              try {
                gate.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

        peer.close();
        assertTrue(channel.closeFuture().await(2, TimeUnit.SECONDS));
        assertTrue(client.isConnected(), "FSM should still report connected");

        var response = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));

        ModbusNotConnectedException failure = assertNotConnected(response.toCompletableFuture());
        assertInstanceOf(ClosedChannelException.class, failure.getCause());
      } finally {
        // The FSM needs the executor to process the disconnect.
        gate.countDown();
        client.disconnect();
      }
    } finally {
      fsmExecutor.shutdownNow();
    }
  }

  private static ChannelFsm channelFsm(ModbusClient client) {
    if (client.getTransport() instanceof NettyTcpClientTransport tcp) {
      return tcp.getChannelFsm();
    } else {
      return ((NettyRtuClientTransport) client.getTransport()).getChannelFsm();
    }
  }

  private ModbusClient createClient(int port, boolean rtu, ExecutorService executor) {
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

  private static ModbusNotConnectedException assertNotConnected(CompletableFuture<?> response) {
    ExecutionException failure =
        assertThrows(ExecutionException.class, () -> response.get(2, TimeUnit.SECONDS));
    return assertInstanceOf(ModbusNotConnectedException.class, failure.getCause());
  }
}
