package com.digitalpetri.modbus.serial.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * A pseudo-terminal opened by a Python helper process. A serial transport opens the slave side at
 * {@link #path()}; the test writes and reads the master side as the RTU client.
 */
final class PseudoTerminal implements AutoCloseable {

  private static final String PTY_HELPER =
      """
      import os, pty, select, sys, time, tty
      master, slave = pty.openpty()
      tty.setraw(slave)
      print(os.ttyname(slave), flush=True)
      for line in sys.stdin:
          command, argument = line.strip().split()
          if command == 'W':
              data = bytes.fromhex(argument)
              while data:
                  data = data[os.write(master, data):]
              print('OK', flush=True)
          elif command == 'R':
              count = int(argument)
              data = b''
              deadline = time.monotonic() + (5 if count else 0.15)
              while time.monotonic() < deadline:
                  if not select.select([master], [], [], max(0, deadline - time.monotonic()))[0]:
                      break
                  data += os.read(master, max(1, count - len(data)) if count else 256)
                  if count and len(data) >= count:
                      break
              print(data.hex(), flush=True)
      """;

  private final Process helper;
  private final BufferedReader helperOut;
  private final PrintWriter helperIn;
  private final String path;

  private PseudoTerminal(Process helper) throws IOException {
    this.helper = helper;
    helperOut =
        new BufferedReader(new InputStreamReader(helper.getInputStream(), StandardCharsets.UTF_8));
    helperIn =
        new PrintWriter(
            new OutputStreamWriter(helper.getOutputStream(), StandardCharsets.UTF_8), true);
    path = helperOut.readLine();
    assertNotNull(path, "helper did not open a pseudo-terminal");
    assertTrue(path.startsWith("/dev/pts/"), path);
  }

  /**
   * Open a pseudo-terminal, skipping the test if python3 is not available.
   *
   * @return the opened pseudo-terminal.
   */
  static PseudoTerminal open() throws IOException {
    Process helper;
    try {
      helper =
          new ProcessBuilder("python3", "-u", "-c", PTY_HELPER).redirectErrorStream(true).start();
    } catch (IOException e) {
      assumeTrue(false, "python3 not available: " + e.getMessage());
      throw e;
    }
    return new PseudoTerminal(helper);
  }

  /** The path of the slave side, for the serial transport to open. */
  String path() {
    return path;
  }

  /** Write {@code data} to the master side in a single write. */
  void write(byte[] data) throws IOException {
    helperIn.println("W " + HexFormat.of().formatHex(data));
    assertEquals("OK", helperOut.readLine());
  }

  /**
   * Read {@code count} bytes from the master side, waiting up to 5 seconds, or, when {@code count}
   * is 0, whatever arrives within 150 ms.
   */
  byte[] read(int count) throws IOException {
    helperIn.println("R " + count);
    String response = helperOut.readLine();
    assertNotNull(response, "helper exited before reading a response");
    return HexFormat.of().parseHex(response);
  }

  @Override
  public void close() throws InterruptedException {
    helper.destroy();
    if (!helper.waitFor(5, TimeUnit.SECONDS)) {
      helper.destroyForcibly();
      assertTrue(helper.waitFor(5, TimeUnit.SECONDS), "helper did not stop");
    }
  }
}
