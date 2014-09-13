package com.digitalpetri.modbus.master.fsm;

import java.util.concurrent.CompletableFuture;

import io.netty.channel.Channel;

public class Connecting implements ConnectionState {

    private final CompletableFuture<Channel> channelFuture;

    public Connecting(CompletableFuture<Channel> channelFuture) {
        this.channelFuture = channelFuture;
    }

    @Override
    public ConnectionState transition(ConnectionEvent event, StateContext context) {
        switch (event) {
            case ChannelOpenSuccess:
                return new Connected(channelFuture);

            case ChannelOpenFailure:
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
