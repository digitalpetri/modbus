package com.digitalpetri.modbus.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.ModbusPduSerializer;
import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.TimeoutScheduler;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.pdu.ModbusPdu;
import com.digitalpetri.modbus.pdu.ModbusRequestPdu;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class RequestValidationClientTest {

  private static final RuntimeException SEND_FAILURE = new RuntimeException("test send failure");

  @Test
  void invalidRequestsCreateNoSendOrTimeout() throws Exception {
    for (Path path : Path.values()) {
      var fixture = new Fixture(path, null);
      for (ModbusRequestPdu request :
          List.of(
              new ReadHoldingRegistersRequest(-1, 1),
              new ReadHoldingRegistersRequest(65536, 1),
              new ReadHoldingRegistersRequest(65535, 2),
              new ReadHoldingRegistersRequest(0, 0),
              new WriteMultipleRegistersRequest(0, 2, new byte[2]))) {
        assertInstanceOf(IllegalArgumentException.class, failure(fixture.send(request)));
      }
      assertEquals(0, fixture.sends.get());
      assertEquals(0, fixture.timeouts.get());
      if (fixture.client instanceof ModbusRtuClient client) {
        assertEquals(0, client.timeouts.size());
      }
    }
  }

  @Test
  void configuredSerializerCannotBypassBuiltInValidation() throws Exception {
    for (Path path : Path.values()) {
      var encodings = new AtomicInteger();
      var fixture = new Fixture(path, new TestSerializer(encodings));
      assertInstanceOf(
          IllegalArgumentException.class,
          failure(fixture.send(new ReadHoldingRegistersRequest(65536, 1))));
      assertEquals(0, encodings.get());
      assertEquals(0, fixture.sends.get());
      assertEquals(0, fixture.timeouts.get());
    }
  }

  @Test
  void mutablePayloadIsValidatedOnEverySend() throws Exception {
    for (Path path : Path.values()) {
      var fixture = new Fixture(path, null);
      byte[] values = {1};
      var request = new WriteMultipleCoilsRequest(65535, 1, values);
      assertSame(SEND_FAILURE, failure(fixture.send(request)));
      assertEquals(1, fixture.sends.get());
      assertEquals(1, fixture.timeouts.get());

      values[0] = (byte) 0x81;
      assertInstanceOf(IllegalArgumentException.class, failure(fixture.send(request)));
      assertEquals(1, fixture.sends.get());
      assertEquals(1, fixture.timeouts.get());
    }
  }

  @Test
  void customRequestMayUseStandardFunctionCodeWithItsOwnLayout() throws Exception {
    for (Path path : Path.values()) {
      var encodings = new AtomicInteger();
      var fixture = new Fixture(path, new TestSerializer(encodings));
      assertSame(SEND_FAILURE, failure(fixture.send(new CustomRequest(65536, 0))));
      assertEquals(1, encodings.get());
      assertEquals(1, fixture.sends.get());
      assertEquals(1, fixture.timeouts.get());
      assertArrayEquals(new byte[] {3, 0, 1, 0, 0, 0, 0, 0, 0}, fixture.lastPdu);
    }
  }

  @Test
  void customRequestValidationIsRepeated() throws Exception {
    for (Path path : Path.values()) {
      var encodings = new AtomicInteger();
      var fixture = new Fixture(path, new TestSerializer(encodings));
      var validations = new AtomicInteger();
      ModbusRequestPdu request =
          new ModbusRequestPdu() {
            @Override
            public int getFunctionCode() {
              return 3;
            }

            @Override
            public void validate() {
              if (validations.incrementAndGet() > 1) {
                throw new IllegalArgumentException("custom validation failed");
              }
            }
          };
      assertSame(SEND_FAILURE, failure(fixture.send(request)));
      assertInstanceOf(IllegalArgumentException.class, failure(fixture.send(request)));
      assertEquals(2, validations.get());
      assertEquals(1, encodings.get());
      assertEquals(1, fixture.sends.get());
      assertEquals(1, fixture.timeouts.get());
    }
  }

  @Test
  void blockingAndConvenienceCallsReportValidationFailure() {
    for (Path path : Path.values()) {
      var fixture = new Fixture(path, null);
      ModbusExecutionException e =
          assertThrows(
              ModbusExecutionException.class,
              () -> {
                if (path == Path.BROADCAST) {
                  ((ModbusRtuClient) fixture.client)
                      .broadcast(new WriteMultipleRegistersRequest(0, 0, new byte[0]));
                } else {
                  fixture.client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(65536, 1));
                }
              });
      assertInstanceOf(IllegalArgumentException.class, e.getCause());
      assertEquals(0, fixture.sends.get());
      assertEquals(0, fixture.timeouts.get());
    }
  }

  @Test
  void rawPduSendRetainsPreEncodedBytes() throws Exception {
    var fixture = new Fixture(Path.TCP, null);
    byte[] pdu = {3, 0, 0, 0, 0};
    assertSame(SEND_FAILURE, failure(((ModbusTcpClient) fixture.client).sendRawAsync(1, pdu)));
    assertArrayEquals(pdu, fixture.lastPdu);
    assertEquals(1, fixture.sends.get());
  }

  private static Throwable failure(CompletionStage<?> stage) {
    return assertThrows(
            ExecutionException.class, () -> stage.toCompletableFuture().get(1, TimeUnit.SECONDS))
        .getCause();
  }

  private enum Path {
    TCP,
    RTU,
    BROADCAST
  }

  private record CustomRequest(int address, int quantity) implements ModbusRequestPdu {
    @Override
    public int getFunctionCode() {
      return 3;
    }
  }

  private record TestSerializer(AtomicInteger encodings) implements ModbusPduSerializer {
    @Override
    public void encode(ModbusPdu pdu, ByteBuffer buffer) {
      encodings.incrementAndGet();
      buffer.put((byte) pdu.getFunctionCode());
      if (pdu instanceof CustomRequest request) {
        buffer.putInt(request.address());
        buffer.putInt(request.quantity());
      }
    }

    @Override
    public ModbusPdu decode(int functionCode, ByteBuffer buffer) {
      throw new UnsupportedOperationException();
    }
  }

  private static class Fixture {
    final Path path;
    final AtomicInteger sends = new AtomicInteger();
    final AtomicInteger timeouts = new AtomicInteger();
    final ModbusClient client;
    byte[] lastPdu;

    Fixture(Path path, ModbusPduSerializer serializer) {
      this.path = path;
      ModbusClientConfig config =
          ModbusClientConfig.create(
              cfg -> {
                if (serializer != null) {
                  cfg.requestSerializer = serializer;
                }
                cfg.timeoutScheduler =
                    (task, delay, unit) -> {
                      timeouts.incrementAndGet();
                      return new TimeoutScheduler.TimeoutHandle() {
                        final AtomicBoolean cancelled = new AtomicBoolean();

                        @Override
                        public void cancel() {
                          cancelled.set(true);
                        }

                        @Override
                        public boolean isCancelled() {
                          return cancelled.get();
                        }
                      };
                    };
              });
      client =
          path == Path.TCP
              ? new ModbusTcpClient(config, new TcpTransport(this))
              : new ModbusRtuClient(config, new RtuTransport(this));
    }

    CompletionStage<?> send(ModbusRequestPdu request) {
      return path == Path.BROADCAST
          ? ((ModbusRtuClient) client).broadcastAsync(request)
          : client.sendAsync(1, request);
    }

    CompletionStage<Void> recordSend(ByteBuffer pdu) {
      lastPdu = new byte[pdu.remaining()];
      pdu.duplicate().get(lastPdu);
      sends.incrementAndGet();
      // Terminate valid requests without involving response decoding or a real timeout.
      return CompletableFuture.failedFuture(SEND_FAILURE);
    }
  }

  private abstract static class TestTransport<T> implements ModbusClientTransport<T> {
    final Fixture fixture;

    TestTransport(Fixture fixture) {
      this.fixture = fixture;
    }

    @Override
    public CompletionStage<Void> connect() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disconnect() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isConnected() {
      return true;
    }

    @Override
    public void receive(Consumer<T> receiver) {}
  }

  private static class TcpTransport extends TestTransport<ModbusTcpFrame>
      implements ModbusTcpClientTransport {
    TcpTransport(Fixture fixture) {
      super(fixture);
    }

    @Override
    public CompletionStage<Void> send(ModbusTcpFrame frame) {
      return fixture.recordSend(frame.pdu());
    }
  }

  private static class RtuTransport extends TestTransport<ModbusRtuFrame>
      implements ModbusRtuClientTransport {
    RtuTransport(Fixture fixture) {
      super(fixture);
    }

    @Override
    public CompletionStage<Void> send(ModbusRtuFrame frame) {
      return fixture.recordSend(frame.pdu());
    }

    @Override
    public void resetFrameParser() {}
  }
}
