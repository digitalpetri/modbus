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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
  void broadcastIsSentInSubmissionOrder() throws Exception {
    var transport = new RecordingRtuTransport();
    var client = ModbusRtuClient.create(transport);

    client.connect();

    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    CompletableFuture<Void> x = broadcastAsync(client, 50);
    CompletableFuture<ReadHoldingRegistersResponse> b = readAsync(client, 100);

    // The broadcast waits for A's response.
    assertEquals(0, startAddress(transport.nextSentFrame()));
    transport.assertNoFrameSent();

    transport.respond(client, registers(0x0A));
    assertArrayEquals(registers(0x0A), a.get(1, TimeUnit.SECONDS).registers());

    ModbusRtuFrame broadcast = transport.nextSentFrame();
    assertEquals(0, broadcast.unitId());
    assertEquals(50, startAddress(broadcast));
    x.get(1, TimeUnit.SECONDS);

    // No turnaround delay by default, and no response to wait for.
    assertEquals(100, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0B));
    assertArrayEquals(registers(0x0B), b.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void nextRequestWaitsForBroadcastTurnaroundDelay() throws Exception {
    var scheduler = new ManualTimeoutScheduler();
    var transport = new RecordingRtuTransport();
    var client =
        ModbusRtuClient.create(
            transport,
            cfg -> {
              cfg.timeoutScheduler = scheduler;
              cfg.broadcastTurnaroundDelay = Duration.ofMillis(100);
            });

    client.connect();

    CompletableFuture<Void> x = broadcastAsync(client, 50);
    assertEquals(50, startAddress(transport.nextSentFrame()));
    x.get(1, TimeUnit.SECONDS);

    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);

    scheduler.nextTimeout(); // the broadcast's request timeout, cancelled when it was written
    Runnable turnaroundElapsed = scheduler.nextTimeout();
    transport.assertNoFrameSent();

    turnaroundElapsed.run();
    assertEquals(0, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0A));
    assertArrayEquals(registers(0x0A), a.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void nextRequestWaitsForTurnaroundWhenBroadcastTimesOut() throws Exception {
    var scheduler = new ManualTimeoutScheduler();
    var broadcastSendFuture = new CompletableFuture<Void>();
    var transport = new BroadcastRtuTransport(broadcastSendFuture);
    var client =
        ModbusRtuClient.create(
            transport,
            cfg -> {
              cfg.timeoutScheduler = scheduler;
              cfg.broadcastTurnaroundDelay = Duration.ofMillis(100);
            });

    client.connect();

    CompletableFuture<Void> x = broadcastAsync(client, 50);
    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    Runnable timeoutX = scheduler.nextTimeout();
    scheduler.nextTimeout(); // A's request timeout

    // The broadcast write stalls, so A waits. A stray frame isn't taken as a broadcast response.
    assertEquals(50, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0xFF));
    transport.assertNoFrameSent();
    assertFalse(x.isDone());

    timeoutX.run();
    var e = assertThrows(ExecutionException.class, () -> x.get(1, TimeUnit.SECONDS));
    assertInstanceOf(TimeoutException.class, e.getCause());
    assertTrue(broadcastSendFuture.isCancelled());

    // The cancelled write may have gone out anyway, so A still waits for the turnaround delay.
    Runnable turnaroundElapsed = scheduler.nextTimeout();
    transport.assertNoFrameSent();

    turnaroundElapsed.run();
    assertEquals(0, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0A));
    assertArrayEquals(registers(0x0A), a.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void nextRequestWaitsForTimedOutBroadcastToFinishWriting() throws Exception {
    var scheduler = new ManualTimeoutScheduler();
    var broadcastWrite = new StartedWriteFuture();
    var transport = new BroadcastRtuTransport(broadcastWrite);
    var client =
        ModbusRtuClient.create(
            transport,
            cfg -> {
              cfg.timeoutScheduler = scheduler;
              cfg.broadcastTurnaroundDelay = Duration.ofMillis(100);
            });

    client.connect();

    CompletableFuture<Void> x = broadcastAsync(client, 50);
    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    Runnable timeoutX = scheduler.nextTimeout();
    scheduler.nextTimeout(); // A's request timeout

    assertEquals(50, startAddress(transport.nextSentFrame()));

    // The caller stops waiting at the request timeout, even though the write goes on.
    timeoutX.run();
    var e = assertThrows(ExecutionException.class, () -> x.get(1, TimeUnit.SECONDS));
    assertInstanceOf(TimeoutException.class, e.getCause());
    assertFalse(broadcastWrite.isDone());

    // A serial transport writes frames one at a time, so A's write would start the moment the
    // broadcast's write finished. A waits for the write, then for the turnaround delay.
    transport.assertNoFrameSent();
    scheduler.assertNoTimeoutScheduled();

    broadcastWrite.complete(null);
    Runnable turnaroundElapsed = scheduler.nextTimeout();
    transport.assertNoFrameSent();

    turnaroundElapsed.run();
    assertEquals(0, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0A));
    assertArrayEquals(registers(0x0A), a.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void requestsWaitingBehindTimedOutBroadcastWriteTimeOut() throws Exception {
    var scheduler = new ManualTimeoutScheduler();
    var broadcastWrite = new StartedWriteFuture();
    var transport = new BroadcastRtuTransport(broadcastWrite);
    var client =
        ModbusRtuClient.create(
            transport,
            cfg -> {
              cfg.timeoutScheduler = scheduler;
              cfg.broadcastTurnaroundDelay = Duration.ofMillis(100);
            });

    client.connect();

    CompletableFuture<Void> x = broadcastAsync(client, 50);
    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    Runnable timeoutX = scheduler.nextTimeout();
    Runnable timeoutA = scheduler.nextTimeout();

    assertEquals(50, startAddress(transport.nextSentFrame()));
    timeoutX.run();
    var e = assertThrows(ExecutionException.class, () -> x.get(1, TimeUnit.SECONDS));
    assertInstanceOf(TimeoutException.class, e.getCause());

    // The broadcast write is stalled, so A times out without being sent.
    timeoutA.run();
    e = assertThrows(ExecutionException.class, () -> a.get(1, TimeUnit.SECONDS));
    assertInstanceOf(TimeoutException.class, e.getCause());
    transport.assertNoFrameSent();

    // e.g. disconnect() closes the port. Slaves discard the partial frame, so there's no
    // turnaround delay.
    broadcastWrite.completeExceptionally(new ModbusException("port closed"));

    CompletableFuture<ReadHoldingRegistersResponse> b = readAsync(client, 100);
    scheduler.nextTimeout(); // B's request timeout
    assertEquals(100, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0B));
    assertArrayEquals(registers(0x0B), b.get(1, TimeUnit.SECONDS).registers());
    scheduler.assertNoTimeoutScheduled();
    assertEquals(0, client.timeouts.size());
  }

  @Test
  void nextRequestIsSentWhenTurnaroundDelayCannotBeScheduled() throws Exception {
    var calls = new AtomicInteger();
    var scheduler =
        new ManualTimeoutScheduler() {
          @Override
          public TimeoutHandle newTimeout(Task task, long delay, TimeUnit unit) {
            // The broadcast's request timeout, then the turnaround delay.
            if (calls.incrementAndGet() == 2) {
              throw new RejectedExecutionException("scheduler shut down");
            }
            return super.newTimeout(task, delay, unit);
          }
        };
    var transport = new RecordingRtuTransport();
    var client =
        ModbusRtuClient.create(
            transport,
            cfg -> {
              cfg.timeoutScheduler = scheduler;
              cfg.broadcastTurnaroundDelay = Duration.ofMillis(100);
            });

    client.connect();

    CompletableFuture<Void> x = broadcastAsync(client, 50);
    assertEquals(50, startAddress(transport.nextSentFrame()));
    x.get(1, TimeUnit.SECONDS);

    CompletableFuture<ReadHoldingRegistersResponse> a = readAsync(client, 0);
    assertEquals(0, startAddress(transport.nextSentFrame()));
    transport.respond(client, registers(0x0A));
    assertArrayEquals(registers(0x0A), a.get(1, TimeUnit.SECONDS).registers());
    assertEquals(0, client.timeouts.size());
  }

  private static CompletableFuture<Void> broadcastAsync(ModbusRtuClient client, int address) {
    return client.broadcastAsync(new WriteSingleRegisterRequest(address, 1)).toCompletableFuture();
  }

  private static CompletableFuture<ReadHoldingRegistersResponse> readAsync(
      ModbusRtuClient client, int address) {

    return client
        .readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(address, 1))
        .toCompletableFuture();
  }

  private static int startAddress(ModbusRtuFrame frame) {
    ByteBuffer pdu = frame.pdu();
    // function code, then the 2-byte starting (or register) address
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

  /** Returns a given send future for broadcasts, and a completed one for other requests. */
  private static class BroadcastRtuTransport extends RecordingRtuTransport {

    final CompletableFuture<Void> broadcastSendFuture;

    BroadcastRtuTransport(CompletableFuture<Void> broadcastSendFuture) {
      this.broadcastSendFuture = broadcastSendFuture;
    }

    @Override
    public CompletionStage<Void> send(ModbusRtuFrame frame) {
      super.send(frame);
      if (frame.unitId() == 0) {
        return broadcastSendFuture;
      } else {
        return CompletableFuture.completedFuture(null);
      }
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

    void assertNoTimeoutScheduled() throws InterruptedException {
      assertNull(timeouts.poll(100, TimeUnit.MILLISECONDS), "expected no timeout to be scheduled");
    }
  }

  /**
   * A send future for a write that has started. Like {@code SerialPortClientTransport}, it can't be
   * cancelled, and it completes when the write does.
   */
  private static class StartedWriteFuture extends CompletableFuture<Void> {

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
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
