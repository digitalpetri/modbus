package com.digitalpetri.modbus.serial.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterResponse;
import com.digitalpetri.modbus.serial.SerialPortTransportConfig;
import com.digitalpetri.modbus.server.ModbusRequestContext;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusRtuRequestContext;
import com.digitalpetri.modbus.server.ModbusRtuServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Exercises the serial transport using a pseudo-terminal's master side as the RTU client. */
@EnabledOnOs(OS.LINUX)
@Timeout(20)
class SerialPortServerBroadcastTest {

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

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final BlockingQueue<Integer> processed = new LinkedBlockingQueue<>();
  private Process helper;
  private BufferedReader helperOut;
  private PrintWriter helperIn;
  private TestTransport transport;

  @BeforeEach
  void setUp() throws Exception {
    try {
      helper =
          new ProcessBuilder("python3", "-u", "-c", PTY_HELPER).redirectErrorStream(true).start();
    } catch (IOException e) {
      assumeTrue(false, "python3 not available: " + e.getMessage());
    }
    helperOut =
        new BufferedReader(new InputStreamReader(helper.getInputStream(), StandardCharsets.UTF_8));
    helperIn =
        new PrintWriter(
            new OutputStreamWriter(helper.getOutputStream(), StandardCharsets.UTF_8), true);
    String path = helperOut.readLine();
    assertNotNull(path, "helper did not open a pseudo-terminal");
    assertTrue(path.startsWith("/dev/pts/"), path);
    transport =
        new TestTransport(
            SerialPortTransportConfig.create(b -> b.setSerialPort(path).setExecutor(executor)));
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
      if (transport != null) {
        transport.unbind().toCompletableFuture().get(5, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
      if (helper != null) {
        helper.destroy();
        if (!helper.waitFor(5, TimeUnit.SECONDS)) {
          helper.destroyForcibly();
          assertTrue(helper.waitFor(5, TimeUnit.SECONDS), "helper did not stop");
        }
      }
    }
  }

  @Test
  void serverAppliesBroadcastWritesAndRemainsSilentUntilUnicast() throws Exception {
    var image = new ProcessImage();
    var services =
        new ReadWriteModbusServices() {
          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            return Optional.of(image);
          }

          @Override
          public WriteSingleRegisterResponse writeSingleRegister(
              ModbusRequestContext context, int unitId, WriteSingleRegisterRequest request)
              throws UnknownUnitIdException {
            if (request.address() == 11) {
              throw new UnknownUnitIdException(unitId);
            }
            return super.writeSingleRegister(context, unitId, request);
          }
        };
    ModbusRtuServer.create(transport, services).start();

    send("0006000a1234a56e");
    assertSilent();
    assertArrayEquals(
        new byte[] {0x12, 0x34}, image.get(tx -> tx.readHoldingRegisters(m -> m.get(10))));

    send("0006000b5678c65b");
    assertSilent();
    for (String frame :
        new String[] {
          "0001000a0001dc19",
          "0002000a00019819",
          "0003000a0001a5d9",
          "0004000a00011019",
          "0017000a000100140001021234786c"
        }) {
      send(frame);
      assertSilent();
    }
    assertEquals(1, image.<Integer>get(tx -> tx.readHoldingRegisters(Map::size)).intValue());

    send("0103000a0001a408");
    assertArrayEquals(hex("0103021234b533"), read(7));
    assertSilent();
  }

  @Test
  void transportSuppressesReturnedBroadcastFramesAndNullResponses() throws Exception {
    transport.receive(
        (context, frame) -> {
          if (frame.unitId() == 1) {
            return null;
          }
          if (frame.pdu().get(2) == 11) {
            return new ModbusRtuFrame(
                0, ByteBuffer.wrap(new byte[] {(byte) 0x86, 2}), ByteBuffer.allocate(2));
          }
          return frame;
        });
    transport.bind().toCompletableFuture().get(5, TimeUnit.SECONDS);

    for (String frame : new String[] {"0006000a1234a56e", "0006000b5678c65b", "0106000a1234a4bf"}) {
      send(frame);
      assertSilent();
    }
    send("0206000a1234a48c");
    assertArrayEquals(hex("0206000a1234a48c"), read(8));
    assertSilent();
  }

  private void send(String frame) throws Exception {
    helperIn.println("W " + frame);
    assertEquals("OK", helperOut.readLine());
    assertNotNull(processed.poll(5, TimeUnit.SECONDS), "request was not processed");
  }

  private byte[] read(int count) throws IOException {
    helperIn.println("R " + count);
    String response = helperOut.readLine();
    assertNotNull(response, "helper exited before reading a response");
    return hex(response);
  }

  private void assertSilent() throws IOException {
    assertArrayEquals(new byte[0], read(0));
  }

  private static byte[] hex(String value) {
    return HexFormat.of().parseHex(value);
  }

  private class TestTransport extends SerialPortServerTransport {

    TestTransport(SerialPortTransportConfig config) {
      super(config);
    }

    @Override
    public void receive(FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame> receiver) {
      super.receive(
          (context, frame) -> {
            try {
              return receiver.receive(context, frame);
            } finally {
              processed.add(frame.unitId());
            }
          });
    }
  }
}
