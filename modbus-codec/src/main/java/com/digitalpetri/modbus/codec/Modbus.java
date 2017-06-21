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

package com.digitalpetri.modbus.codec;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    /**
     * Shutdown/stop any shared resources that me be in use, blocking until finished or interrupted.
     *
     * @param timeout the duration to wait.
     * @param unit    the {@link TimeUnit} of the {@code timeout} duration.
     */
    public static void releaseSharedResources(long timeout, TimeUnit unit) throws InterruptedException {
        sharedExecutor().awaitTermination(timeout, unit);
        sharedEventLoop().shutdownGracefully().await(timeout, unit);
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
