package com.digitalpetri.modbus;

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

  static TimeoutScheduler fromScheduledExecutor(ScheduledExecutorService ses) {
    return (task, delay, unit) -> {
      final var ref = new AtomicReference<ScheduledFuture<?>>();

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
        ref.set(ses.schedule(() -> task.run(handle), delay, unit));
      }

      return handle;
    };
  }

}
