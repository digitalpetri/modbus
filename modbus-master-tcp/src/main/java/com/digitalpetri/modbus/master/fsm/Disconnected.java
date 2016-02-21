/*
 * Copyright 2016 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
