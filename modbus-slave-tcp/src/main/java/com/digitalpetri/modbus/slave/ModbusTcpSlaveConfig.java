/*
 * Copyright 2014 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.modbus.slave;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import com.digitalpetri.modbus.codec.Modbus;
import io.netty.channel.EventLoopGroup;
import io.netty.util.HashedWheelTimer;

public class ModbusTcpSlaveConfig {

    private final Optional<String> instanceId;
    private final ExecutorService executor;
    private final EventLoopGroup eventLoop;
    private final HashedWheelTimer wheelTimer;

    public ModbusTcpSlaveConfig(Optional<String> instanceId,
                                ExecutorService executor,
                                EventLoopGroup eventLoop,
                                HashedWheelTimer wheelTimer) {

        this.instanceId = instanceId;
        this.executor = executor;
        this.eventLoop = eventLoop;
        this.wheelTimer = wheelTimer;
    }

    public Optional<String> getInstanceId() {
        return instanceId;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public EventLoopGroup getEventLoop() {
        return eventLoop;
    }

    public HashedWheelTimer getWheelTimer() {
        return wheelTimer;
    }

    public static class Builder {
        private Optional<String> instanceId = Optional.empty();
        private ExecutorService executor;
        private EventLoopGroup eventLoop;
        private HashedWheelTimer wheelTimer;

        public Builder setInstanceId(String instanceId) {
            this.instanceId = Optional.of(instanceId);
            return this;
        }

        public Builder setExecutor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public Builder setEventLoop(EventLoopGroup eventLoop) {
            this.eventLoop = eventLoop;
            return this;
        }

        public Builder setWheelTimer(HashedWheelTimer wheelTimer) {
            this.wheelTimer = wheelTimer;
            return this;
        }

        public ModbusTcpSlaveConfig build() {
            return new ModbusTcpSlaveConfig(
                    instanceId,
                    executor != null ? executor : Modbus.sharedExecutor(),
                    eventLoop != null ? eventLoop : Modbus.sharedEventLoop(),
                    wheelTimer != null ? wheelTimer : Modbus.sharedWheelTimer()
            );
        }
    }
}
