package com.digitalpetri.modbus.tcp.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterResponse;
import com.digitalpetri.modbus.server.ModbusRequestContext;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusRtuRequestContext;
import com.digitalpetri.modbus.server.ModbusRtuServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class NettyRtuServerBroadcastTest {

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);
  private final CompletableFuture<Integer> port = new CompletableFuture<>();
  private final BlockingQueue<Integer> processed = new LinkedBlockingQueue<>();
  private TestTransport transport;
  private Socket socket;

  @BeforeEach
  void setUp() {
    transport =
        new TestTransport(
            NettyServerTransportConfig.create(
                b -> {
                  b.setBindAddress("127.0.0.1")
                      .setPort(0)
                      .setExecutor(executor)
                      .setEventLoopGroup(eventLoop);
                  b.setBootstrapCustomizer(
                      bootstrap ->
                          bootstrap.handler(
                              new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                  port.complete(
                                      ((InetSocketAddress) ctx.channel().localAddress()).getPort());
                                  ctx.fireChannelActive();
                                }
                              }));
                }));
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
      if (socket != null) {
        socket.close();
      }
      if (transport != null) {
        transport.unbind().toCompletableFuture().get(5, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
      eventLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
    }
  }

  @Test
  void serverAppliesBroadcastWritesAndRemainsSilentUntilUnicast() throws Exception {
    var image = new ProcessImage();
    var services =
        new ReadWriteModbusServices() {
          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            return Optional.of(image);
          }

          @Override
          public WriteSingleRegisterResponse writeSingleRegister(
              ModbusRequestContext context, int unitId, WriteSingleRegisterRequest request)
              throws ModbusResponseException, UnknownUnitIdException {
            if (request.address() == 11) {
              throw new UnknownUnitIdException(unitId);
            }
            return super.writeSingleRegister(context, unitId, request);
          }
        };
    ModbusRtuServer.create(transport, services).start();
    connect();

    send("0006000a1234a56e");
    assertSilent();
    assertArrayEquals(
        new byte[] {0x12, 0x34}, image.get(tx -> tx.readHoldingRegisters(m -> m.get(10))));

    send("0006000b5678c65b");
    assertSilent();
    // FC1 through FC4 and FC23 must not dispatch a read or apply FC23's write.
    for (String frame :
        new String[] {
          "0001000a0001dc19",
          "0002000a00019819",
          "0003000a0001a5d9",
          "0004000a00011019",
          "0017000a000100140001021234786c"
        }) {
      send(frame);
      assertSilent();
    }
    assertEquals(1, image.<Integer>get(tx -> tx.readHoldingRegisters(Map::size)).intValue());

    send("0103000a0001a408");
    assertArrayEquals(hex("0103021234b533"), socket.getInputStream().readNBytes(7));
    assertSilent();
  }

  @Test
  void transportSuppressesReturnedBroadcastFramesAndNullResponses() throws Exception {
    transport.receive(
        (context, frame) -> {
          if (frame.unitId() == 1) {
            return null;
          }
          if (frame.pdu().get(2) == 11) {
            return new ModbusRtuFrame(
                0, ByteBuffer.wrap(new byte[] {(byte) 0x86, 2}), ByteBuffer.allocate(2));
          }
          return frame;
        });
    transport.bind().toCompletableFuture().get(5, TimeUnit.SECONDS);
    connect();

    for (String frame : new String[] {"0006000a1234a56e", "0006000b5678c65b", "0106000a1234a4bf"}) {
      send(frame);
      assertSilent();
    }
    send("0206000a1234a48c");
    assertArrayEquals(hex("0206000a1234a48c"), socket.getInputStream().readNBytes(8));
    assertSilent();
  }

  private void connect() throws Exception {
    socket = new Socket("127.0.0.1", port.get(5, TimeUnit.SECONDS));
    socket.setSoTimeout(2000);
  }

  private void send(String frame) throws Exception {
    socket.getOutputStream().write(hex(frame));
    socket.getOutputStream().flush();
    assertNotNull(processed.poll(5, TimeUnit.SECONDS), "request was not processed");
  }

  private void assertSilent() throws Exception {
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

  private class TestTransport extends NettyRtuServerTransport {

    TestTransport(NettyServerTransportConfig config) {
      super(config);
    }

    @Override
    public void receive(FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame> receiver) {
      super.receive(
          (context, frame) -> {
            try {
              return receiver.receive(context, frame);
            } finally {
              processed.add(frame.unitId());
            }
          });
    }
  }
}
