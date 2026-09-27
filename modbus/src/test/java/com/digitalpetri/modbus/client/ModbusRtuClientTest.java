package com.digitalpetri.modbus.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusTimeoutException;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class ModbusRtuClientTest {

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
