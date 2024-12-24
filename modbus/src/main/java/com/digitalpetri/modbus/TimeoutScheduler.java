package com.digitalpetri.modbus;

import com.digitalpetri.modbus.internal.util.ExecutionQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public interface TimeoutScheduler {

  TimeoutHandle newTimeout(Task task, long delay, TimeUnit unit);

  interface Task {

    void run(TimeoutHandle handle);

  }

  interface TimeoutHandle {

    void cancel();

    boolean isCancelled();

  }

  static TimeoutScheduler create(Executor executor, ScheduledExecutorService scheduledExecutor) {
    return (task, delay, unit) -> {
      final var ref = new AtomicReference<ScheduledFuture<?>>();
      final ExecutionQueue queue = new ExecutionQueue(executor);

      var handle = new TimeoutHandle() {
        @Override
        public void cancel() {
          synchronized (ref) {
            ref.get().cancel(false);
          }
        }

        @Override
        public boolean isCancelled() {
          synchronized (ref) {
            return ref.get().isCancelled();
          }
        }
      };

      synchronized (ref) {
        ScheduledFuture<?> future = scheduledExecutor.schedule(
            () -> queue.submit(() -> task.run(handle)),
            delay,
            unit
        );
        ref.set(future);
      }

      return handle;
    };
  }

}
