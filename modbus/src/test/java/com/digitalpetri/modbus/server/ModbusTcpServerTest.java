package com.digitalpetri.modbus.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpRequestContext;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ModbusTcpServerTest {

  @Test
  void rawServicesHandleVendorFunctionBeforeTypedDecode() throws Exception {
    var receivedContext = new AtomicReference<ModbusTcpRequestContext>();
    var receivedRequest = new AtomicReference<RawModbusTcpRequest>();
    var context = new TestContext();
    RawModbusTcpServices services =
        (requestContext, request) -> {
          receivedContext.set(requestContext);
          receivedRequest.set(request);
          return Optional.of(RawModbusTcpResponse.response(0x5A, new byte[] {0x10, 0x20}));
        };
    var server = new TestServer(services);

    ModbusTcpFrame response =
        server.handle(requestFrame(7, 3, new byte[] {0x5A, 0x01, 0x02}), context);

    assertSame(context, receivedContext.get());
    RawModbusTcpRequest request = receivedRequest.get();
    assertEquals(3, request.unitId());
    assertEquals(0x5A, request.functionCode());
    assertArrayEquals(new byte[] {0x01, 0x02}, request.payload());

    assertEquals(7, response.header().transactionId());
    assertEquals(0, response.header().protocolId());
    assertEquals(4, response.header().length());
    assertEquals(3, response.header().unitId());
    assertArrayEquals(new byte[] {0x5A, 0x10, 0x20}, bytes(response.pdu()));
  }

  @Test
  void rawServicesCanReturnExceptionShapedPduWithoutServerInterpretation() throws Exception {
    RawModbusTcpServices services =
        (context, request) -> Optional.of(new RawModbusTcpResponse(new byte[] {(byte) 0xDA, 0x04}));
    var server = new TestServer(services);

    ModbusTcpFrame response =
        server.handle(requestFrame(7, 3, new byte[] {0x5A, 0x01, 0x02}), new TestContext());

    assertEquals(7, response.header().transactionId());
    assertEquals(0, response.header().protocolId());
    assertEquals(3, response.header().length());
    assertEquals(3, response.header().unitId());
    assertArrayEquals(new byte[] {(byte) 0xDA, 0x04}, bytes(response.pdu()));
  }

  @Test
  void rawServicesCanDeclineTypedRequest() throws Exception {
    var rawCalled = new AtomicBoolean(false);
    RawModbusTcpServices services =
        new RawModbusTcpServices() {

          @Override
          public Optional<RawModbusTcpResponse> handleRawTcpRequest(
              ModbusTcpRequestContext context, RawModbusTcpRequest request) {

            rawCalled.set(true);
            return Optional.empty();
          }

          @Override
          public ReadHoldingRegistersResponse readHoldingRegisters(
              ModbusRequestContext context, int unitId, ReadHoldingRegistersRequest request) {

            assertEquals(3, unitId);
            assertEquals(0x0010, request.address());
            assertEquals(2, request.quantity());
            return new ReadHoldingRegistersResponse(new byte[] {0x11, 0x22, 0x33, 0x44});
          }
        };
    var server = new TestServer(services);

    ModbusTcpFrame response = server.handle(readHoldingRegistersFrame(7, 3), new TestContext());

    assertTrue(rawCalled.get());
    assertTypedReadHoldingRegistersResponse(response);
  }

  @Test
  void nonRawServicesKeepExistingTypedBehavior() throws Exception {
    ModbusServices services =
        new ModbusServices() {

          @Override
          public ReadHoldingRegistersResponse readHoldingRegisters(
              ModbusRequestContext context, int unitId, ReadHoldingRegistersRequest request) {

            assertEquals(3, unitId);
            assertEquals(0x0010, request.address());
            assertEquals(2, request.quantity());
            return new ReadHoldingRegistersResponse(new byte[] {0x11, 0x22, 0x33, 0x44});
          }
        };
    var server = new TestServer(services);

    ModbusTcpFrame response = server.handle(readHoldingRegistersFrame(7, 3), new TestContext());

    assertTypedReadHoldingRegistersResponse(response);
  }

  private static ModbusTcpFrame requestFrame(int transactionId, int unitId, byte[] pdu) {
    return new ModbusTcpFrame(
        new MbapHeader(transactionId, 0, pdu.length + 1, unitId), ByteBuffer.wrap(pdu));
  }

  private static ModbusTcpFrame readHoldingRegistersFrame(int transactionId, int unitId) {
    return requestFrame(transactionId, unitId, new byte[] {0x03, 0x00, 0x10, 0x00, 0x02});
  }

  private static void assertTypedReadHoldingRegistersResponse(ModbusTcpFrame response) {
    assertEquals(7, response.header().transactionId());
    assertEquals(0, response.header().protocolId());
    assertEquals(7, response.header().length());
    assertEquals(3, response.header().unitId());
    assertArrayEquals(new byte[] {0x03, 0x04, 0x11, 0x22, 0x33, 0x44}, bytes(response.pdu()));
  }

  private static byte[] bytes(ByteBuffer buffer) {
    ByteBuffer copy = buffer.slice();
    byte[] bytes = new byte[copy.remaining()];
    copy.get(bytes);
    return bytes;
  }

  private static class TestServer extends ModbusTcpServer {

    TestServer(ModbusServices services) {
      super(ModbusServerConfig.create(b -> {}), new TestTransport(), services);
    }

    ModbusTcpFrame handle(ModbusTcpFrame frame, ModbusTcpRequestContext context) throws Exception {
      return handleModbusTcpFrame(frame, context);
    }
  }

  private static class TestTransport implements ModbusTcpServerTransport {

    FrameReceiver<ModbusTcpRequestContext, ModbusTcpFrame> frameReceiver;

    @Override
    public CompletionStage<Void> bind() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> unbind() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void receive(FrameReceiver<ModbusTcpRequestContext, ModbusTcpFrame> frameReceiver) {
      this.frameReceiver = frameReceiver;
    }
  }

  private static class TestContext implements ModbusTcpRequestContext {

    @Override
    public SocketAddress localAddress() {
      return null;
    }

    @Override
    public SocketAddress remoteAddress() {
      return null;
    }
  }
}
