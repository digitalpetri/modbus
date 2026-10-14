package com.digitalpetri.modbus.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.Crc16;
import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusPduSerializer;
import com.digitalpetri.modbus.ModbusPduSerializer.DefaultResponseSerializer;
import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.pdu.ModbusPdu;
import com.digitalpetri.modbus.pdu.ModbusRequestPdu;
import com.digitalpetri.modbus.pdu.ModbusResponsePdu;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersResponse;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ModbusResponseValidationTest {

  private static final byte[] VALID_READ = {3, 4, 0, 1, 0, 2};

  @Test
  void syncAndAsyncReadsRejectMalformedResponsesAndRecoverOverTcpAndRtu() throws Exception {
    byte[][] invalidResponses = {
      {3, 2, 0, 1}, // well-formed, but one register instead of two
      {3, 6, 0, 1, 0, 2, 0, 3}, // well-formed, but three registers instead of two
      {3, 4, 0, 1, 0, 2, 0}, // trailing byte
      {3, 4, 0, 1}, // truncated data
      {3, 3, 0, 1, 0} // odd register byte count
    };
    for (boolean rtu : new boolean[] {false, true}) {
      for (boolean async : new boolean[] {false, true}) {
        for (int i = 0; i < invalidResponses.length; i++) {
          ModbusClient client = createClient(rtu, cfg -> {}, invalidResponses[i], VALID_READ);
          try (var ignored = client.open()) {
            Throwable failure = readFailure(client, async);
            if (i < 2) {
              assertInstanceOf(ModbusException.class, failure);
            } else {
              assertInstanceOf(IllegalArgumentException.class, failure);
            }
            assertTrue(failure.getMessage().contains(i < 2 ? "byte count" : "response"));
            assertArrayEquals(new byte[] {0, 1, 0, 2}, read(client, async).registers());
            assertNoRtuTimeouts(client);
          }
        }
      }
    }
  }

  @Test
  void syncAndAsyncWritesRejectWrongEchoesAndRecoverOverTcpAndRtu() throws Exception {
    var request = new WriteMultipleRegistersRequest(10, 2, new byte[] {0, 1, 0, 2});
    for (boolean rtu : new boolean[] {false, true}) {
      for (boolean async : new boolean[] {false, true}) {
        for (byte[] invalid : new byte[][] {{16, 0, 11, 0, 2}, {16, 0, 10, 0, 1}}) {
          ModbusClient client = createClient(rtu, cfg -> {}, invalid, new byte[] {16, 0, 10, 0, 2});
          try (var ignored = client.open()) {
            Throwable failure;
            if (async) {
              var e =
                  assertThrows(
                      ExecutionException.class,
                      () ->
                          client
                              .writeMultipleRegistersAsync(1, request)
                              .toCompletableFuture()
                              .get(1, TimeUnit.SECONDS));
              failure = e.getCause();
            } else {
              failure =
                  assertThrows(
                          ModbusExecutionException.class,
                          () -> client.writeMultipleRegisters(1, request))
                      .getCause();
            }
            assertInstanceOf(ModbusException.class, failure);
            WriteMultipleRegistersResponse response =
                async
                    ? client
                        .writeMultipleRegistersAsync(1, request)
                        .toCompletableFuture()
                        .get(1, TimeUnit.SECONDS)
                    : client.writeMultipleRegisters(1, request);
            assertEquals(new WriteMultipleRegistersResponse(10, 2), response);
            assertNoRtuTimeouts(client);
          }
        }
      }
    }
  }

  @Test
  void customValidatorReplacesStandardRulesIndependentlyOfCodec() throws Exception {
    var request = new ReadHoldingRegistersRequest(0, 2);
    for (boolean rtu : new boolean[] {false, true}) {
      var calls = new AtomicInteger();
      ModbusClient client =
          createClient(
              rtu,
              cfg ->
                  cfg.setResponseValidator(
                      (q, r) -> {
                        assertSame(request, q);
                        calls.incrementAndGet();
                      }),
              new byte[] {3, 2, 0, 1});
      try (var ignored = client.open()) {
        assertEquals(2, client.readHoldingRegisters(1, request).registers().length);
        assertEquals(1, calls.get());
      }
    }
  }

  @Test
  void customCodecAndValidatorSupportFourByteRegistersAndOwnConsumption() throws Exception {
    byte[] wideResponse = {3, 8, 0, 0, 0, 1, 0, 0, 0, 2, 0x7F};
    for (boolean rtu : new boolean[] {false, true}) {
      // Replacing the codec alone retains standard semantic validation.
      ModbusClient standard =
          createClient(rtu, cfg -> cfg.responseSerializer = new WideRegisterCodec(), wideResponse);
      try (var ignored = standard.open()) {
        assertInstanceOf(ModbusException.class, readFailure(standard, true));
      }
      for (boolean async : new boolean[] {false, true}) {
        var codec = new WideRegisterCodec();
        var calls = new AtomicInteger();
        ModbusClient custom =
            createClient(
                rtu,
                cfg -> {
                  cfg.responseSerializer = codec;
                  cfg.setResponseValidator(
                      (q, r) -> {
                        var request = (ReadHoldingRegistersRequest) q;
                        var response = (ReadHoldingRegistersResponse) r;
                        if (response.registers().length != 4 * request.quantity()) {
                          throw new ModbusException("unexpected vendor register byte count");
                        }
                        calls.incrementAndGet();
                      });
                },
                wideResponse);
        try (var ignored = custom.open()) {
          assertEquals(8, read(custom, async).registers().length);
          assertEquals(1, calls.get());
          // The vendor codec deliberately leaves an extension byte unread.
          assertEquals(1, codec.remaining);
        }
      }
    }
  }

  @Test
  void customPduTypesUsingAStandardFunctionCodeBypassStandardSemantics() throws Exception {
    var codec = new VendorCodec();
    for (boolean rtu : new boolean[] {false, true}) {
      ModbusClient client =
          createClient(
              rtu,
              cfg -> {
                cfg.requestSerializer = codec;
                cfg.responseSerializer = codec;
              },
              new byte[] {3, 0, 0, 0, 42});
      try (var ignored = client.open()) {
        assertEquals(new VendorResponse(42), client.send(1, new VendorRequest()));
      }
    }
  }

  @Test
  void customValidatorDoesNotBypassFunctionCodeOrExceptionHandling() throws Exception {
    for (boolean rtu : new boolean[] {false, true}) {
      var calls = new AtomicInteger();
      ModbusClient client =
          createClient(
              rtu,
              cfg -> cfg.responseValidator = (q, r) -> calls.incrementAndGet(),
              new byte[] {0x22, 0},
              new byte[] {(byte) 0x83, 2},
              VALID_READ);
      try (var ignored = client.open()) {
        assertInstanceOf(ModbusException.class, readFailure(client, true));
        ModbusResponseException exception =
            assertInstanceOf(ModbusResponseException.class, readFailure(client, true));
        assertEquals(2, exception.getExceptionCode());
        assertEquals(0, calls.get());
        read(client, true);
        assertEquals(1, calls.get());
      }
    }
  }

  @Test
  void rawTcpBypassesBothCodecAndValidator() throws Exception {
    byte[] rawResponse = {3, 1, 0, 0x7F};
    ModbusTcpClient client =
        (ModbusTcpClient)
            createClient(
                false,
                cfg -> {
                  cfg.responseSerializer =
                      new DefaultResponseSerializer() {
                        @Override
                        public ModbusPdu decode(int functionCode, ByteBuffer buffer) {
                          throw new AssertionError("raw response must not be decoded");
                        }
                      };
                  cfg.responseValidator =
                      (q, r) -> {
                        throw new AssertionError("raw response must not be validated");
                      };
                },
                rawResponse);
    try (var ignored = client.open()) {
      assertArrayEquals(rawResponse, client.sendRaw(1, new byte[] {3, 0}));
    }
  }

  private static ReadHoldingRegistersResponse read(ModbusClient client, boolean async)
      throws Exception {
    var request = new ReadHoldingRegistersRequest(0, 2);
    return async
        ? client
            .readHoldingRegistersAsync(1, request)
            .toCompletableFuture()
            .get(1, TimeUnit.SECONDS)
        : client.readHoldingRegisters(1, request);
  }

  private static Throwable readFailure(ModbusClient client, boolean async) {
    if (async) {
      return assertThrows(
              ExecutionException.class,
              () ->
                  client
                      .readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 2))
                      .toCompletableFuture()
                      .get(1, TimeUnit.SECONDS))
          .getCause();
    }
    return assertThrows(
            ModbusExecutionException.class,
            () -> client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 2)))
        .getCause();
  }

  private static void assertNoRtuTimeouts(ModbusClient client) {
    if (client instanceof ModbusRtuClient rtu) {
      assertEquals(0, rtu.timeouts.size());
    }
  }

  private static ModbusClient createClient(
      boolean rtu, Consumer<ModbusClientConfig.Builder> configure, byte[]... responses) {
    return rtu
        ? ModbusRtuClient.create(new RtuResponder(responses), configure)
        : ModbusTcpClient.create(new TcpResponder(responses), configure);
  }

  private static class WideRegisterCodec extends DefaultResponseSerializer {
    volatile int remaining;

    @Override
    public ModbusPdu decode(int functionCode, ByteBuffer buffer) {
      assertEquals(3, functionCode);
      assertEquals(3, buffer.get());
      int count = buffer.get() & 0xFF;
      byte[] registers = new byte[count];
      buffer.get(registers);
      remaining = buffer.remaining();
      return new ReadHoldingRegistersResponse(registers);
    }
  }

  private record VendorRequest() implements ModbusRequestPdu {
    @Override
    public int getFunctionCode() {
      return 3;
    }
  }

  private record VendorResponse(int value) implements ModbusResponsePdu {
    @Override
    public int getFunctionCode() {
      return 3;
    }
  }

  private static class VendorCodec implements ModbusPduSerializer {
    @Override
    public void encode(ModbusPdu pdu, ByteBuffer buffer) {
      assertInstanceOf(VendorRequest.class, pdu);
      buffer.put((byte) 3).put((byte) 0x42);
    }

    @Override
    public ModbusPdu decode(int functionCode, ByteBuffer buffer) {
      assertEquals(3, functionCode);
      assertEquals(3, buffer.get());
      return new VendorResponse(buffer.getInt());
    }
  }

  private abstract static class Responder<T> implements ModbusClientTransport<T> {
    final Queue<byte[]> responses;
    Consumer<T> receiver;
    boolean connected;

    Responder(byte[][] responses) {
      this.responses = new ConcurrentLinkedQueue<>(Arrays.asList(responses));
    }

    @Override
    public CompletionStage<Void> connect() {
      connected = true;
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disconnect() {
      connected = false;
      assertTrue(responses.isEmpty(), "expected every scripted response to be used");
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isConnected() {
      return connected;
    }

    @Override
    public void receive(Consumer<T> receiver) {
      this.receiver = receiver;
    }
  }

  private static class TcpResponder extends Responder<ModbusTcpFrame>
      implements ModbusTcpClientTransport {
    TcpResponder(byte[][] responses) {
      super(responses);
    }

    @Override
    public CompletionStage<Void> send(ModbusTcpFrame request) {
      byte[] pdu = responses.remove();
      MbapHeader header = request.header();
      receiver.accept(
          new ModbusTcpFrame(
              new MbapHeader(header.transactionId(), 0, 1 + pdu.length, header.unitId()),
              ByteBuffer.wrap(pdu)));
      return CompletableFuture.completedFuture(null);
    }
  }

  private static class RtuResponder extends Responder<ModbusRtuFrame>
      implements ModbusRtuClientTransport {
    RtuResponder(byte[][] responses) {
      super(responses);
    }

    @Override
    public CompletionStage<Void> send(ModbusRtuFrame request) {
      ByteBuffer pdu = ByteBuffer.wrap(responses.remove());
      var crc = new Crc16();
      crc.update(request.unitId());
      crc.update(pdu);
      ByteBuffer crcBytes =
          ByteBuffer.wrap(new byte[] {(byte) crc.getValue(), (byte) (crc.getValue() >> 8)});
      receiver.accept(new ModbusRtuFrame(request.unitId(), pdu, crcBytes));
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void resetFrameParser() {
      throw new AssertionError("complete frames with valid CRC do not require a parser reset");
    }
  }
}
