package com.digitalpetri.modbus.serial.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.exceptions.ModbusException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Tests {@link SerialPortClientTransport#send(ModbusRtuFrame)} when the serial port stops accepting
 * data.
 *
 * <p>A Python helper opens a pseudo-terminal and holds the master side open without reading it, so
 * writes to the slave side stall once the pty buffer fills. On request, it drains the master side
 * and reports how many bytes were written.
 */
@EnabledOnOs(OS.LINUX)
@Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
class SerialPortClientTransportWriteTest {

  private static final String PTY_HELPER =
      """
      import os, pty, select, sys
      m, s = pty.openpty()
      print(os.ttyname(s), flush=True)
      sys.stdin.readline()
      total = 0
      while select.select([m], [], [], 1.0)[0]:
          total += len(os.read(m, 65536))
      print(total, flush=True)
      sys.stdin.readline()
      """;

  private static final int PDU_LENGTH = 250;

  /** Unit ID + PDU + CRC. */
  private static final int FRAME_LENGTH = 1 + PDU_LENGTH + 2;

  private Process helper;
  private BufferedReader helperOut;
  private OutputStream helperIn;
  private String ptyPath;
  private SerialPortClientTransport transport;

  @BeforeEach
  void setUp() throws Exception {
    try {
      helper = new ProcessBuilder("python3", "-c", PTY_HELPER).start();
    } catch (IOException e) {
      assumeTrue(false, "python3 not available: " + e.getMessage());
    }
    helperOut =
        new BufferedReader(new InputStreamReader(helper.getInputStream(), StandardCharsets.UTF_8));
    helperIn = helper.getOutputStream();

    ptyPath = helperOut.readLine();
    assumeTrue(ptyPath != null, "failed to open pty");

    transport = SerialPortClientTransport.create(cfg -> cfg.setSerialPort(ptyPath));
    transport.connect().get(5, TimeUnit.SECONDS);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (transport != null) {
      transport.disconnect().get(5, TimeUnit.SECONDS);
    }
    if (helper != null) {
      helper.destroy();
    }
  }

  @Test
  void cancelledWriteIsSkipped() throws Exception {
    int stalledFrames = sendUntilStalled();

    CompletableFuture<Void> cancelled = send();
    CompletableFuture<Void> last = send();
    assertTrue(cancelled.cancel(false));

    int written = drain(last);

    assertEquals((stalledFrames + 1) * FRAME_LENGTH, written);
  }

  @Test
  void disconnectReleasesStalledWrite() throws Exception {
    sendUntilStalled();

    long start = System.nanoTime();
    List<CompletableFuture<Void>> queued = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      queued.add(send());
    }
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    assertTrue(elapsedMillis < 1000, "send() blocked for " + elapsedMillis + "ms");
    queued.forEach(f -> assertFalse(f.isDone()));

    transport.disconnect().get(5, TimeUnit.SECONDS);

    for (CompletableFuture<Void> f : queued) {
      ExecutionException e =
          assertThrows(ExecutionException.class, () -> f.get(5, TimeUnit.SECONDS));
      assertInstanceOf(ModbusException.class, e.getCause());
    }
  }

  @Test
  void writeQueuedBeforeReconnectIsNotSent() throws Exception {
    transport.disconnect().get(5, TimeUnit.SECONDS);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    var gate = new CountDownLatch(1);
    try {
      // Occupy the executor's only thread so the write stays queued until the gate opens.
      executor.execute(
          () -> {
            try {
              gate.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });

      transport =
          SerialPortClientTransport.create(cfg -> cfg.setSerialPort(ptyPath).setExecutor(executor));
      transport.connect().get(5, TimeUnit.SECONDS);

      CompletableFuture<Void> queued = send();

      transport.disconnect().get(5, TimeUnit.SECONDS);
      transport.connect().get(5, TimeUnit.SECONDS);
      gate.countDown();

      ExecutionException e =
          assertThrows(ExecutionException.class, () -> queued.get(5, TimeUnit.SECONDS));
      assertInstanceOf(ModbusException.class, e.getCause());
    } finally {
      gate.countDown();
      executor.shutdown();
    }
  }

  /**
   * Send frames until one doesn't complete, meaning the pty buffer is full and a write is stuck.
   *
   * @return the number of frames sent, including the stuck one.
   */
  private int sendUntilStalled() throws Exception {
    for (int i = 1; i <= 10_000; i++) {
      try {
        send().get(200, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        return i;
      }
    }
    throw new AssertionError("writes never stalled");
  }

  /**
   * Tell the helper to drain the pty, wait for {@code last} to be written, and return the total
   * number of bytes written to the pty.
   */
  private int drain(CompletableFuture<Void> last) throws Exception {
    helperIn.write('\n');
    helperIn.flush();

    last.get(5, TimeUnit.SECONDS);

    return Integer.parseInt(helperOut.readLine());
  }

  private CompletableFuture<Void> send() {
    var frame = new ModbusRtuFrame(1, ByteBuffer.allocate(PDU_LENGTH), ByteBuffer.allocate(2));
    return transport.send(frame).toCompletableFuture();
  }
}
