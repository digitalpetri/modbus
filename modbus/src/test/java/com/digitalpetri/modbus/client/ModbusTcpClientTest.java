package com.digitalpetri.modbus.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class ModbusTcpClientTest {

  @Test
  void sendRawAcceptsVendorResponseFunctionCode() throws Exception {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    CompletionStage<byte[]> cs = client.sendRawAsync(1, new byte[] {0x5A, 0x01, 0x02});

    transport.frameReceiver.accept(
        new ModbusTcpFrame(
            new MbapHeader(0, 1, 3, 1), ByteBuffer.wrap(new byte[] {(byte) 0xDA, 0x04})));

    assertArrayEquals(new byte[] {(byte) 0xDA, 0x04}, cs.toCompletableFuture().get());
  }

  @Test
  void sendRawAcceptsUnexpectedFunctionCode() throws Exception {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    CompletionStage<byte[]> cs = client.sendRawAsync(1, new byte[] {0x5A, 0x01, 0x02});

    transport.frameReceiver.accept(
        new ModbusTcpFrame(
            new MbapHeader(0, 1, 4, 1), ByteBuffer.wrap(new byte[] {0x22, 0x10, 0x20})));

    assertArrayEquals(new byte[] {0x22, 0x10, 0x20}, cs.toCompletableFuture().get());
  }

  @Test
  void sendRawRejectsEmptyRequestPdu() {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    CompletionStage<byte[]> cs = client.sendRawAsync(1, new byte[0]);

    ExecutionException ex =
        assertThrows(ExecutionException.class, () -> cs.toCompletableFuture().get());

    ModbusException cause = assertInstanceOf(ModbusException.class, ex.getCause());
    assertEquals("empty request PDU", cause.getMessage());
    assertNull(transport.lastFrameSent);
  }

  /**
   * Tests handling of an erroneous empty response PDU.
   *
   * @see <a
   *     href="https://github.com/digitalpetri/modbus/issues/121">https://github.com/digitalpetri/modbus/issues/121</a>
   */
  @Test
  void sendRawStillRejectsEmptyResponsePdu() {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    CompletionStage<byte[]> cs = client.sendRawAsync(1, new byte[] {0x04, 0x03, 0x00, 0x00, 0x01});

    transport.frameReceiver.accept(
        new ModbusTcpFrame(new MbapHeader(0, 1, 1, 1), ByteBuffer.allocate(0)));

    ExecutionException ex =
        assertThrows(ExecutionException.class, () -> cs.toCompletableFuture().get());

    ModbusException cause = (ModbusException) ex.getCause();
    assertEquals("empty response PDU", cause.getMessage());
  }

  /**
   * Tests typed handling of a malformed exception response PDU containing only the function code |
   * 0x80 and missing the required exception code byte.
   */
  @Test
  void sendAsyncStillRejectsMalformedExceptionResponsePdu() {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    var cs = client.sendAsync(1, new ReadHoldingRegistersRequest(0, 1));

    transport.frameReceiver.accept(
        new ModbusTcpFrame(new MbapHeader(0, 1, 2, 1), ByteBuffer.wrap(new byte[] {(byte) 0x83})));

    ExecutionException ex =
        assertThrows(ExecutionException.class, () -> cs.toCompletableFuture().get());

    ModbusException cause = (ModbusException) ex.getCause();
    assertEquals("malformed exception response PDU: 83", cause.getMessage());
  }

  @Test
  void sendAsyncStillTranslatesStandardExceptionResponse() {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    var cs = client.sendAsync(1, new ReadHoldingRegistersRequest(0, 1));

    transport.frameReceiver.accept(
        new ModbusTcpFrame(
            new MbapHeader(0, 1, 3, 1), ByteBuffer.wrap(new byte[] {(byte) 0x83, 0x02})));

    ExecutionException ex =
        assertThrows(ExecutionException.class, () -> cs.toCompletableFuture().get());

    ModbusResponseException cause = assertInstanceOf(ModbusResponseException.class, ex.getCause());
    assertEquals(0x03, cause.getFunctionCode());
    assertEquals(0x02, cause.getExceptionCode());
  }

  @Test
  void sendAsyncStillRejectsUnexpectedFunctionCode() {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    var cs = client.sendAsync(1, new ReadHoldingRegistersRequest(0, 1));

    transport.frameReceiver.accept(
        new ModbusTcpFrame(new MbapHeader(0, 1, 3, 1), ByteBuffer.wrap(new byte[] {0x22, 0x01})));

    ExecutionException ex =
        assertThrows(ExecutionException.class, () -> cs.toCompletableFuture().get());

    ModbusException cause = assertInstanceOf(ModbusException.class, ex.getCause());
    assertEquals("unexpected function code: 0x22", cause.getMessage());
  }

  private static class TestTransport implements ModbusTcpClientTransport {

    boolean connected = false;
    ModbusTcpFrame lastFrameSent;
    Consumer<ModbusTcpFrame> frameReceiver;

    @Override
    public CompletionStage<Void> connect() {
      connected = true;
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disconnect() {
      connected = false;
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isConnected() {
      return connected;
    }

    @Override
    public CompletionStage<Void> send(ModbusTcpFrame frame) {
      lastFrameSent = frame;
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void receive(Consumer<ModbusTcpFrame> frameReceiver) {
      this.frameReceiver = frameReceiver;
    }
  }
}
