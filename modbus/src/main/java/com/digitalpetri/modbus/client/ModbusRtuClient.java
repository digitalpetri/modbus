package com.digitalpetri.modbus.client;

import com.digitalpetri.modbus.Crc16;
import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.TimeoutScheduler.TimeoutHandle;
import com.digitalpetri.modbus.exceptions.ModbusCrcException;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import com.digitalpetri.modbus.pdu.ModbusPdu;
import com.digitalpetri.modbus.pdu.ModbusRequestPdu;
import com.digitalpetri.modbus.pdu.ModbusResponsePdu;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusRtuClient extends ModbusClient {

  /** The unit/slave ID used when sending broadcast messages. */
  private static final int BROADCAST_ID = 0;

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /** Executor used to run request state changes and to complete request futures. */
  private final Executor executor = Modbus.sharedExecutor();

  /**
   * Runs every request state change (submit, send, response, timeout, and send failure) serially.
   *
   * <p>RTU responses carry no transaction ID, so a response can only be matched to a request if at
   * most one request is outstanding at a time. The fields below are only accessed from tasks on
   * this queue, which lets the transport be called without holding a lock.
   */
  private final ExecutionQueue requestQueue = new ExecutionQueue(executor);

  /** Requests waiting to be sent, in the order submitted. */
  private final ArrayDeque<PendingRequest> queued = new ArrayDeque<>();

  /** The request that has been sent and is waiting for a response, or {@code null}. */
  private PendingRequest inFlight;

  // package visibility for testing
  final Map<PendingRequest, TimeoutHandle> timeouts = new ConcurrentHashMap<>();

  private final ModbusClientConfig config;
  private final ModbusRtuClientTransport transport;

  public ModbusRtuClient(ModbusClientConfig config, ModbusRtuClientTransport transport) {
    super(transport);

    this.config = config;
    this.transport = transport;

    transport.receive(this::onFrameReceived);
  }

  /**
   * Get the {@link ModbusClientConfig} used by this client.
   *
   * @return the {@link ModbusClientConfig} used by this client.
   */
  public ModbusClientConfig getConfig() {
    return config;
  }

  /**
   * Get the {@link ModbusRtuClientTransport} used by this client.
   *
   * @return the {@link ModbusRtuClientTransport} used by this client.
   */
  @Override
  public ModbusRtuClientTransport getTransport() {
    return transport;
  }

  @Override
  public CompletionStage<ModbusResponsePdu> sendAsync(int unitId, ModbusRequestPdu request) {
    ByteBuffer pdu = ByteBuffer.allocate(256);

    try {
      config.requestSerializer().encode(request, pdu);
      pdu.flip();
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }

    ByteBuffer crc = calculateCrc16(unitId, pdu);

    var pending =
        new PendingRequest(unitId, request.getFunctionCode(), new ModbusRtuFrame(unitId, pdu, crc));

    requestQueue.submit(
        () -> {
          // The timeout starts when the request is submitted, so it bounds how long the caller
          // waits, including time spent queued behind other requests. It's scheduled on the queue
          // so the timeout task can't run before the request is queued.
          TimeoutHandle timeout;
          try {
            timeout =
                config
                    .timeoutScheduler()
                    .newTimeout(
                        t -> requestQueue.submit(() -> onTimeout(pending)),
                        config.requestTimeout().toMillis(),
                        TimeUnit.MILLISECONDS);
          } catch (Exception e) {
            // e.g. RejectedExecutionException if the scheduler has been shut down. Without a
            // timeout the request could wait forever, so fail it instead of sending it.
            failRequest(pending, e);
            return;
          }

          timeouts.put(pending, timeout);

          queued.addLast(pending);
          sendNext();
        });

    return pending.future;
  }

  /**
   * Send the next queued request, unless a request is already in flight.
   *
   * <p>Must be called from a task on {@link #requestQueue}.
   */
  private void sendNext() {
    while (inFlight == null && !queued.isEmpty()) {
      PendingRequest pending = queued.poll();

      if (pending.future.isDone()) {
        // The caller cancelled or completed the future before the request was sent.
        cancelTimeout(pending);
        continue;
      }

      inFlight = pending;

      pending.sendFuture = send(pending.frame);
      pending.sendFuture.whenComplete(
          (v, ex) -> {
            if (ex != null) {
              requestQueue.submit(() -> onSendFailure(pending, ex));
            }
          });
    }
  }

  /**
   * Handle the timeout of a request, whether it's queued or in flight.
   *
   * <p>Must be called from a task on {@link #requestQueue}.
   */
  private void onTimeout(PendingRequest pending) {
    var ex =
        new TimeoutException(
            "request timed out after %sms".formatted(config.requestTimeout().toMillis()));

    if (pending == inFlight) {
      inFlight = null;

      // The frame parser needs to be reset!
      // It could be "stuck" in Accumulating or ParseError states if the timeout was
      // caused by an incomplete or invalid response rather than no response.
      resetFrameParser();

      // Cancel the send so a transport that queues writes doesn't write this request
      // after it timed out. Responses aren't matched to requests by any ID, so the
      // late request's response would be taken as the response to another request.
      // This happens before failRequest, so callers' callbacks see the send cancelled.
      try {
        pending.sendFuture.toCompletableFuture().cancel(false);
      } catch (UnsupportedOperationException ignored) {
        // This CompletionStage implementation can't be cancelled.
      }

      failRequest(pending, ex);
      sendNext();
    } else if (queued.remove(pending)) {
      // Timed out while waiting behind other requests; it was never sent.
      failRequest(pending, ex);
    }
  }

  /**
   * Handle a failure to send a request.
   *
   * <p>Must be called from a task on {@link #requestQueue}.
   */
  private void onSendFailure(PendingRequest pending, Throwable failure) {
    // Ignore the failure if the request already completed, e.g. it timed out and the timeout
    // cancelled the send.
    if (pending == inFlight) {
      inFlight = null;

      failRequest(pending, failure);
      sendNext();
    }
  }

  private void completeRequest(PendingRequest pending, ModbusResponsePdu response) {
    cancelTimeout(pending);

    // Complete off the request queue so caller callbacks, which may block on another request
    // from this client, don't hold up the queue.
    executor.execute(() -> pending.future.complete(response));
  }

  private void failRequest(PendingRequest pending, Throwable failure) {
    cancelTimeout(pending);

    // Complete off the request queue so caller callbacks, which may block on another request
    // from this client, don't hold up the queue.
    executor.execute(() -> pending.future.completeExceptionally(failure));
  }

  private void cancelTimeout(PendingRequest pending) {
    TimeoutHandle t = timeouts.remove(pending);
    if (t != null) {
      t.cancel();
    }
  }

  /**
   * Send {@code frame} using the transport, converting an exception thrown by the transport into a
   * failed {@link CompletionStage}.
   */
  private CompletionStage<Void> send(ModbusRtuFrame frame) {
    try {
      return transport.send(frame);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  /**
   * Send a broadcast request to all connected slaves. No response is returned to broadcast requests
   * sent by the master.
   *
   * <p>Broadcast requests are necessarily write commands.
   *
   * @param request the request to broadcast. Must be a write command.
   * @throws ModbusExecutionException if an error occurs while sending the request.
   */
  public void broadcast(ModbusRequestPdu request) throws ModbusExecutionException {
    try {
      broadcastAsync(request).toCompletableFuture().get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      throw new ModbusExecutionException(cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ModbusExecutionException(e);
    }
  }

  /**
   * Send a broadcast request to all connected slaves. No response is returned to broadcast requests
   * sent by the master.
   *
   * <p>Broadcast requests are necessarily write commands.
   *
   * @param request the request to broadcast. Must be a write command.
   * @return a {@link CompletionStage} that completes when the request has been sent.
   */
  public CompletionStage<Void> broadcastAsync(ModbusRequestPdu request) {
    ByteBuffer pdu = ByteBuffer.allocate(256);

    try {
      config.requestSerializer().encode(request, pdu);
      pdu.flip();
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }

    ByteBuffer crc = calculateCrc16(BROADCAST_ID, pdu);

    return transport.send(new ModbusRtuFrame(BROADCAST_ID, pdu, crc));
  }

  private void onFrameReceived(ModbusRtuFrame frame) {
    requestQueue.submit(
        () -> {
          PendingRequest pending = inFlight;

          if (pending != null) {
            inFlight = null;

            handleResponse(pending, frame);
            sendNext();
          } else {
            logger.warn("No pending request for response frame: {}", frame);
          }
        });
  }

  /**
   * Complete {@code pending} using the response {@code frame}.
   *
   * <p>Must be called from a task on {@link #requestQueue}.
   */
  private void handleResponse(PendingRequest pending, ModbusRtuFrame frame) {
    if (!verifyCrc16(frame)) {
      resetFrameParser();

      failRequest(pending, new ModbusCrcException(frame));
      return;
    }

    int slaveId = frame.unitId();

    if (pending.slaveId != slaveId) {
      failRequest(
          pending,
          new ModbusException("slave id mismatch: %s != %s".formatted(pending.slaveId, slaveId)));
      return;
    }

    ByteBuffer buffer = frame.pdu();
    int functionCode = buffer.get(buffer.position()) & 0xFF;

    if (functionCode < 0x80) {
      if (functionCode != pending.functionCode) {
        // Response might be out of sync, e.g. the timeout elapsed in request A,
        // we sent request B, and now we're receiving response A.
        failRequest(
            pending,
            new ModbusException(
                "function code mismatch: %s != %s".formatted(pending.functionCode, functionCode)));
      } else {
        try {
          ModbusPdu modbusPdu = config.responseSerializer().decode(functionCode, buffer);
          completeRequest(pending, (ModbusResponsePdu) modbusPdu);
        } catch (Exception e) {
          failRequest(pending, e);
        }
      }
    } else {
      int exceptionCode = buffer.get();

      failRequest(pending, new ModbusResponseException(pending.functionCode, exceptionCode));
    }
  }

  /** Reset the transport's frame parser. */
  protected void resetFrameParser() {
    transport.resetFrameParser();
  }

  /**
   * Calculate the CRC-16 for the given frame (unit ID and PDU).
   *
   * @param unitId the unit ID.
   * @param pdu the PDU.
   * @return a {@link ByteBuffer} containing the calculated CRC-16.
   */
  protected ByteBuffer calculateCrc16(int unitId, ByteBuffer pdu) {
    var crc16 = new Crc16();
    crc16.update(unitId);
    crc16.update(pdu);

    ByteBuffer crc = ByteBuffer.allocate(2);
    // write crc in little-endian order
    crc.put((byte) (crc16.getValue() & 0xFF));
    crc.put((byte) ((crc16.getValue() >> 8) & 0xFF));

    return crc.flip();
  }

  /**
   * Verify the reported CRC-16 matches the calculated CRC-16.
   *
   * @param frame the frame to verify.
   * @return {@code true} if the CRC-16 matches, {@code false} otherwise.
   */
  protected boolean verifyCrc16(ModbusRtuFrame frame) {
    var crc16 = new Crc16();
    crc16.update(frame.unitId());
    crc16.update(frame.pdu());
    int expected = crc16.getValue();

    int offset = frame.crc().position();
    int low = frame.crc().get(offset) & 0xFF;
    int high = frame.crc().get(offset + 1) & 0xFF;
    int reported = (high << 8) | low;

    return expected == reported;
  }

  /**
   * Create a new {@link ModbusRtuClient} using the given {@link ModbusRtuClientTransport} and a
   * {@link ModbusClientConfig} with the default values.
   *
   * @param transport the {@link ModbusRtuClientTransport} to use.
   * @return a new {@link ModbusRtuClient}.
   */
  public static ModbusRtuClient create(ModbusRtuClientTransport transport) {
    return create(transport, cfg -> {});
  }

  /**
   * Create a new {@link ModbusRtuClient} using the given {@link ModbusRtuClientTransport} and a
   * callback for building a {@link ModbusClientConfig}.
   *
   * @param transport the {@link ModbusRtuClientTransport} to use.
   * @param configure a callback used to build a {@link ModbusClientConfig}.
   * @return a new {@link ModbusRtuClient}.
   */
  public static ModbusRtuClient create(
      ModbusRtuClientTransport transport, Consumer<ModbusClientConfig.Builder> configure) {

    var builder = new ModbusClientConfig.Builder();
    configure.accept(builder);
    return new ModbusRtuClient(builder.build(), transport);
  }

  // Not a record: requests are compared by identity, and sendFuture is assigned when it's sent.
  private static final class PendingRequest {

    final int slaveId;
    final int functionCode;
    final ModbusRtuFrame frame;
    final CompletableFuture<ModbusResponsePdu> future = new CompletableFuture<>();

    /** Assigned on the request queue when the request is sent. */
    CompletionStage<Void> sendFuture;

    PendingRequest(int slaveId, int functionCode, ModbusRtuFrame frame) {
      this.slaveId = slaveId;
      this.functionCode = functionCode;
      this.frame = frame;
    }
  }
}
