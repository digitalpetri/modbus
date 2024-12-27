package com.digitalpetri.modbus.tcp.client;

import com.digitalpetri.modbus.TimeoutScheduler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class NettyTimeoutScheduler implements TimeoutScheduler {

  private final HashedWheelTimer wheelTimer;

  public NettyTimeoutScheduler(HashedWheelTimer wheelTimer) {
    this.wheelTimer = wheelTimer;
  }

  @Override
  public TimeoutHandle newTimeout(Task task, long delay, TimeUnit unit) {
    final var ref = new AtomicReference<Timeout>();

    var handle =
        new TimeoutHandle() {
          @Override
          public void cancel() {
            synchronized (ref) {
              ref.get().cancel();
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
      ref.set(wheelTimer.newTimeout(timeout -> task.run(handle), delay, unit));
    }

    return handle;
  }
}
