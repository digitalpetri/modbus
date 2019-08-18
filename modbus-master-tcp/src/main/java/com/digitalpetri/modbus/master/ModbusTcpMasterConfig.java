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

package com.digitalpetri.modbus.master;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import com.digitalpetri.modbus.codec.Modbus;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.util.HashedWheelTimer;

public class ModbusTcpMasterConfig {

    private final String address;
    private final int port;
    private final Duration timeout;
    private final boolean lazy;
    private final boolean persistent;
    private final int maxReconnectDelay;
    private final Optional<String> instanceId;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final EventLoopGroup eventLoop;
    private final HashedWheelTimer wheelTimer;
    private final Consumer<Bootstrap> bootstrapConsumer;

    public ModbusTcpMasterConfig(
        String address,
        int port,
        Duration timeout,
        boolean lazy,
        boolean persistent,
        int maxReconnectDelay,
        Optional<String> instanceId,
        ExecutorService executor,
        ScheduledExecutorService scheduler,
        EventLoopGroup eventLoop,
        HashedWheelTimer wheelTimer,
        Consumer<Bootstrap> bootstrapConsumer
    ) {

        this.address = address;
        this.port = port;
        this.timeout = timeout;
        this.lazy = lazy;
        this.persistent = persistent;
        this.maxReconnectDelay = maxReconnectDelay;
        this.instanceId = instanceId;
        this.executor = executor;
        this.scheduler = scheduler;
        this.eventLoop = eventLoop;
        this.wheelTimer = wheelTimer;
        this.bootstrapConsumer = bootstrapConsumer;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isLazy() {
        return lazy;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public int getMaxReconnectDelaySeconds() {
        return maxReconnectDelay;
    }

    public Optional<String> getInstanceId() {
        return instanceId;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public EventLoopGroup getEventLoop() {
        return eventLoop;
    }

    public HashedWheelTimer getWheelTimer() {
        return wheelTimer;
    }

    public Consumer<Bootstrap> getBootstrapConsumer() {
        return bootstrapConsumer;
    }

    public static class Builder {

        private final String address;

        private int port = 502;
        private Duration timeout = Duration.ofSeconds(5);
        private boolean lazy = true;
        private boolean persistent = true;
        private int maxReconnectDelaySeconds = 16;
        private Optional<String> instanceId = Optional.empty();
        private ExecutorService executor;
        private ScheduledExecutorService scheduler;
        private EventLoopGroup eventLoop;
        private HashedWheelTimer wheelTimer;
        private Consumer<Bootstrap> bootstrapConsumer = (b) -> {};

        public Builder(String address) {
            this.address = address;
        }

        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        public Builder setTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder setLazy(boolean lazy) {
            this.lazy = lazy;
            return this;
        }

        public Builder setPersistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }

        public Builder setMaxReconnectDelaySeconds(int maxReconnectDelaySeconds) {
            this.maxReconnectDelaySeconds = maxReconnectDelaySeconds;
            return this;
        }

        public Builder setInstanceId(String instanceId) {
            this.instanceId = Optional.of(instanceId);
            return this;
        }

        public Builder setExecutor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public Builder setScheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
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

        public Builder setBootstrapConsumer(Consumer<Bootstrap> consumer) {
            this.bootstrapConsumer = consumer;
            return this;
        }

        public ModbusTcpMasterConfig build() {
            return new ModbusTcpMasterConfig(
                address,
                port,
                timeout,
                lazy,
                persistent,
                maxReconnectDelaySeconds,
                instanceId,
                executor != null ? executor : Modbus.sharedExecutor(),
                scheduler != null ? scheduler : Modbus.sharedScheduler(),
                eventLoop != null ? eventLoop : Modbus.sharedEventLoop(),
                wheelTimer != null ? wheelTimer : Modbus.sharedWheelTimer(),
                bootstrapConsumer
            );
        }

    }
}
