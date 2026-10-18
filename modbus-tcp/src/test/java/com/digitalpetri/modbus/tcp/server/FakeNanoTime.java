package com.digitalpetri.modbus.tcp.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * A time source that only moves when a test advances it.
 *
 * <p>Server transports read the time once per chunk of received bytes, so a test can wait for a
 * chunk to be received before advancing the time.
 */
class FakeNanoTime implements LongSupplier {

  private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
  private final Semaphore reads = new Semaphore(0);

  @Override
  public long getAsLong() {
    long now = nanos.get();
    reads.release();
    return now;
  }

  /** Advance the time by {@code duration}. */
  void advance(Duration duration) {
    nanos.addAndGet(duration.toNanos());
  }

  /** Wait until the time has been read once more, i.e. until a transport received a chunk. */
  void awaitRead() throws InterruptedException {
    assertTrue(reads.tryAcquire(5, TimeUnit.SECONDS), "transport did not receive bytes");
  }
}
