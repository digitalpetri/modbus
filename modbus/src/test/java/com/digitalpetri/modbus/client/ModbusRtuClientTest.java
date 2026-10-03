package com.digitalpetri.modbus.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.ModbusPduSerializer.DefaultResponseSerializer;
import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.TimeoutScheduler;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusTimeoutException;
import com.digitalpetri.modbus.pdu.ModbusResponsePdu;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.pdu.ReadInputRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class ModbusRtuClientTest {

  @Test
  void responsesAreMatchedToRequestsInOrder() throws Exception {
    var transport = new RecordingRtuTransport();
    var client = ModbusRtuClient.create(transport);

    client.connect();

    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    CompletableFuture<ReadHoldingRegistersResponse> b = readAsync(client, 100);

    // Only one request is sent until it gets a response.
    assertEquals(0, startAddress(transport.nextSentFrame()));
    transport.assertNoFrameSent();

    transport.respond(client, registers(0x0A));
    assertArrayEquals(registers(0x0A), a.get(1, TimeUnit.SECONDS).registers());

    assertEquals(100, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0B));
    assertArrayEquals(registers(0x0B), b.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void nextRequestIsSentWhenInFlightRequestTimesOut() throws Exception {
    var scheduler = new ManualTimeoutScheduler();
    var transport = new RecordingRtuTransport();
    var client = ModbusRtuClient.create(transport, cfg -> cfg.timeoutScheduler = scheduler);

    client.connect();

    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    CompletableFuture<ReadHoldingRegistersResponse> b = readAsync(client, 100);
    Runnable timeoutA = scheduler.nextTimeout();

    assertEquals(0, startAddress(transport.nextSentFrame()));

    timeoutA.run();
    var e = assertThrows(ExecutionException.class, () -> a.get(1, TimeUnit.SECONDS));
    assertInstanceOf(TimeoutException.class, e.getCause());

    assertEquals(100, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0B));
    assertArrayEquals(registers(0x0B), b.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void queuedRequestThatTimesOutIsNotSent() throws Exception {
    var scheduler = new ManualTimeoutScheduler();
    var transport = new RecordingRtuTransport();
    var client = ModbusRtuClient.create(transport, cfg -> cfg.timeoutScheduler = scheduler);

    client.connect();

    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    CompletableFuture<ReadHoldingRegistersResponse> b = readAsync(client, 100);
    CompletableFuture<ReadHoldingRegistersResponse> c = readAsync(client, 200);
    scheduler.nextTimeout();
    Runnable timeoutB = scheduler.nextTimeout();

    assertEquals(0, startAddress(transport.nextSentFrame()));

    timeoutB.run();
    var e = assertThrows(ExecutionException.class, () -> b.get(1, TimeUnit.SECONDS));
    assertInstanceOf(TimeoutException.class, e.getCause());

    transport.respond(client, registers(0x0A));
    assertArrayEquals(registers(0x0A), a.get(1, TimeUnit.SECONDS).registers());

    // B was skipped; C is the next request written.
    assertEquals(200, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0C));
    assertArrayEquals(registers(0x0C), c.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void nextRequestIsSentWhenSendFails() throws Exception {
    var failure = new ModbusException("write failed");
    var transport =
        new RecordingRtuTransport() {
          @Override
          public CompletionStage<Void> send(ModbusRtuFrame frame) {
            super.send(frame);
            if (startAddress(frame) == 0) {
              return CompletableFuture.failedFuture(failure);
            } else {
              return CompletableFuture.completedFuture(null);
            }
          }
        };
    var client = ModbusRtuClient.create(transport);

    client.connect();

    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    CompletableFuture<ReadHoldingRegistersResponse> b = readAsync(client, 100);

    assertEquals(0, startAddress(transport.nextSentFrame()));
    var e = assertThrows(ExecutionException.class, () -> a.get(1, TimeUnit.SECONDS));
    assertSame(failure, e.getCause());

    assertEquals(100, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0B));
    assertArrayEquals(registers(0x0B), b.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void functionCodeMismatchFailsOnlyInFlightRequest() throws Exception {
    var transport = new RecordingRtuTransport();
    var client = ModbusRtuClient.create(transport);

    client.connect();

    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    CompletableFuture<ReadHoldingRegistersResponse> b = readAsync(client, 100);

    assertEquals(0, startAddress(transport.nextSentFrame()));
    transport.respond(client, new ReadInputRegistersResponse(registers(0x0A)));
    var e = assertThrows(ExecutionException.class, () -> a.get(1, TimeUnit.SECONDS));
    assertInstanceOf(ModbusException.class, e.getCause());

    // B was queued, not sent, so it isn't affected by the mismatch.
    assertEquals(100, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0B));
    assertArrayEquals(registers(0x0B), b.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void queuedRequestCancelledByCallerIsNotSent() throws Exception {
    var transport = new RecordingRtuTransport();
    var client = ModbusRtuClient.create(transport);

    client.connect();

    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    // sendAsync, because cancelling the future from readHoldingRegistersAsync doesn't reach the
    // client's own future.
    CompletableFuture<ModbusResponsePdu> b =
        client.sendAsync(1, new ReadHoldingRegistersRequest(100, 1)).toCompletableFuture();
    CompletableFuture<ReadHoldingRegistersResponse> c = readAsync(client, 200);

    assertEquals(0, startAddress(transport.nextSentFrame()));
    b.cancel(false);

    transport.respond(client, registers(0x0A));
    assertArrayEquals(registers(0x0A), a.get(1, TimeUnit.SECONDS).registers());

    // B was skipped; C is the next request written.
    assertEquals(200, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0C));
    assertArrayEquals(registers(0x0C), c.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void requestFailsWhenTimeoutCannotBeScheduled() throws Exception {
    var failure = new RejectedExecutionException("scheduler shut down");
    var transport = new RecordingRtuTransport();
    var client =
        ModbusRtuClient.create(
            transport,
            cfg ->
                cfg.timeoutScheduler =
                    (task, delay, unit) -> {
                      throw failure;
                    });

    client.connect();

    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    var e = assertThrows(ExecutionException.class, () -> a.get(1, TimeUnit.SECONDS));
    assertSame(failure, e.getCause());

    transport.assertNoFrameSent();
  }

  @Test
  void timeoutHandleIsRemoved() throws ModbusExecutionException {
    var transport = new TimeoutRtuTransport();
    var client =
        ModbusRtuClient.create(transport, cfg -> cfg.requestTimeout = Duration.ofMillis(100));

    client.connect();

    assertThrows(
        ModbusTimeoutException.class,
        () -> client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 10)));

    assertEquals(0, client.timeouts.size());
  }

  @Test
  void sendIsCancelledBeforeRequestTimesOut() throws Exception {
    var sendFuture = new CompletableFuture<Void>();
    var transport =
        new TimeoutRtuTransport() {
          @Override
          public CompletionStage<Void> send(ModbusRtuFrame frame) {
            return sendFuture;
          }
        };
    var client =
        ModbusRtuClient.create(transport, cfg -> cfg.requestTimeout = Duration.ofMillis(100));

    client.connect();

    // Callbacks run when the request fails must already see the send cancelled, or a queued
    // write could still go out while they run.
    var cancelledWhenFailed = new CompletableFuture<Boolean>();
    client
        .readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 10))
        .whenComplete((r, ex) -> cancelledWhenFailed.complete(sendFuture.isCancelled()));

    assertTrue(cancelledWhenFailed.get(5, TimeUnit.SECONDS));
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void broadcastTimesOutWhenSendNeverCompletes() throws Exception {
    var transport =
        new TimeoutRtuTransport() {
          @Override
          public CompletionStage<Void> send(ModbusRtuFrame frame) {
            return new CompletableFuture<>();
          }
        };
    var client =
        ModbusRtuClient.create(transport, cfg -> cfg.requestTimeout = Duration.ofMillis(100));

    client.connect();

    var e =
        assertThrows(
            ModbusExecutionException.class,
            () -> client.broadcast(new WriteSingleRegisterRequest(0, 0x0A)));
    assertInstanceOf(TimeoutException.class, e.getCause());
  }

  @Test
  void broadcastAsyncTimesOutAndCancelsSend() throws Exception {
    var scheduler = new ManualTimeoutScheduler();
    var sendFuture = new CompletableFuture<Void>();
    var transport =
        new TimeoutRtuTransport() {
          @Override
          public CompletionStage<Void> send(ModbusRtuFrame frame) {
            return sendFuture;
          }
        };
    var client = ModbusRtuClient.create(transport, cfg -> cfg.timeoutScheduler = scheduler);

    client.connect();

    CompletableFuture<Void> broadcast =
        client.broadcastAsync(new WriteSingleRegisterRequest(0, 0x0A)).toCompletableFuture();

    scheduler.nextTimeout().run();

    var e = assertThrows(ExecutionException.class, () -> broadcast.get(1, TimeUnit.SECONDS));
    assertInstanceOf(TimeoutException.class, e.getCause());

    // The send was cancelled so a transport that queues writes skips the broadcast.
    assertTrue(sendFuture.isCancelled());
  }

  @Test
  void broadcastCompletesWhenSent() throws Exception {
    var transport = new RecordingRtuTransport();
    var client =
        ModbusRtuClient.create(transport, cfg -> cfg.requestTimeout = Duration.ofSeconds(1));

    client.connect();

    client.broadcast(new WriteSingleRegisterRequest(0, 0x0A));

    assertEquals(0, transport.nextSentFrame().unitId());
  }

  @Test
  void broadcastSendIsCancelledBeforeBroadcastFails() throws Exception {
    var sendFuture = new CompletableFuture<Void>();
    var transport =
        new TimeoutRtuTransport() {
          @Override
          public CompletionStage<Void> send(ModbusRtuFrame frame) {
            return sendFuture;
          }
        };
    var client =
        ModbusRtuClient.create(transport, cfg -> cfg.requestTimeout = Duration.ofMillis(100));

    client.connect();

    // Callbacks run when the broadcast fails must already see the send cancelled, or a queued
    // write could still go out while they run.
    var cancelledWhenFailed = new CompletableFuture<Boolean>();
    client
        .broadcastAsync(new WriteSingleRegisterRequest(0, 0x0A))
        .whenComplete((v, ex) -> cancelledWhenFailed.complete(sendFuture.isCancelled()));

    assertTrue(cancelledWhenFailed.get(5, TimeUnit.SECONDS));
  }

  @Test
  void broadcastTimedOutBeforeSendIsPublishedCancelsSend() throws Exception {
    var scheduler = new ManualTimeoutScheduler();
    var sendFuture = new CompletableFuture<Void>();
    var transport =
        new TimeoutRtuTransport() {
          @Override
          public CompletionStage<Void> send(ModbusRtuFrame frame) {
            // Fire the timeout while send() is still running, before the client publishes the
            // send future, to exercise the re-check after publication.
            scheduler.timeouts.remove().run();
            return sendFuture;
          }
        };
    var client = ModbusRtuClient.create(transport, cfg -> cfg.timeoutScheduler = scheduler);

    client.connect();

    CompletableFuture<Void> broadcast =
        client.broadcastAsync(new WriteSingleRegisterRequest(0, 0x0A)).toCompletableFuture();

    var e = assertThrows(ExecutionException.class, () -> broadcast.get(1, TimeUnit.SECONDS));
    assertInstanceOf(TimeoutException.class, e.getCause());

    // The re-check after publication cancelled the send so a queued write is skipped.
    assertTrue(sendFuture.isCancelled());
  }

  @Test
  void cancellingBroadcastCancelsSend() throws Exception {
    var sendFuture = new CompletableFuture<Void>();
    var transport =
        new TimeoutRtuTransport() {
          @Override
          public CompletionStage<Void> send(ModbusRtuFrame frame) {
            return sendFuture;
          }
        };
    var client =
        ModbusRtuClient.create(
            transport, cfg -> cfg.timeoutScheduler = new ManualTimeoutScheduler());

    client.connect();

    CompletableFuture<Void> broadcast =
        client.broadcastAsync(new WriteSingleRegisterRequest(0, 0x0A)).toCompletableFuture();

    // Callbacks run when the broadcast is cancelled must already see the send cancelled, or a
    // queued write could still go out while they run.
    var cancelledWhenCompleted = new AtomicBoolean(false);
    broadcast.whenComplete((v, ex) -> cancelledWhenCompleted.set(sendFuture.isCancelled()));

    assertTrue(broadcast.cancel(false));
    assertTrue(broadcast.isCancelled());
    assertTrue(sendFuture.isCancelled());
    assertTrue(cancelledWhenCompleted.get());
  }

  @Test
  void broadcastIsCancelledWhenTransportCancelsSend() throws Exception {
    var scheduler = new ManualTimeoutScheduler();
    var sendFuture = new CompletableFuture<Void>();
    var transport =
        new TimeoutRtuTransport() {
          @Override
          public CompletionStage<Void> send(ModbusRtuFrame frame) {
            return sendFuture;
          }
        };
    var client = ModbusRtuClient.create(transport, cfg -> cfg.timeoutScheduler = scheduler);

    client.connect();

    CompletableFuture<Void> broadcast =
        client.broadcastAsync(new WriteSingleRegisterRequest(0, 0x0A)).toCompletableFuture();

    // The transport cancels the send before the timeout fires, so this isn't a timeout.
    sendFuture.cancel(false);

    assertThrows(CancellationException.class, () -> broadcast.get(1, TimeUnit.SECONDS));
  }

  @Test
  void broadcastFailsWhenSendFails() throws Exception {
    var failure = new ModbusException("write failed");
    var transport =
        new TimeoutRtuTransport() {
          @Override
          public CompletionStage<Void> send(ModbusRtuFrame frame) {
            return CompletableFuture.failedFuture(failure);
          }
        };
    var client = ModbusRtuClient.create(transport);

    client.connect();

    var e =
        assertThrows(
            ModbusExecutionException.class,
            () -> client.broadcast(new WriteSingleRegisterRequest(0, 0x0A)));
    assertSame(failure, e.getCause());
  }

  @Test
  void broadcastFailsWhenTimeoutCannotBeScheduled() throws Exception {
    var failure = new RejectedExecutionException("scheduler shut down");
    var transport = new RecordingRtuTransport();
    var client =
        ModbusRtuClient.create(
            transport,
            cfg ->
                cfg.timeoutScheduler =
                    (task, delay, unit) -> {
                      throw failure;
                    });

    client.connect();

    var e =
        assertThrows(
            ExecutionException.class,
            () ->
                client
                    .broadcastAsync(new WriteSingleRegisterRequest(0, 0x0A))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS));
    assertSame(failure, e.getCause());

    transport.assertNoFrameSent();
  }

  private static CompletableFuture<ReadHoldingRegistersResponse> readAsync(
      ModbusRtuClient client, int address) {

    return client
        .readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(address, 1))
        .toCompletableFuture();
  }

  private static int startAddress(ModbusRtuFrame frame) {
    ByteBuffer pdu = frame.pdu();
    // function code, then the 2-byte starting address
    return pdu.getShort(pdu.position() + 1) & 0xFFFF;
  }

  private static byte[] registers(int value) {
    return new byte[] {(byte) (value >> 8), (byte) value};
  }

  /** Records sent frames and delivers responses on request. */
  private static class RecordingRtuTransport extends TimeoutRtuTransport {

    final BlockingQueue<ModbusRtuFrame> sentFrames = new LinkedBlockingQueue<>();

    volatile Consumer<ModbusRtuFrame> frameReceiver;

    @Override
    public CompletionStage<Void> send(ModbusRtuFrame frame) {
      sentFrames.add(frame);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void receive(Consumer<ModbusRtuFrame> frameReceiver) {
      this.frameReceiver = frameReceiver;
    }

    ModbusRtuFrame nextSentFrame() throws InterruptedException {
      ModbusRtuFrame frame = sentFrames.poll(1, TimeUnit.SECONDS);
      assertNotNull(frame, "expected a frame to be sent");
      return frame;
    }

    void assertNoFrameSent() throws InterruptedException {
      assertNull(sentFrames.poll(100, TimeUnit.MILLISECONDS), "expected no frame to be sent");
    }

    void respond(ModbusRtuClient client, byte[] registers) throws Exception {
      respond(client, new ReadHoldingRegistersResponse(registers));
    }

    void respond(ModbusRtuClient client, ModbusResponsePdu response) throws Exception {
      ByteBuffer pdu = ByteBuffer.allocate(256);
      DefaultResponseSerializer.INSTANCE.encode(response, pdu);
      pdu.flip();

      frameReceiver.accept(new ModbusRtuFrame(1, pdu, client.calculateCrc16(1, pdu)));
    }
  }

  /** A {@link TimeoutScheduler} whose timeouts only fire when the test runs them. */
  private static class ManualTimeoutScheduler implements TimeoutScheduler {

    final BlockingQueue<Runnable> timeouts = new LinkedBlockingQueue<>();

    @Override
    public TimeoutHandle newTimeout(Task task, long delay, TimeUnit unit) {
      var cancelled = new AtomicBoolean(false);

      var handle =
          new TimeoutHandle() {
            @Override
            public void cancel() {
              cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
              return cancelled.get();
            }
          };

      timeouts.add(
          () -> {
            if (!cancelled.get()) {
              task.run(handle);
            }
          });

      return handle;
    }

    /** Wait for the next timeout to be scheduled and return a {@link Runnable} that fires it. */
    Runnable nextTimeout() throws InterruptedException {
      Runnable timeout = timeouts.poll(1, TimeUnit.SECONDS);
      assertNotNull(timeout, "expected a timeout to be scheduled");
      return timeout;
    }
  }

  private static class TimeoutRtuTransport implements ModbusRtuClientTransport {
    boolean connected = false;

    @Override
    public void resetFrameParser() {}

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
    public CompletionStage<Void> send(ModbusRtuFrame frame) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void receive(Consumer<ModbusRtuFrame> frameReceiver) {}
  }
}
