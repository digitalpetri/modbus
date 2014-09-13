package com.digitalpetri.modbus.master;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import com.digitalpetri.modbus.codec.Modbus;
import io.netty.channel.EventLoopGroup;
import io.netty.util.HashedWheelTimer;

public class ModbusTcpMasterConfig {

    private final String address;
    private final int port;
    private final Duration timeout;
    private final boolean autoConnect;
    private final Optional<String> instanceId;
    private final ExecutorService executor;
    private final EventLoopGroup eventLoop;
    private final HashedWheelTimer wheelTimer;

    public ModbusTcpMasterConfig(String address,
                                 int port,
                                 Duration timeout,
                                 boolean autoConnect,
                                 Optional<String> instanceId,
                                 ExecutorService executor,
                                 EventLoopGroup eventLoop,
                                 HashedWheelTimer wheelTimer) {
        this.address = address;
        this.port = port;
        this.timeout = timeout;
        this.autoConnect = autoConnect;
        this.instanceId = instanceId;
        this.executor = executor;
        this.eventLoop = eventLoop;
        this.wheelTimer = wheelTimer;
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

    public boolean isAutoConnect() {
        return autoConnect;
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

        private final String address;

        private int port = 502;
        private Duration timeout = Duration.ofSeconds(5);
        private boolean autoConnect = true;
        private Optional<String> instanceId = Optional.empty();
        private ExecutorService executor;
        private EventLoopGroup eventLoop;
        private HashedWheelTimer wheelTimer;

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

        public Builder setAutoConnect(boolean autoConnect) {
            this.autoConnect = autoConnect;
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

        public Builder setEventLoop(EventLoopGroup eventLoop) {
            this.eventLoop = eventLoop;
            return this;
        }

        public Builder setWheelTimer(HashedWheelTimer wheelTimer) {
            this.wheelTimer = wheelTimer;
            return this;
        }

        public ModbusTcpMasterConfig build() {
            return new ModbusTcpMasterConfig(
                    address,
                    port,
                    timeout,
                    autoConnect,
                    instanceId,
                    executor != null ? executor : Modbus.sharedExecutor(),
                    eventLoop != null ? eventLoop : Modbus.sharedEventLoop(),
                    wheelTimer != null ? wheelTimer : Modbus.sharedWheelTimer()
            );
        }

    }
}
