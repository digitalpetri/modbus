package com.digitalpetri.modbus.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.TimeoutScheduler;
import com.digitalpetri.modbus.exceptions.ModbusConnectionLostException;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.ModbusTimeoutException;
import com.digitalpetri.modbus.exceptions.QueuedRequestTimeoutException;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class ModbusTcpClientTest {

  @Test
  void connectionLossFailsAllTypedAndRawRequestsAndCancelsTimeouts() throws Exception {
    var transport = new TestTransport();
    var scheduler = new RecordingTimeoutScheduler();
    var client = ModbusTcpClient.create(transport, cfg -> cfg.timeoutScheduler = scheduler);
    var typed = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));
    var raw = client.sendRawAsync(1, new byte[] {3, 0, 0, 0, 1});

    Runnable lossNotification = transport.connectionLost.get();
    var next = client.sendRawAsync(1, new byte[] {3, 0, 0, 0, 1});
    lossNotification.run();

    for (CompletionStage<?> response : List.of(typed, raw)) {
      ExecutionException failure =
          assertThrows(
              ExecutionException.class,
              () -> response.toCompletableFuture().get(1, TimeUnit.SECONDS));
      assertInstanceOf(ModbusConnectionLostException.class, failure.getCause());
    }
    assertEquals(3, scheduler.handles.size());
    assertTrue(scheduler.handles.get(0).isCancelled());
    assertTrue(scheduler.handles.get(1).isCancelled());
    assertFalse(scheduler.handles.get(2).isCancelled());
    assertFalse(next.toCompletableFuture().isDone());
    transport.frameReceiver.accept(
        new ModbusTcpFrame(
            transport.lastFrameSent.header(), ByteBuffer.wrap(new byte[] {3, 2, 0, 42})));
    assertArrayEquals(
        new byte[] {3, 2, 0, 42}, next.toCompletableFuture().get(1, TimeUnit.SECONDS));
  }

  @Test
  void completedResponseIsPreservedWhenConnectionIsLost() throws Exception {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);
    var response = client.sendRawAsync(1, new byte[] {3, 0, 0, 0, 1});
    transport.frameReceiver.accept(
        new ModbusTcpFrame(
            transport.lastFrameSent.header(), ByteBuffer.wrap(new byte[] {3, 2, 0, 42})));

    transport.connectionLost.get().run();
    assertArrayEquals(
        new byte[] {3, 2, 0, 42}, response.toCompletableFuture().get(1, TimeUnit.SECONDS));
  }

  @Test
  void lateSendFailureAndTimeoutAfterConnectionLossDoNotRemoveReusedTransaction() throws Exception {
    var send = new CompletableFuture<Void>();
    var transport =
        new TestTransport() {
          @Override
          public CompletionStage<Void> send(ModbusTcpFrame frame) {
            boolean first = lastFrameSent == null;
            super.send(frame);
            return first ? send : CompletableFuture.completedFuture(null);
          }
        };
    var scheduler = new RecordingTimeoutScheduler();
    var config = ModbusClientConfig.create(cfg -> cfg.timeoutScheduler = scheduler);
    var client =
        new ModbusTcpClient(config, transport) {
          @Override
          protected TransactionSequence createTransactionSequence() {
            return () -> 0;
          }
        };
    var first = client.sendRawAsync(1, new byte[] {3, 0, 0, 0, 1});
    transport.connectionLost.get().run();
    ExecutionException failure =
        assertThrows(
            ExecutionException.class, () -> first.toCompletableFuture().get(1, TimeUnit.SECONDS));
    assertInstanceOf(ModbusConnectionLostException.class, failure.getCause());

    var next = client.sendRawAsync(1, new byte[] {3, 0, 0, 0, 1});
    send.completeExceptionally(new ModbusException("late send failure"));
    // A timeout task may already be queued when its handle is cancelled.
    scheduler.tasks.get(0).run(scheduler.handles.get(0));
    assertFalse(next.toCompletableFuture().isDone());
    transport.frameReceiver.accept(
        new ModbusTcpFrame(
            transport.lastFrameSent.header(), ByteBuffer.wrap(new byte[] {3, 2, 0, 42})));
    assertArrayEquals(
        new byte[] {3, 2, 0, 42}, next.toCompletableFuture().get(1, TimeUnit.SECONDS));
  }

  @Test
  void registerReadRejectsResponseWithFewerRegistersThanRequested() {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    var response = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 2));

    transport.frameReceiver.accept(
        new ModbusTcpFrame(
            transport.lastFrameSent.header(),
            ByteBuffer.wrap(new byte[] {0x03, 0x02, 0x12, 0x34})));

    ExecutionException failure =
        assertThrows(
            ExecutionException.class,
            () -> response.toCompletableFuture().get(1, TimeUnit.SECONDS));
    ModbusException cause = assertInstanceOf(ModbusException.class, failure.getCause());
    assertTrue(cause.getMessage().contains("register byte count"));
  }

  @Test
  void writeRejectsResponseWithDifferentAddressAndQuantity() {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    var response =
        client.writeMultipleRegistersAsync(
            1, new WriteMultipleRegistersRequest(10, 2, new byte[] {0, 1, 0, 2}));

    transport.frameReceiver.accept(
        new ModbusTcpFrame(
            transport.lastFrameSent.header(), ByteBuffer.wrap(new byte[] {0x10, 0, 11, 0, 1})));

    ExecutionException failure =
        assertThrows(
            ExecutionException.class,
            () -> response.toCompletableFuture().get(1, TimeUnit.SECONDS));
    ModbusException cause = assertInstanceOf(ModbusException.class, failure.getCause());
    assertTrue(cause.getMessage().contains("address"));
  }

  @Test
  void concurrentReadsValidateAgainstTheirOwnRequestWhenResponsesArriveOutOfOrder()
      throws Exception {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);
    var first = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 2));
    MbapHeader firstHeader = transport.lastFrameSent.header();
    var second = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(100, 1));
    MbapHeader secondHeader = transport.lastFrameSent.header();

    byte[] oneRegister = {3, 2, 0x12, 0x34};
    transport.frameReceiver.accept(new ModbusTcpFrame(secondHeader, ByteBuffer.wrap(oneRegister)));
    transport.frameReceiver.accept(new ModbusTcpFrame(firstHeader, ByteBuffer.wrap(oneRegister)));

    assertEquals(2, second.toCompletableFuture().get(1, TimeUnit.SECONDS).registers().length);
    ExecutionException failure =
        assertThrows(
            ExecutionException.class, () -> first.toCompletableFuture().get(1, TimeUnit.SECONDS));
    assertInstanceOf(ModbusException.class, failure.getCause());
  }

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
  void sendRawAcceptsEmptyRequestPdu() throws Exception {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    CompletionStage<byte[]> cs = client.sendRawAsync(1, new byte[0]);

    assertNotNull(transport.lastFrameSent);
    assertEquals(1, transport.lastFrameSent.header().length());
    assertEquals(0, transport.lastFrameSent.pdu().remaining());

    transport.frameReceiver.accept(
        new ModbusTcpFrame(new MbapHeader(0, 1, 1, 1), ByteBuffer.allocate(0)));

    assertArrayEquals(new byte[0], cs.toCompletableFuture().get());
  }

  @Test
  void sendRawAcceptsEmptyResponsePdu() throws Exception {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    CompletionStage<byte[]> cs = client.sendRawAsync(1, new byte[] {0x5A});

    transport.frameReceiver.accept(
        new ModbusTcpFrame(new MbapHeader(0, 1, 1, 1), ByteBuffer.allocate(0)));

    assertArrayEquals(new byte[0], cs.toCompletableFuture().get());
  }

  /**
   * Tests typed handling of an erroneous empty response PDU.
   *
   * @see <a
   *     href="https://github.com/digitalpetri/modbus/issues/121">https://github.com/digitalpetri/modbus/issues/121</a>
   */
  @Test
  void sendAsyncStillRejectsEmptyResponsePdu() {
    var transport = new TestTransport();
    var client = ModbusTcpClient.create(transport);

    var cs = client.sendAsync(1, new ReadHoldingRegistersRequest(0, 1));

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

  @Test
  void requestFailsWhenTimeoutCannotBeScheduled() throws Exception {
    var failure = new RejectedExecutionException("scheduler shut down");
    var transport = new TestTransport();
    var client =
        ModbusTcpClient.create(
            transport,
            cfg ->
                cfg.timeoutScheduler =
                    (task, delay, unit) -> {
                      throw failure;
                    });

    // Neither call throws; the stage fails, and the request is never handed to the transport.
    var typed = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));
    var raw = client.sendRawAsync(1, new byte[] {3, 0, 0, 0, 1});

    for (CompletionStage<?> response : List.of(typed, raw)) {
      ExecutionException e =
          assertThrows(
              ExecutionException.class,
              () -> response.toCompletableFuture().get(1, TimeUnit.SECONDS));
      var cause = assertInstanceOf(QueuedRequestTimeoutException.class, e.getCause());
      assertSame(failure, cause.getCause());
    }

    var sync =
        assertThrows(
            ModbusTimeoutException.class,
            () -> client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 1)));
    assertInstanceOf(QueuedRequestTimeoutException.class, sync.getCause());

    assertNull(transport.lastFrameSent);
  }

  private static class RecordingTimeoutScheduler implements TimeoutScheduler {

    final List<TimeoutHandle> handles = new ArrayList<>();
    final List<Task> tasks = new ArrayList<>();

    @Override
    public TimeoutHandle newTimeout(Task task, long delay, TimeUnit unit) {
      var handle =
          new TimeoutHandle() {
            boolean cancelled;

            @Override
            public void cancel() {
              cancelled = true;
            }

            @Override
            public boolean isCancelled() {
              return cancelled;
            }
          };
      handles.add(handle);
      tasks.add(task);
      return handle;
    }
  }

  private static class TestTransport implements ModbusTcpClientTransport {

    boolean connected = false;
    ModbusTcpFrame lastFrameSent;
    Consumer<ModbusTcpFrame> frameReceiver;
    Supplier<Runnable> connectionLost;

    @Override
    public void onConnectionLost(Supplier<Runnable> callback) {
      connectionLost = callback;
    }

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
