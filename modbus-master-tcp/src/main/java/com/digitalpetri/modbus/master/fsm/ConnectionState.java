package com.digitalpetri.modbus.master.fsm;

import java.util.concurrent.CompletableFuture;

import io.netty.channel.Channel;

public interface ConnectionState {

    ConnectionState transition(ConnectionEvent event, StateContext context);

    /**
     * @return the {@code CompletableFuture} holding the {@link Channel} for this connection.
     */
    CompletableFuture<Channel> channelFuture();

}
