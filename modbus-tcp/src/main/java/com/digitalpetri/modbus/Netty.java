package com.digitalpetri.modbus;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;

public final class Netty {

  private static NioEventLoopGroup EVENT_LOOP;

  private static HashedWheelTimer WHEEL_TIMER;

  /**
   * @return a shared {@link NioEventLoopGroup}.
   */
  public static synchronized NioEventLoopGroup sharedEventLoop() {
    if (EVENT_LOOP == null) {
      ThreadFactory threadFactory = new ThreadFactory() {
        private final AtomicLong threadNumber = new AtomicLong(0L);

        @Override
        public Thread newThread(Runnable r) {
          Thread thread = new Thread(r,
              "modbus-netty-event-loop-" + threadNumber.getAndIncrement());
          thread.setDaemon(true);
          return thread;
        }
      };

      EVENT_LOOP = new NioEventLoopGroup(0, threadFactory);
    }

    return EVENT_LOOP;
  }


  /**
   * @return a shared {@link HashedWheelTimer}.
   */
  public static synchronized HashedWheelTimer sharedWheelTimer() {
    if (WHEEL_TIMER == null) {
      ThreadFactory threadFactory = r -> {
        Thread thread = new Thread(r, "modbus-netty-wheel-timer");
        thread.setDaemon(true);
        return thread;
      };

      WHEEL_TIMER = new HashedWheelTimer(threadFactory);
    }

    return WHEEL_TIMER;
  }

  /**
   * Release shared resources, waiting at most 5 seconds for each of the shared resources to shut
   * down gracefully.
   *
   * @see #releaseSharedResources(long, TimeUnit)
   */
  public static synchronized void releaseSharedResources() {
    releaseSharedResources(5, TimeUnit.SECONDS);
  }

  /**
   * Release shared resources, waiting at most the specified timeout for each of the shared
   * resources to shut down gracefully.
   *
   * @param timeout the duration of the timeout.
   * @param unit the unit of the timeout duration.
   */
  public static synchronized void releaseSharedResources(long timeout, TimeUnit unit) {
    if (EVENT_LOOP != null) {
      try {
        if (!EVENT_LOOP.shutdownGracefully().await(timeout, unit)) {
          LoggerFactory.getLogger(Netty.class)
              .warn("Event loop not shut down after {} {}.", timeout, unit);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LoggerFactory.getLogger(Netty.class)
            .warn("Interrupted awaiting event loop shutdown", e);
      }
      EVENT_LOOP = null;
    }

    if (WHEEL_TIMER != null) {
      WHEEL_TIMER.stop().forEach(Timeout::cancel);
      WHEEL_TIMER = null;
    }
  }

}
