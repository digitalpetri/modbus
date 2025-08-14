package com.digitalpetri.modbus.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.exceptions.ModbusException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class ModbusTcpClientTest {

  /**
   * Tests handling of an erroneous empty response PDU.
   *
   * @see <a
   *     href="https://github.com/digitalpetri/modbus/issues/121">https://github.com/digitalpetri/modbus/issues/121</a>
   */
  @Test
  void emptyResponsePdu() {
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
   * Tests handling of a malformed exception response PDU containing only the function code | 0x80
   * and missing the required exception code byte.
   */
  @Test
  void malformedExceptionResponsePdu() {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    // Send a request with function code 0x04
    CompletionStage<byte[]> cs = client.sendRawAsync(1, new byte[] {0x04, 0x03, 0x00, 0x00, 0x01});

    // Receive a malformed exception response: only 1 byte (0x84), no exception code
    transport.frameReceiver.accept(
        new ModbusTcpFrame(
            new MbapHeader(0, 1, 1, 1), ByteBuffer.wrap(new byte[] {(byte) 0x84})));

    ExecutionException ex =
        assertThrows(ExecutionException.class, () -> cs.toCompletableFuture().get());

    ModbusException cause = (ModbusException) ex.getCause();
    assertEquals("malformed exception response PDU: 84", cause.getMessage());
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
