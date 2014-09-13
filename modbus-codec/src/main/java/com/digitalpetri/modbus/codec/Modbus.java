package com.digitalpetri.modbus.codec;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;

/**
 * Shared resources that if not otherwise provided can be used as defaults.
 * <p>
 * These resources should be released when the JVM is shutting down or the ClassLoader that loaded us is unloaded.
 * See {@link #releaseSharedResources()}.
 */
public abstract class Modbus {

    private Modbus() {}

    /**
     * @return a shared {@link ExecutorService}.
     */
    public static ExecutorService sharedExecutor() {
        return ExecutorHolder.Executor;
    }

    /**
     * @return a shared {@link EventLoopGroup}.
     */
    public static EventLoopGroup sharedEventLoop() {
        return EventLoopHolder.EventLoop;
    }

    /**
     * @return a shared {@link HashedWheelTimer}.
     */
    public static HashedWheelTimer sharedWheelTimer() {
        return WheelTimerHolder.WheelTimer;
    }

    /** Shutdown/stop any shared resources that may be in use. */
    public static void releaseSharedResources() {
        sharedExecutor().shutdown();
        sharedEventLoop().shutdownGracefully();
        sharedWheelTimer().stop();
    }

    private static class ExecutorHolder {
        private static final ExecutorService Executor = Executors.newWorkStealingPool();
    }

    private static class EventLoopHolder {
        private static final EventLoopGroup EventLoop = new NioEventLoopGroup();
    }

    private static class WheelTimerHolder {
        private static final HashedWheelTimer WheelTimer = new HashedWheelTimer();

        static {
            WheelTimer.start();
        }
    }

}
