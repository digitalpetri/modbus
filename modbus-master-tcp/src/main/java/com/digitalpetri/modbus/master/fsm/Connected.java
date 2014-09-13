package com.digitalpetri.modbus.master.fsm;

import java.util.concurrent.CompletableFuture;

import io.netty.channel.Channel;

public class Connected implements ConnectionState {

    private final CompletableFuture<Channel> channelFuture;

    public Connected(CompletableFuture<Channel> channelFuture) {
        this.channelFuture = channelFuture;
    }

    @Override
    public ConnectionState transition(ConnectionEvent event, StateContext context) {
        switch (event) {
            case ChannelClosed:
                return new Disconnected();

            case DisconnectRequested:
                channelFuture.thenAccept(ch -> ch.close());
                return new Disconnected();

            default:
                return context.getState();
        }
    }

    @Override
    public CompletableFuture<Channel> channelFuture() {
        return channelFuture;
    }

}
