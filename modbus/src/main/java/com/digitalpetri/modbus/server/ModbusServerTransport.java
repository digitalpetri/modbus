package com.digitalpetri.modbus.server;

import java.util.concurrent.CompletionStage;

public interface ModbusServerTransport<C extends ModbusRequestContext, T> {

  /**
   * Bind the transport to its configured local address.
   *
   * @return a {@link CompletionStage} that completes when the transport is bound.
   */
  CompletionStage<Void> bind();

  /**
   * Unbind the transport from its configured local address.
   *
   * @return a {@link CompletionStage} that completes when the transport is unbound.
   */
  CompletionStage<Void> unbind();

  /**
   * Configure a callback to receive request frames from the transport.
   *
   * @param frameReceiver the callback to receive request frames.
   */
  void receive(FrameReceiver<C, T> frameReceiver);

  interface FrameReceiver<C, T> {

    /**
     * Receive a request frame from the transport and respond to it.
     *
     * @param frame the request frame.
     * @return a corresponding response frame.
     * @throws Exception if there is an unrecoverable error and the channel should be closed.
     */
    T receive(C context, T frame) throws Exception;

  }

}
