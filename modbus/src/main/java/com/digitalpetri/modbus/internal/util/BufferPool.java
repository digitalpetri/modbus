package com.digitalpetri.modbus.internal.util;

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

public abstract class BufferPool implements AutoCloseable {

  private static final int QUEUE_SIZE = 3;

  private final Map<Integer, AtomicLong> allocationCounts = new ConcurrentHashMap<>();
  private final Map<Integer, AtomicLong> rejectionCounts = new ConcurrentHashMap<>();

  private final NavigableMap<Integer, Deque<ByteBuffer>> buffers = new ConcurrentSkipListMap<>();

  public void give(ByteBuffer buffer) {
    Deque<ByteBuffer> queue =
        buffers.computeIfAbsent(buffer.capacity(), k -> new LinkedBlockingDeque<>(QUEUE_SIZE));
    if (!queue.offer(buffer)) {
      rejectionCounts.computeIfAbsent(buffer.capacity(), k -> new AtomicLong()).incrementAndGet();
    }
  }

  public ByteBuffer take(int capacity) {
    var entry = buffers.ceilingEntry(capacity);

    if (entry != null) {
      Deque<ByteBuffer> queue = entry.getValue();
      ByteBuffer buffer = queue.poll();

      if (buffer != null) {
        return buffer.clear().limit(capacity);
      } else {
        return allocate(capacity);
      }
    } else {
      return allocate(capacity);
    }
  }

  @Override
  public void close() {
    buffers.clear();
    allocationCounts.clear();
    rejectionCounts.clear();
  }

  public Map<Integer, AtomicLong> getAllocationCounts() {
    return allocationCounts;
  }

  public Map<Integer, AtomicLong> getRejectionCounts() {
    return rejectionCounts;
  }

  protected final ByteBuffer allocate(int capacity) {
    allocationCounts.computeIfAbsent(capacity, k -> new AtomicLong()).incrementAndGet();
    return create(capacity);
  }

  protected abstract ByteBuffer create(int capacity);

  public static class HeapBufferPool extends BufferPool {

    @Override
    protected ByteBuffer create(int capacity) {
      return ByteBuffer.allocate(capacity);
    }

    @Override
    public void give(ByteBuffer buffer) {
      assert !buffer.isDirect();
      super.give(buffer);
    }
  }

  public static class DirectBufferPool extends BufferPool {

    @Override
    protected ByteBuffer create(int capacity) {
      return ByteBuffer.allocateDirect(capacity);
    }

    @Override
    public void give(ByteBuffer buffer) {
      assert buffer.isDirect();
      super.give(buffer);
    }
  }

  public static class NoOpBufferPool extends BufferPool {

    @Override
    protected ByteBuffer create(int capacity) {
      return ByteBuffer.allocate(capacity);
    }

    @Override
    public void give(ByteBuffer buffer) {}

    @Override
    public ByteBuffer take(int capacity) {
      return allocate(capacity);
    }
  }
}
