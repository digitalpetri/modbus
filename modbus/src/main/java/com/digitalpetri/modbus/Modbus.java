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

package com.digitalpetri.modbus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;

/**
 * Shared resources that, if not otherwise provided, can be used as defaults.
 *
 * <p>These resources should be released when the JVM is shutting down or the ClassLoader that
 * loaded this library is unloaded.
 *
 * <p>See {@link #releaseSharedResources()}.
 */
public final class Modbus {

  private Modbus() {}

  private static ExecutorService EXECUTOR_SERVICE;
  private static ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE;

  /**
   * @return a shared {@link ExecutorService}.
   */
  public static synchronized ExecutorService sharedExecutor() {
    if (EXECUTOR_SERVICE == null) {
      ThreadFactory threadFactory =
          new ThreadFactory() {
            private final AtomicLong threadNumber = new AtomicLong(0L);

            @Override
            public Thread newThread(Runnable r) {
              Thread thread =
                  new Thread(r, "modbus-shared-thread-pool-" + threadNumber.getAndIncrement());
              thread.setDaemon(true);
              thread.setUncaughtExceptionHandler(
                  (t, e) ->
                      LoggerFactory.getLogger(Modbus.class)
                          .warn("Uncaught Exception on shared stack ExecutorService thread", e));
              return thread;
            }
          };

      EXECUTOR_SERVICE = Executors.newCachedThreadPool(threadFactory);
    }

    return EXECUTOR_SERVICE;
  }

  /**
   * @return a shared {@link ScheduledExecutorService}.
   */
  public static synchronized ScheduledExecutorService sharedScheduledExecutor() {
    if (SCHEDULED_EXECUTOR_SERVICE == null) {
      ThreadFactory threadFactory =
          new ThreadFactory() {
            private final AtomicLong threadNumber = new AtomicLong(0L);

            @Override
            public Thread newThread(Runnable r) {
              Thread thread =
                  new Thread(
                      r, "modbus-shared-scheduled-executor-" + threadNumber.getAndIncrement());
              thread.setDaemon(true);
              thread.setUncaughtExceptionHandler(
                  (t, e) ->
                      LoggerFactory.getLogger(Modbus.class)
                          .warn(
                              "Uncaught Exception on shared stack ScheduledExecutorService thread",
                              e));
              return thread;
            }
          };

      var executor = new ScheduledThreadPoolExecutor(1, threadFactory);

      executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

      SCHEDULED_EXECUTOR_SERVICE = executor;
    }

    return SCHEDULED_EXECUTOR_SERVICE;
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
    if (EXECUTOR_SERVICE != null) {
      EXECUTOR_SERVICE.shutdown();
    }

    if (SCHEDULED_EXECUTOR_SERVICE != null) {
      SCHEDULED_EXECUTOR_SERVICE.shutdown();
    }

    if (EXECUTOR_SERVICE != null) {
      try {
        if (!EXECUTOR_SERVICE.awaitTermination(timeout, unit)) {
          LoggerFactory.getLogger(Modbus.class)
              .warn("ExecutorService not shut down after {} {}.", timeout, unit);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LoggerFactory.getLogger(Modbus.class)
            .warn("Interrupted awaiting executor service shutdown", e);
      }
      EXECUTOR_SERVICE = null;
    }

    if (SCHEDULED_EXECUTOR_SERVICE != null) {
      try {
        if (!SCHEDULED_EXECUTOR_SERVICE.awaitTermination(timeout, unit)) {
          LoggerFactory.getLogger(Modbus.class)
              .warn("ScheduledExecutorService not shut down after {} {}.", timeout, unit);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LoggerFactory.getLogger(Modbus.class)
            .warn("Interrupted awaiting scheduled executor service shutdown", e);
      }
      SCHEDULED_EXECUTOR_SERVICE = null;
    }
  }
}
