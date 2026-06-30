package com.digitalpetri.modbus.client;

import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.TimeoutScheduler.TimeoutHandle;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.ModbusTimeoutException;
import com.digitalpetri.modbus.internal.util.Hex;
import com.digitalpetri.modbus.pdu.ModbusPdu;
import com.digitalpetri.modbus.pdu.ModbusRequestPdu;
import com.digitalpetri.modbus.pdu.ModbusResponsePdu;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusTcpClient extends ModbusClient {

  /** Fixed protocol ID identifying the protocol as Modbus in {@link MbapHeader}. */
  private static final int MODBUS_PROTOCOL_ID = 0;

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final Map<Integer, ResponsePromise> promises = new ConcurrentHashMap<>();

  private final AtomicReference<TransactionSequence> transactionSequence = new AtomicReference<>();

  private final ModbusClientConfig config;
  private final ModbusTcpClientTransport transport;

  public ModbusTcpClient(ModbusClientConfig config, ModbusTcpClientTransport transport) {
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
   * Get the {@link ModbusTcpClientTransport} used by this client.
   *
   * @return the {@link ModbusTcpClientTransport} used by this client.
   */
  @Override
  public ModbusTcpClientTransport getTransport() {
    return transport;
  }

  /**
   * Send an already-encoded request PDU and wait for the matching Modbus/TCP response PDU.
   *
   * <p>The supplied bytes are the PDU only, beginning with the function code; callers must not
   * include an MBAP header. The client allocates the transaction id, adds the MBAP header, applies
   * the configured request timeout, and correlates the response by transaction id.
   *
   * <p>The returned bytes are the response PDU exactly as received after MBAP correlation. Raw
   * calls do not decode standard Modbus exception PDUs, so a response whose first byte is {@code
   * requestFunction + 0x80} is returned to the caller instead of being translated into a {@link
   * ModbusResponseException}. Transport send failures and request timeouts are still reported
   * through the same exceptions as other client calls.
   *
   * @param unitId the unit id to place in the MBAP header.
   * @param pduBytes the encoded request PDU bytes, without an MBAP header.
   * @return the response PDU bytes exactly as received, without the MBAP header.
   * @throws ModbusExecutionException if the request fails before a response is received.
   * @throws ModbusResponseException if the raw request stage is completed with a Modbus response
   *     exception by another client layer; response PDU bytes are not decoded into this exception
   *     by raw calls.
   * @throws ModbusTimeoutException if the configured request timeout expires before a response is
   *     received.
   */
  public byte[] sendRaw(int unitId, byte[] pduBytes)
      throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    try {
      return sendRawAsync(unitId, pduBytes).toCompletableFuture().get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof TimeoutException ex) {
        throw new ModbusTimeoutException(ex);
      } else if (cause instanceof ModbusResponseException ex) {
        throw ex;
      } else {
        throw new ModbusExecutionException(cause);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ModbusExecutionException(e);
    }
  }

  /**
   * Send an already-encoded request PDU and complete with the matching Modbus/TCP response PDU.
   *
   * <p>The supplied bytes are the PDU only, beginning with the function code; callers must not
   * include an MBAP header. The client allocates the transaction id, adds the MBAP header, applies
   * the configured request timeout, and correlates the response by transaction id.
   *
   * <p>The completed value contains the response PDU exactly as received after MBAP correlation.
   * Raw calls do not decode standard Modbus exception PDUs, so a response whose first byte is
   * {@code requestFunction + 0x80} completes successfully with those raw bytes. Transport send
   * failures, request timeouts, and malformed TCP responses such as an empty PDU still complete the
   * returned stage exceptionally.
   *
   * @param unitId the unit id to place in the MBAP header.
   * @param pduBytes the encoded request PDU bytes, without an MBAP header.
   * @return a stage that completes with the response PDU bytes exactly as received, without the
   *     MBAP header.
   */
  public CompletionStage<byte[]> sendRawAsync(int unitId, byte[] pduBytes) {
    if (pduBytes.length == 0) {
      return CompletableFuture.failedFuture(new ModbusException("empty request PDU"));
    }

    CompletionStage<ByteBuffer> cs =
        sendBufferAsync(unitId, ByteBuffer.wrap(pduBytes), RawResponsePromise::new);

    return cs.thenApply(
        buffer -> {
          var bytes = new byte[buffer.remaining()];
          buffer.get(bytes);
          return bytes;
        });
  }

  @Override
  public CompletionStage<ModbusResponsePdu> sendAsync(int unitId, ModbusRequestPdu request) {
    ByteBuffer pduBytes = ByteBuffer.allocate(256);

    try {
      config.requestSerializer().encode(request, pduBytes);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }

    ByteBuffer requestBuffer = pduBytes.flip();
    int functionCode = requestBuffer.get(requestBuffer.position()) & 0xFF;
    CompletionStage<ByteBuffer> cs =
        sendBufferAsync(
            unitId,
            requestBuffer,
            (future, timeout) -> new ModbusResponsePromise(functionCode, future, timeout));

    return cs.thenApply(
        responseBuffer -> {
          try {
            ModbusPdu decoded =
                config.responseSerializer().decode(request.getFunctionCode(), responseBuffer);
            return (ModbusResponsePdu) decoded;
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        });
  }

  private CompletionStage<ByteBuffer> sendBufferAsync(
      int unitId, ByteBuffer buffer, ResponsePromiseFactory promiseFactory) {
    TransactionSequence sequence =
        transactionSequence.updateAndGet(ts -> ts != null ? ts : createTransactionSequence());
    int transactionId = sequence.next();

    var header = new MbapHeader(transactionId, MODBUS_PROTOCOL_ID, 1 + buffer.remaining(), unitId);

    long timeoutMillis = config.requestTimeout().toMillis();
    TimeoutHandle timeout =
        config
            .timeoutScheduler()
            .newTimeout(
                t -> {
                  ResponsePromise promise = promises.remove(header.transactionId());
                  if (promise != null) {
                    promise
                        .future()
                        .completeExceptionally(
                            new TimeoutException(
                                "request timed out after %sms".formatted(timeoutMillis)));
                  }
                },
                timeoutMillis,
                TimeUnit.MILLISECONDS);

    ResponsePromise pending = promiseFactory.create(new CompletableFuture<>(), timeout);

    promises.put(header.transactionId(), pending);

    transport
        .send(new ModbusTcpFrame(header, buffer))
        .whenComplete(
            (v, ex) -> {
              if (ex != null) {
                ResponsePromise promise = promises.remove(header.transactionId());
                if (promise != null) {
                  promise.timeout().cancel();
                  promise.future().completeExceptionally(ex);
                }
              }
            });

    return pending.future();
  }

  private void onFrameReceived(ModbusTcpFrame frame) {
    MbapHeader header = frame.header();
    ResponsePromise promise = promises.remove(header.transactionId());

    if (promise != null) {
      promise.timeout().cancel();

      ByteBuffer buffer = frame.pdu();

      if (buffer.remaining() == 0) {
        promise.future().completeExceptionally(new ModbusException("empty response PDU"));
        return;
      }

      promise.complete(buffer);
    } else {
      logger.warn("No pending request for response frame: {}", frame);
    }
  }

  /**
   * Create and return the {@link TransactionSequence} that will be used to generate transaction
   * ids.
   *
   * @return the {@link TransactionSequence} that will be used to generate transaction ids.
   */
  protected TransactionSequence createTransactionSequence() {
    return new DefaultTransactionSequence();
  }

  /**
   * Create a new {@link ModbusTcpClient} using the given {@link ModbusTcpClientTransport} and a
   * {@link ModbusClientConfig} with the default values.
   *
   * @param transport the {@link ModbusTcpClientTransport} to use.
   * @return a new {@link ModbusTcpClient}.
   */
  public static ModbusTcpClient create(ModbusTcpClientTransport transport) {
    return create(transport, cfg -> {});
  }

  /**
   * Create a new {@link ModbusTcpClient} using the given {@link ModbusTcpClientTransport} and a
   * callback for building a {@link ModbusClientConfig}.
   *
   * @param transport the {@link ModbusTcpClientTransport} to use.
   * @param configure a callback used to build a {@link ModbusClientConfig}.
   * @return a new {@link ModbusTcpClient}.
   */
  public static ModbusTcpClient create(
      ModbusTcpClientTransport transport, Consumer<ModbusClientConfig.Builder> configure) {

    var config = ModbusClientConfig.create(configure);

    return new ModbusTcpClient(config, transport);
  }

  private interface ResponsePromiseFactory {

    ResponsePromise create(CompletableFuture<ByteBuffer> future, TimeoutHandle timeout);
  }

  private sealed interface ResponsePromise permits RawResponsePromise, ModbusResponsePromise {

    CompletableFuture<ByteBuffer> future();

    TimeoutHandle timeout();

    void complete(ByteBuffer buffer);
  }

  private record RawResponsePromise(CompletableFuture<ByteBuffer> future, TimeoutHandle timeout)
      implements ResponsePromise {

    @Override
    public void complete(ByteBuffer buffer) {
      future.complete(buffer);
    }
  }

  private record ModbusResponsePromise(
      int functionCode, CompletableFuture<ByteBuffer> future, TimeoutHandle timeout)
      implements ResponsePromise {

    @Override
    public void complete(ByteBuffer buffer) {
      int responseFunctionCode = buffer.get(buffer.position()) & 0xFF;

      if (responseFunctionCode == functionCode) {
        future.complete(buffer);
      } else if (responseFunctionCode == functionCode + 0x80) {
        if (buffer.remaining() >= 2) {
          buffer.get(); // skip FC byte
          int exceptionCode = buffer.get() & 0xFF;

          future.completeExceptionally(new ModbusResponseException(functionCode, exceptionCode));
        } else {
          future.completeExceptionally(
              new ModbusException(
                  "malformed exception response PDU: %s".formatted(Hex.format(buffer))));
        }
      } else {
        future.completeExceptionally(
            new ModbusException(
                "unexpected function code: 0x%02X".formatted(responseFunctionCode)));
      }
    }
  }

  public interface TransactionSequence {

    /**
     * Return the next 2-byte transaction identifier. Range is [0, 65535] by default.
     *
     * <p>Implementations must be safe for use by multiple threads.
     *
     * @return the next 2-byte transaction identifier.
     */
    int next();
  }

  public static class DefaultTransactionSequence implements TransactionSequence {

    private final int low;
    private final int high;

    private final AtomicReference<Integer> transactionId = new AtomicReference<>(0);

    public DefaultTransactionSequence() {
      this(0, 65535);
    }

    public DefaultTransactionSequence(int low, int high) {
      this.low = low;
      this.high = high;

      transactionId.set(low);
    }

    @Override
    public int next() {
      while (true) {
        Integer id = transactionId.get();
        Integer nextId = id >= high ? low : id + 1;

        if (transactionId.compareAndSet(id, nextId)) {
          return id;
        }
      }
    }
  }
}
