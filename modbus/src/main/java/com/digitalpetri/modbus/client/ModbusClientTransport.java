package com.digitalpetri.modbus.client;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ModbusClientTransport<T> {

  /**
   * Connect this transport.
   *
   * @return a {@link CompletionStage} that completes when the transport has been connected.
   */
  CompletionStage<Void> connect();

  /**
   * Disconnect this transport.
   *
   * @return a {@link CompletionStage} that completes when the transport has been disconnected.
   */
  CompletionStage<Void> disconnect();

  /**
   * Check if the transport is connected.
   *
   * @return {@code true} if the transport is connected.
   */
  boolean isConnected();

  /**
   * Send a request frame to the transport.
   *
   * @param frame the request frame to send.
   * @return a {@link CompletionStage} that completes when the frame has been sent.
   */
  CompletionStage<Void> send(T frame);

  /**
   * Configure a callback to receive response frames from the transport.
   *
   * @param frameReceiver the callback to response receive frames.
   */
  void receive(Consumer<T> frameReceiver);

  /**
   * Configure a callback that captures requests affected by a lost or disconnected connection.
   *
   * <p>The transport invokes the supplier during the connection-state transition, before another
   * connection can be used. The supplier must return promptly and must not wait for transport
   * operations. It captures the affected requests and returns a task that the transport runs in
   * order with response delivery, after responses already queued before the loss. This prevents a
   * delayed notification from failing requests sent on a replacement connection.
   *
   * <p>The callback replaces any previously configured callback. It does not request reconnection
   * or indicate whether a request reached the device. Transports without connection-loss detection
   * may retain this default no-op.
   *
   * @param callback the supplier that captures affected requests and returns their cleanup task.
   */
  default void onConnectionLost(Supplier<Runnable> callback) {}
}
