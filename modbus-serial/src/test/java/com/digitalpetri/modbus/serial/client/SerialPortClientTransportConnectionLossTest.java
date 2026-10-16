package com.digitalpetri.modbus.serial.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.digitalpetri.modbus.client.ModbusRtuClient;
import com.digitalpetri.modbus.exceptions.ModbusConnectionLostException;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusNotConnectedException;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.fazecast.jSerialComm.SerialPort;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Tests that {@link SerialPortClientTransport} reports connection loss when its port goes away and
 * when it's disconnected.
 *
 * <p>A Python helper opens a pseudo-terminal and reports how many bytes have arrived on the master
 * side. When the helper exits it closes the master, and the slave side jSerialComm has open gets a
 * hangup, the same signal a serial port gives when its USB adapter is unplugged.
 */
@EnabledOnOs(OS.LINUX)
@Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
class SerialPortClientTransportConnectionLossTest {

  private static final String PTY_HELPER =
      """
      import os, pty, select, sys
      m, s = pty.openpty()
      print(os.ttyname(s), flush=True)
      total = 0
      while True:
          ready = select.select([m, sys.stdin], [], [])[0]
          if m in ready:
              total += len(os.read(m, 65536))
              print(total, flush=True)
          if sys.stdin in ready:
              break
      """;

  /** Unit ID + Read Holding Registers PDU + CRC. */
  private static final int REQUEST_LENGTH = 8;

  private Process helper;
  private BufferedReader helperOut;
  private OutputStream helperIn;
  private SerialPortClientTransport transport;
  private ModbusRtuClient client;

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

    String ptyPath = helperOut.readLine();
    assumeTrue(ptyPath != null, "failed to open pty");

    transport = SerialPortClientTransport.create(cfg -> cfg.setSerialPort(ptyPath));
    client =
        ModbusRtuClient.create(transport, cfg -> cfg.setRequestTimeout(Duration.ofSeconds(30)));
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
  void lostPortFailsPendingRequestBeforeItsTimeout() throws Exception {
    client.connect();

    var response = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));
    awaitBytesWritten(REQUEST_LENGTH);

    closeMaster();

    assertConnectionLost(response.toCompletableFuture());
    assertFalse(transport.isConnected());

    // The connection is down, so the next request is never sent.
    var next = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));
    assertNotConnected(next.toCompletableFuture());
  }

  @Test
  void synchronousCallWrapsLostPort() throws Exception {
    client.connect();

    CompletableFuture<ModbusExecutionException> failure =
        CompletableFuture.supplyAsync(
            () ->
                assertThrows(
                    ModbusExecutionException.class,
                    () -> client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 1))));
    awaitBytesWritten(REQUEST_LENGTH);

    closeMaster();

    assertInstanceOf(
        ModbusConnectionLostException.class, failure.get(5, TimeUnit.SECONDS).getCause());
  }

  @Test
  void lostPortIsReportedOnce() throws Exception {
    var captures = new AtomicInteger();
    var notified = new CompletableFuture<Void>();
    transport.onConnectionLost(
        () -> {
          captures.incrementAndGet();
          return () -> notified.complete(null);
        });
    transport.connect().get(5, TimeUnit.SECONDS);
    SerialPort port = transport.getSerialPort();

    closeMaster();

    notified.get(5, TimeUnit.SECONDS);
    assertFalse(transport.isConnected());

    // jSerialComm repeats the disconnect event until the port is closed. Wait for the transport
    // to close it, then check the loss wasn't reported again in the meantime.
    assertTrue(awaitPortClosed(port), "transport didn't close the lost port");
    assertEquals(1, captures.get());

    // An explicit disconnect after the loss doesn't report it again either.
    transport.disconnect().get(5, TimeUnit.SECONDS);
    assertEquals(1, captures.get());
  }

  @Test
  void explicitDisconnectFailsPendingRequest() throws Exception {
    client.connect();

    var response = client.readHoldingRegistersAsync(1, new ReadHoldingRegistersRequest(0, 1));
    awaitBytesWritten(REQUEST_LENGTH);

    client.disconnect();

    assertConnectionLost(response.toCompletableFuture());
  }

  @Test
  void disconnectCapturesBeforeReturning() throws Exception {
    var captured = new CompletableFuture<Void>();
    var notified = new CompletableFuture<Void>();
    transport.onConnectionLost(
        () -> {
          captured.complete(null);
          return () -> notified.complete(null);
        });
    transport.connect().get(5, TimeUnit.SECONDS);

    transport.disconnect().get(5, TimeUnit.SECONDS);

    assertTrue(captured.isDone(), "disconnect() returned before capturing affected requests");
    notified.get(5, TimeUnit.SECONDS);
  }

  @Test
  void disconnectWithoutConnectionDoesNotReportLoss() throws Exception {
    var captures = new AtomicInteger();
    transport.onConnectionLost(
        () -> {
          captures.incrementAndGet();
          return () -> {};
        });

    transport.disconnect().get(5, TimeUnit.SECONDS);

    transport.connect().get(5, TimeUnit.SECONDS);
    transport.disconnect().get(5, TimeUnit.SECONDS);
    transport.disconnect().get(5, TimeUnit.SECONDS);

    assertEquals(1, captures.get());
  }

  /** Wait until the helper reports that at least {@code count} bytes arrived on the master. */
  private void awaitBytesWritten(int count) throws IOException {
    int total = 0;
    while (total < count) {
      String line = helperOut.readLine();
      assertTrue(line != null, "helper exited before " + count + " bytes were written");
      total = Integer.parseInt(line);
    }
  }

  /** Tell the helper to exit, which closes the pty master. */
  private void closeMaster() throws Exception {
    helperIn.write('\n');
    helperIn.flush();
    assertTrue(helper.waitFor(5, TimeUnit.SECONDS), "helper didn't exit");
  }

  /** Wait for the transport to close {@code port} after its loss. */
  private static boolean awaitPortClosed(SerialPort port) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      if (!port.isOpen()) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }

  private static void assertConnectionLost(CompletableFuture<?> response) {
    ExecutionException failure =
        assertThrows(ExecutionException.class, () -> response.get(5, TimeUnit.SECONDS));
    assertInstanceOf(ModbusConnectionLostException.class, failure.getCause());
  }

  private static void assertNotConnected(CompletableFuture<?> response) {
    ExecutionException failure =
        assertThrows(ExecutionException.class, () -> response.get(5, TimeUnit.SECONDS));
    assertInstanceOf(ModbusNotConnectedException.class, failure.getCause());
  }
}
