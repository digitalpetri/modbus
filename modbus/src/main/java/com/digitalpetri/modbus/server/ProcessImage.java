package com.digitalpetri.modbus.server;

import com.digitalpetri.modbus.internal.util.Hex;
import com.digitalpetri.modbus.server.ProcessImage.Modification.CoilModification;
import com.digitalpetri.modbus.server.ProcessImage.Modification.DiscreteInputModification;
import com.digitalpetri.modbus.server.ProcessImage.Modification.HoldingRegisterModification;
import com.digitalpetri.modbus.server.ProcessImage.Modification.InputRegisterModification;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class ProcessImage {

  private static final ThreadLocal<Object> IN_TRANSACTION = new ThreadLocal<>();

  private final ReadWriteLock exclusiveLock = new ReentrantReadWriteLock();

  private final ReadWriteLock coilLock = new ReentrantReadWriteLock();
  private final Map<Integer, Boolean> coilMap = new HashMap<>();

  private final ReadWriteLock discreteInputLock = new ReentrantReadWriteLock();
  private final Map<Integer, Boolean> discreteInputMap = new HashMap<>();

  private final ReadWriteLock holdingRegisterLock = new ReentrantReadWriteLock();
  private final Map<Integer, byte[]> holdingRegisterMap = new HashMap<>();

  private final ReadWriteLock inputRegisterLock = new ReentrantReadWriteLock();
  private final Map<Integer, byte[]> inputRegisterMap = new HashMap<>();

  private final List<ModificationListener> modificationListeners = new CopyOnWriteArrayList<>();

  /**
   * Perform an action using a {@link Transaction} that does not return value.
   *
   * <p>Transactions are only valid during the scope of the provided action.
   *
   * @param action an action to perform using the provided {@link Transaction}. The Transaction is
   *     only valid during the scope of this action.
   */
  public void with(Consumer<Transaction> action) {
    with(false, action);
  }

  /**
   * Perform an action using a {@link Transaction} that does not return value.
   *
   * <p>Transactions are only valid during the scope of the provided action.
   *
   * @param exclusive whether this Transaction should be exclusive, i.e. it is guaranteed to be the
   *     only Transaction running against the ProcessImage.
   * @param action an action to perform using the provided {@link Transaction}. The Transaction is
   *     only valid during the scope of this action.
   */
  public void with(boolean exclusive, Consumer<Transaction> action) {
    get(
        exclusive,
        tx -> {
          action.accept(tx);
          return null;
        });
  }

  /**
   * Perform an action using a {@link Transaction} that returns value.
   *
   * <p>Transactions are only valid during the scope of the provided action.
   *
   * @param action the action to perform using the provided {@link Transaction}. The Transaction is
   *     only valid during the scope of this action.
   * @param <T> the return type of the action.
   * @return the return value.
   */
  public <T> T get(Function<Transaction, T> action) {
    return get(false, action);
  }

  /**
   * Perform an action using a {@link Transaction} that returns value.
   *
   * <p>Transactions are only valid during the scope of the provided action.
   *
   * @param exclusive whether this Transaction should be exclusive, i.e. it is guaranteed to be the
   *     only Transaction running against the ProcessImage.
   * @param action the action to perform using the provided {@link Transaction}. The Transaction is
   *     only valid during the scope of this action.
   * @param <T> the return type of the action.
   * @return the return value.
   */
  public <T> T get(boolean exclusive, Function<Transaction, T> action) {
    if (IN_TRANSACTION.get() != null) {
      throw new IllegalStateException("nested transaction");
    } else {
      IN_TRANSACTION.set(new Object());
    }

    if (exclusive) {
      exclusiveLock.writeLock().lock();
    } else {
      exclusiveLock.readLock().lock();
    }
    try {
      try (var tx = new Transaction()) {
        return action.apply(tx);
      }
    } finally {
      if (exclusive) {
        exclusiveLock.writeLock().unlock();
      } else {
        exclusiveLock.readLock().unlock();
      }
      IN_TRANSACTION.remove();
    }
  }

  /**
   * Add a {@link ModificationListener} to be notified when the ProcessImage is modified.
   *
   * @param listener the listener to add.
   */
  public void addModificationListener(ModificationListener listener) {
    modificationListeners.add(listener);
  }

  /**
   * Remove a {@link ModificationListener}.
   *
   * @param listener the listener to remove.
   */
  public void removeModificationListener(ModificationListener listener) {
    modificationListeners.remove(listener);
  }

  public class Transaction implements AutoCloseable {

    private enum State {
      OPEN,
      CLOSED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);

    /**
     * Provide a function that reads from an unmodifiable view of the Coils in the ProcessImage.
     *
     * @param read the function that reads the Coils.
     * @param <T> the return type of the function.
     * @return the result of the read function.
     */
    public <T> T readCoils(Function<Map<Integer, Boolean>, T> read) {
      if (state.get() != State.OPEN) {
        throw new IllegalStateException("transaction closed");
      }

      coilLock.readLock().lock();
      try {
        return read.apply(Collections.unmodifiableMap(coilMap));
      } finally {
        coilLock.readLock().unlock();
      }
    }

    /**
     * Provide a function that reads from an unmodifiable view of the Discrete Inputs in the
     * ProcessImage.
     *
     * @param read the function that reads the Discrete Inputs.
     * @param <T> the return type of the function.
     * @return the result of the read function.
     */
    public <T> T readDiscreteInputs(Function<Map<Integer, Boolean>, T> read) {
      if (state.get() != State.OPEN) {
        throw new IllegalStateException("transaction closed");
      }

      discreteInputLock.readLock().lock();
      try {
        return read.apply(Collections.unmodifiableMap(discreteInputMap));
      } finally {
        discreteInputLock.readLock().unlock();
      }
    }

    /**
     * Provide a function that reads from an unmodifiable view of the Holding Registers in the
     * ProcessImage.
     *
     * @param read the function that reads the Holding Registers.
     * @param <T> the return type of the function.
     * @return the result of the read function.
     */
    public <T> T readHoldingRegisters(Function<Map<Integer, byte[]>, T> read) {
      if (state.get() != State.OPEN) {
        throw new IllegalStateException("transaction closed");
      }

      holdingRegisterLock.readLock().lock();
      try {
        return read.apply(Collections.unmodifiableMap(holdingRegisterMap));
      } finally {
        holdingRegisterLock.readLock().unlock();
      }
    }

    /**
     * Provide a function that reads from an unmodifiable view of the Input Registers in the
     * ProcessImage.
     *
     * @param read the function that reads the Input Registers.
     * @param <T> the return type of the function.
     * @return the result of the read function.
     */
    public <T> T readInputRegisters(Function<Map<Integer, byte[]>, T> read) {
      if (state.get() != State.OPEN) {
        throw new IllegalStateException("transaction closed");
      }

      inputRegisterLock.readLock().lock();
      try {
        return read.apply(Collections.unmodifiableMap(inputRegisterMap));
      } finally {
        inputRegisterLock.readLock().unlock();
      }
    }

    /**
     * Provide a callback that can write to mutable view of the Coils in the ProcessImage.
     *
     * @param write the callback that can write to the Coils.
     */
    public void writeCoils(Consumer<Map<Integer, Boolean>> write) {
      if (state.get() != State.OPEN) {
        throw new IllegalStateException("transaction closed");
      }

      var modifications = new ArrayList<CoilModification>();

      coilLock.writeLock().lock();
      try {
        write.accept(
            new TransactionScopedMap<>(coilMap, state) {
              @Override
              protected void recordPut(Integer key, Boolean value) {
                modifications.add(new CoilModification(key, value));
              }

              @Override
              protected void recordRemove(Object key) {
                if (key instanceof Integer k) {
                  modifications.add(new CoilModification(k, false));
                }
              }
            });

        notifyCoilsModified(modifications);
      } finally {
        coilLock.writeLock().unlock();
      }
    }

    /**
     * Provide a callback that can write to mutable view of the Discrete Inputs in the ProcessImage.
     *
     * @param write the callback that can write to the Discrete Inputs.
     */
    public void writeDiscreteInputs(Consumer<Map<Integer, Boolean>> write) {
      if (state.get() != State.OPEN) {
        throw new IllegalStateException("transaction closed");
      }

      var modifications = new ArrayList<DiscreteInputModification>();

      discreteInputLock.writeLock().lock();
      try {
        write.accept(
            new TransactionScopedMap<>(discreteInputMap, state) {
              @Override
              protected void recordPut(Integer key, Boolean value) {
                modifications.add(new DiscreteInputModification(key, value));
              }

              @Override
              protected void recordRemove(Object key) {
                if (key instanceof Integer k) {
                  modifications.add(new DiscreteInputModification(k, false));
                }
              }
            });

        notifyDiscreteInputsModified(modifications);
      } finally {
        discreteInputLock.writeLock().unlock();
      }
    }

    /**
     * Provide a callback that can write to mutable view of the Holding Registers in the
     * ProcessImage.
     *
     * @param write the callback that can write to the Holding Registers.
     */
    public void writeHoldingRegisters(Consumer<Map<Integer, byte[]>> write) {
      if (state.get() != State.OPEN) {
        throw new IllegalStateException("transaction closed");
      }

      var modifications = new ArrayList<HoldingRegisterModification>();

      holdingRegisterLock.writeLock().lock();
      try {
        write.accept(
            new TransactionScopedMap<>(holdingRegisterMap, state) {
              @Override
              protected void recordPut(Integer key, byte[] value) {
                modifications.add(new HoldingRegisterModification(key, value));
              }

              @Override
              protected void recordRemove(Object key) {
                if (key instanceof Integer k) {
                  modifications.add(new HoldingRegisterModification(k, new byte[2]));
                }
              }
            });

        notifyHoldingRegistersModified(modifications);
      } finally {
        holdingRegisterLock.writeLock().unlock();
      }
    }

    /**
     * Provide a callback that can write to mutable view of the Input Registers in the ProcessImage.
     *
     * @param write the callback that can write to the Input Registers.
     */
    public void writeInputRegisters(Consumer<Map<Integer, byte[]>> write) {
      if (state.get() != State.OPEN) {
        throw new IllegalStateException("transaction closed");
      }

      var modifications = new ArrayList<InputRegisterModification>();

      inputRegisterLock.writeLock().lock();
      try {
        write.accept(
            new TransactionScopedMap<>(inputRegisterMap, state) {
              @Override
              protected void recordPut(Integer key, byte[] value) {
                modifications.add(new InputRegisterModification(key, value));
              }

              @Override
              protected void recordRemove(Object key) {
                if (key instanceof Integer k) {
                  modifications.add(new InputRegisterModification(k, new byte[2]));
                }
              }
            });

        notifyInputRegistersModified(modifications);
      } finally {
        inputRegisterLock.writeLock().unlock();
      }
    }

    @Override
    public void close() {
      state.set(State.CLOSED);
    }

    private void notifyCoilsModified(List<CoilModification> modifications) {
      if (!modifications.isEmpty()) {
        modificationListeners.forEach(listener -> listener.onCoilsModified(modifications));
      }
    }

    private void notifyDiscreteInputsModified(List<DiscreteInputModification> modifications) {
      if (!modifications.isEmpty()) {
        modificationListeners.forEach(listener -> listener.onDiscreteInputsModified(modifications));
      }
    }

    private void notifyHoldingRegistersModified(List<HoldingRegisterModification> modifications) {
      if (!modifications.isEmpty()) {
        modificationListeners.forEach(
            listener -> listener.onHoldingRegistersModified(modifications));
      }
    }

    private void notifyInputRegistersModified(List<InputRegisterModification> modifications) {
      if (!modifications.isEmpty()) {
        modificationListeners.forEach(listener -> listener.onInputRegistersModified(modifications));
      }
    }

    private abstract static class TransactionScopedMap<K, V> extends AbstractMap<K, V> {

      private final Map<K, V> delegate;
      private final AtomicReference<State> state;

      public TransactionScopedMap(Map<K, V> delegate, AtomicReference<State> state) {
        this.delegate = delegate;
        this.state = state;
      }

      @Override
      public Set<Entry<K, V>> entrySet() {
        if (state.get() != State.OPEN) {
          throw new IllegalStateException("transaction closed");
        }
        return delegate.entrySet();
      }

      @Override
      public V put(K key, V value) {
        if (state.get() != State.OPEN) {
          throw new IllegalStateException("transaction closed");
        }
        try {
          return delegate.put(key, value);
        } finally {
          recordPut(key, value);
        }
      }

      @Override
      public V remove(Object key) {
        if (state.get() != State.OPEN) {
          throw new IllegalStateException("transaction closed");
        }
        try {
          return super.remove(key);
        } finally {
          recordRemove(key);
        }
      }

      protected abstract void recordPut(K key, V value);

      protected abstract void recordRemove(Object key);
    }
  }

  public sealed interface Modification
      permits CoilModification,
          DiscreteInputModification,
          HoldingRegisterModification,
          InputRegisterModification {

    record CoilModification(int address, boolean value) implements Modification {}

    record DiscreteInputModification(int address, boolean value) implements Modification {}

    record HoldingRegisterModification(int address, byte[] value) implements Modification {

      @Override
      public String toString() {
        return new StringJoiner(", ", HoldingRegisterModification.class.getSimpleName() + "[", "]")
            .add("address=" + address)
            .add("value=0x" + Hex.format(value))
            .toString();
      }
    }

    record InputRegisterModification(int address, byte[] value) implements Modification {

      @Override
      public String toString() {
        return new StringJoiner(", ", InputRegisterModification.class.getSimpleName() + "[", "]")
            .add("address=" + address)
            .add("value=0x" + Hex.format(value))
            .toString();
      }
    }
  }

  public interface ModificationListener {

    /**
     * Coils in the ProcessImage have been modified.
     *
     * <p>This callback is made while holding the write lock for the modified area. Consider
     * queueing and processing asynchronously if the listener needs to block.
     *
     * @param modifications the list of {@link CoilModification}s that were applied.
     */
    void onCoilsModified(List<CoilModification> modifications);

    /**
     * Discrete Inputs in the ProcessImage have been modified.
     *
     * <p>This callback is made while holding the write lock for the modified area. Consider
     * queueing and processing asynchronously if the listener needs to block.
     *
     * @param modifications the list of {@link DiscreteInputModification}s that were applied.
     */
    void onDiscreteInputsModified(List<DiscreteInputModification> modifications);

    /**
     * Holding Registers in the ProcessImage have been modified.
     *
     * <p>This callback is made while holding the write lock for the modified area. Consider
     * queueing and processing asynchronously if the listener needs to block.
     *
     * @param modifications the list of {@link HoldingRegisterModification}s that were applied.
     */
    void onHoldingRegistersModified(List<HoldingRegisterModification> modifications);

    /**
     * Input Registers in the ProcessImage have been modified.
     *
     * <p>This callback is made while holding the write lock for the modified area. Consider
     * queueing and processing asynchronously if the listener needs to block.
     *
     * @param modifications the list of {@link InputRegisterModification}s that were applied.
     */
    void onInputRegistersModified(List<InputRegisterModification> modifications);
  }
}
