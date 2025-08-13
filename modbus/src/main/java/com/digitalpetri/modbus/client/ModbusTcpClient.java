package com.digitalpetri.modbus.client;

import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.TimeoutScheduler.TimeoutHandle;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.ModbusTimeoutException;
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

  public CompletionStage<byte[]> sendRawAsync(int unitId, byte[] pduBytes) {
    CompletionStage<ByteBuffer> cs = sendBufferAsync(unitId, ByteBuffer.wrap(pduBytes));

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

    CompletionStage<ByteBuffer> cs = sendBufferAsync(unitId, pduBytes.flip());

    return cs.thenApply(
        buffer -> {
          try {
            ModbusPdu decoded =
                config.responseSerializer().decode(request.getFunctionCode(), buffer);
            return (ModbusResponsePdu) decoded;
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        });
  }

  private CompletionStage<ByteBuffer> sendBufferAsync(int unitId, ByteBuffer buffer) {
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
                    promise.future.completeExceptionally(
                        new TimeoutException(
                            "request timed out after %sms".formatted(timeoutMillis)));
                  }
                },
                timeoutMillis,
                TimeUnit.MILLISECONDS);

    var pending = new ResponsePromise(buffer.get(0) & 0xFF, new CompletableFuture<>(), timeout);

    promises.put(header.transactionId(), pending);

    transport
        .send(new ModbusTcpFrame(header, buffer))
        .whenComplete(
            (v, ex) -> {
              if (ex != null) {
                ResponsePromise promise = promises.remove(header.transactionId());
                if (promise != null) {
                  promise.timeout.cancel();
                  promise.future.completeExceptionally(ex);
                }
              }
            });

    return pending.future;
  }

  private void onFrameReceived(ModbusTcpFrame frame) {
    MbapHeader header = frame.header();
    ResponsePromise promise = promises.remove(header.transactionId());

    if (promise != null) {
      promise.timeout.cancel();

      ByteBuffer buffer = frame.pdu();

      if (buffer.remaining() == 0) {
        promise.future.completeExceptionally(new ModbusException("empty response PDU"));
        return;
      }

      int functionCode = buffer.get(buffer.position()) & 0xFF;

      if (functionCode == promise.functionCode) {
        try {
          promise.future.complete(buffer);
        } catch (Exception e) {
          promise.future.completeExceptionally(e);
        }
      } else if (functionCode == promise.functionCode + 0x80) {
        buffer.get(); // skip FC byte
        int exceptionCode = buffer.get() & 0xFF;

        promise.future.completeExceptionally(
            new ModbusResponseException(promise.functionCode, exceptionCode));
      } else {
        promise.future.completeExceptionally(
            new ModbusException("unexpected function code: 0x%02X".formatted(functionCode)));
      }
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

  /**
   * The promise of some future response PDU bytes and the function code of the originating request.
   *
   * @param functionCode the function code of the originating request.
   * @param future a {@link CompletableFuture} that completes successfully with the response PDU
   *     bytes, or completes exceptionally if no response is received.
   * @param timeout a {@link TimeoutHandle} handle to be cancelled when the response is received.
   */
  private record ResponsePromise(
      int functionCode, CompletableFuture<ByteBuffer> future, TimeoutHandle timeout) {}

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
