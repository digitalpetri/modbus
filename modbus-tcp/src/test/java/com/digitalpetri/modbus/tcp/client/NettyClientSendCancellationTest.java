package com.digitalpetri.modbus.tcp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.client.ModbusClientTransport;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests that a send cancelled while a Netty transport waits for a channel is not written once the
 * channel arrives.
 *
 * <p>{@code ModbusRtuClient} cancels the send of a request that times out. A request that timed out
 * while the transport was connecting or reconnecting must not be written later: its response would
 * be taken as the response to a later request.
 */
@Timeout(20)
class NettyClientSendCancellationTest {

  private static final byte[] PDU = {3, 0, 0, 0, 1};

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);

  @AfterEach
  void tearDown() throws Exception {
    executor.shutdownNow();
    eventLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
  }

  @Test
  void cancelledTcpSendWaitingForChannelIsNotWritten() throws Exception {
    // Unit ID + PDU, after the 7-byte MBAP header.
    assertCancelledSendIsNotWritten(
        NettyTcpClientTransport::new, NettyClientSendCancellationTest::tcpFrame, 7 + PDU.length, 6);
  }

  @Test
  void cancelledRtuSendWaitingForChannelIsNotWritten() throws Exception {
    // Unit ID + PDU + CRC.
    assertCancelledSendIsNotWritten(
        NettyRtuClientTransport::new,
        NettyClientSendCancellationTest::rtuFrame,
        1 + PDU.length + 2,
        0);
  }

  /**
   * The channel FSM runs on the transport executor. Occupying that executor's only thread holds the
   * connect, so a send submitted meanwhile waits for the channel. The send is cancelled before the
   * executor is released.
   *
   * @param frameLength the encoded length of a frame.
   * @param unitIdOffset the offset of the unit ID in an encoded frame.
   */
  private <T> void assertCancelledSendIsNotWritten(
      Function<NettyClientTransportConfig, ModbusClientTransport<T>> newTransport,
      Function<Integer, T> newFrame,
      int frameLength,
      int unitIdOffset)
      throws Exception {

    var gate = new CountDownLatch(1);
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ModbusClientTransport<T> transport =
          newTransport.apply(
              NettyClientTransportConfig.create(
                  b ->
                      b.setHostname(InetAddress.getLoopbackAddress().getHostAddress())
                          .setPort(server.getLocalPort())
                          .setExecutor(executor)
                          .setEventLoopGroup(eventLoop)));

      executor.execute(
          () -> {
            try {
              gate.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });

      CompletableFuture<Void> connected = transport.connect().toCompletableFuture();
      CompletableFuture<Void> cancelled = transport.send(newFrame.apply(1)).toCompletableFuture();
      assertTrue(cancelled.cancel(false));

      gate.countDown();
      connected.get(5, TimeUnit.SECONDS);
      try (Socket peer = server.accept()) {
        // Channel delivery to the waiting send is dispatched on the executor. Once this marker
        // runs, that delivery, and any write it made, has been handed to the event loop.
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);

        transport.send(newFrame.apply(2)).toCompletableFuture().get(5, TimeUnit.SECONDS);

        peer.setSoTimeout(5000);
        byte[] received = peer.getInputStream().readNBytes(frameLength);
        assertEquals(frameLength, received.length);
        assertEquals(2, received[unitIdOffset], "the cancelled frame was written first");
      } finally {
        transport.disconnect().toCompletableFuture().get(5, TimeUnit.SECONDS);
      }
    }
  }

  private static ModbusTcpFrame tcpFrame(int unitId) {
    var header = new MbapHeader(0, 0, 1 + PDU.length, unitId);
    return new ModbusTcpFrame(header, ByteBuffer.wrap(PDU));
  }

  private static ModbusRtuFrame rtuFrame(int unitId) {
    return new ModbusRtuFrame(unitId, ByteBuffer.wrap(PDU), ByteBuffer.allocate(2));
  }
}
