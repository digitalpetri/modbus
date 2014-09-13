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
