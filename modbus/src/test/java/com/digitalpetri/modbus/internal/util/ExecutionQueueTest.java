package com.digitalpetri.modbus.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExecutionQueueTest {

  @Test
  void rejectedDispatchDoesNotLoseTasksOrStallQueue() throws Exception {
    ExecutorService delegate = Executors.newCachedThreadPool();
    try {
      // Reject the second execute() call. The first dispatches task 0; the second happens when
      // the queue hands off task 2 after running task 1 inline.
      var calls = new AtomicInteger();
      Executor executor =
          command -> {
            if (calls.incrementAndGet() == 2) {
              throw new RejectedExecutionException("rejected for test");
            }
            delegate.execute(command);
          };

      var queue = new ExecutionQueue(executor);
      List<Integer> ran = new CopyOnWriteArrayList<>();
      var release = new CountDownLatch(1);

      queue.submit(
          () -> {
            await(release);
            ran.add(0);
          });
      for (int i = 1; i <= 3; i++) {
        int n = i;
        queue.submit(() -> ran.add(n));
      }
      release.countDown();

      var last = new CountDownLatch(1);
      queue.submit(
          () -> {
            ran.add(4);
            last.countDown();
          });

      assertTrue(last.await(5, TimeUnit.SECONDS), "queue stalled; ran " + ran);
      assertEquals(List.of(0, 1, 2, 3, 4), ran);
      assertTrue(calls.get() >= 2, "rejection was not exercised");
    } finally {
      delegate.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
