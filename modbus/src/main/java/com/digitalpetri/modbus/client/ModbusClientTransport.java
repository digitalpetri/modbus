package com.digitalpetri.modbus.client;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

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

}
