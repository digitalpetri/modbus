package com.digitalpetri.modbus.master.fsm;

import java.util.concurrent.CompletableFuture;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import io.netty.channel.Channel;

public class Disconnected implements ConnectionState {

    private final CompletableFuture<Channel> channelFuture = new CompletableFuture<>();

    public Disconnected() {
        channelFuture.completeExceptionally(new Exception("not connected"));
    }

    @Override
    public ConnectionState transition(ConnectionEvent event, StateContext context) {
        switch (event) {
            case ConnectRequested:
                CompletableFuture<Channel> bootstrap =
                        ModbusTcpMaster.bootstrap(context.getMaster(), context.getConfig());

                bootstrap.whenCompleteAsync((ch, ex) -> {
                    if (ch != null) context.handleEvent(ConnectionEvent.ChannelOpenSuccess);
                    else context.handleEvent(ConnectionEvent.ChannelOpenFailure);
                }, context.getConfig().getExecutor());

                return new Connecting(bootstrap);

            default:
                return context.getState();
        }
    }

    @Override
    public CompletableFuture<Channel> channelFuture() {
        return channelFuture;
    }

}
