package com.digitalpetri.modbus.client;

import com.digitalpetri.modbus.Crc16;
import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.TimeoutScheduler.TimeoutHandle;
import com.digitalpetri.modbus.exceptions.ModbusCrcException;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.pdu.ModbusPdu;
import com.digitalpetri.modbus.pdu.ModbusRequestPdu;
import com.digitalpetri.modbus.pdu.ModbusResponsePdu;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusRtuClient extends ModbusClient {

  /**
   * The unit/slave ID used when sending broadcast messages.
   */
  private static final int BROADCAST_ID = 0;

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ArrayDeque<ResponsePromise> promises = new ArrayDeque<>();
  private final Map<ResponsePromise, TimeoutHandle> timeouts = new ConcurrentHashMap<>();

  private final ModbusClientConfig config;
  private final ModbusRtuClientTransport transport;

  public ModbusRtuClient(ModbusClientConfig config, ModbusRtuClientTransport transport) {
    super(transport);

    this.config = config;
    this.transport = transport;

    transport.receive(this::onFrameReceived);
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

    var promise = new ResponsePromise(
        unitId,
        request.getFunctionCode(),
        new CompletableFuture<>()
    );

    synchronized (promises) {
      promises.push(promise);
    }

    long timeoutMillis = config.requestTimeout().toMillis();
    TimeoutHandle timeout = config.timeoutScheduler().newTimeout(t -> {
      boolean removed;
      synchronized (promises) {
        removed = promises.remove(promise);
      }
      if (removed) {
        // The frame parser needs to be reset!
        // It could be "stuck" in Accumulating or ParseError states if the timeout was caused by
        // an incomplete or invalid response rather than no response.
        resetFrameParser();

        promise.future.completeExceptionally(
            new TimeoutException("request timed out after %sms".formatted(timeoutMillis))
        );
      }
    }, timeoutMillis, TimeUnit.MILLISECONDS);

    timeouts.put(promise, timeout);

    transport.send(new ModbusRtuFrame(unitId, pdu, crc)).whenComplete((v, ex) -> {
      if (ex != null) {
        boolean removed;
        synchronized (promises) {
          removed = promises.remove(promise);
        }
        if (removed) {
          promise.future.completeExceptionally(ex);
        }
        TimeoutHandle t = timeouts.remove(promise);
        if (t != null) {
          t.cancel();
        }
      }
    });

    return promise.future;
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
    ResponsePromise promise;
    synchronized (promises) {
      promise = promises.poll();
    }

    if (promise != null) {
      TimeoutHandle t = timeouts.remove(promise);
      if (t != null) {
        t.cancel();
      }

      if (!verifyCrc16(frame)) {
        resetFrameParser();

        promise.future.completeExceptionally(new ModbusCrcException(frame));
        return;
      }

      int slaveId = frame.unitId();

      if (promise.slaveId != slaveId) {
        promise.future.completeExceptionally(
            new ModbusException(
                "slave id mismatch: %s != %s"
                    .formatted(promise.slaveId, slaveId))
        );
        return;
      }

      ByteBuffer buffer = frame.pdu();
      int functionCode = buffer.get(buffer.position()) & 0xFF;

      if (functionCode < 0x80) {
        if (functionCode != promise.functionCode) {
          // Response might be out of sync, e.g. the timeout elapsed in request A,
          // we sent request B, and now we're receiving response A.

          promise.future.completeExceptionally(
              new ModbusException(
                  "function code mismatch: %s != %s"
                      .formatted(promise.functionCode, functionCode))
          );

          // Clear out any pending promises.
          var pending = new ArrayList<ResponsePromise>();
          synchronized (promises) {
            while (!promises.isEmpty()) {
              pending.add(promises.poll());
            }
          }
          pending.forEach(
              p ->
                  p.future.completeExceptionally(new ModbusException("synchronization error"))
          );
        } else {
          try {
            ModbusPdu modbusPdu = config.responseSerializer().decode(functionCode, buffer);
            promise.future.complete((ModbusResponsePdu) modbusPdu);
          } catch (Exception e) {
            promise.future.completeExceptionally(e);
          }
        }
      } else {
        int exceptionCode = buffer.get();

        promise.future.completeExceptionally(
            new ModbusResponseException(promise.functionCode, exceptionCode)
        );
      }
    } else {
      logger.warn("No pending request for response frame: {}", frame);
    }
  }

  /**
   * Reset the transport's frame parser.
   */
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
      ModbusRtuClientTransport transport,
      Consumer<ModbusClientConfig.Builder> configure
  ) {

    var builder = new ModbusClientConfig.Builder();
    configure.accept(builder);
    return new ModbusRtuClient(builder.build(), transport);
  }

  private record ResponsePromise(
      int slaveId,
      int functionCode,
      CompletableFuture<ModbusResponsePdu> future
  ) {}

}
