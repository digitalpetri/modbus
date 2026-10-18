package com.digitalpetri.modbus.serial.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.digitalpetri.modbus.serial.SerialPortTransportConfig;
import com.digitalpetri.modbus.server.ModbusRtuServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Partial request frame recovery for the serial RTU server, using a pseudo-terminal's master side
 * as the RTU client.
 */
@EnabledOnOs(OS.LINUX)
@Timeout(30)
class SerialPortServerFramingTest {

  /** Read holding register 0 of unit 1, and its response. */
  private static final byte[] READ_0 = hex("010300000001840a");

  private static final byte[] READ_0_RESPONSE = hex("0103020000b844");

  /** Read holding register 1 of unit 1, and its response. */
  private static final byte[] READ_1 = hex("010300010001d5ca");

  private static final byte[] READ_1_RESPONSE = hex("0103021234b533");

  /** A diagnostics request; the request parser does not support function code 0x08. */
  private static final byte[] DIAGNOSTICS = hex("010800000000e00b");

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final FakeNanoTime nanoTime = new FakeNanoTime();
  private PseudoTerminal pty;
  private SerialPortTransportConfig config;
  private ModbusRtuServer server;

  @BeforeEach
  void setUp() throws Exception {
    pty = PseudoTerminal.open();
    config =
        SerialPortTransportConfig.create(
            b -> b.setSerialPort(pty.path()).setBaudRate(9600).setExecutor(executor));
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
      if (server != null) {
        server.stop();
      }
    } finally {
      executor.shutdownNow();
      if (pty != null) {
        pty.close();
      }
    }
  }

  @Test
  void requestSplitByLessSilenceThanTimeoutIsAnswered() throws Exception {
    startServer(new SerialPortServerTransport(config, nanoTime));

    send(Arrays.copyOfRange(READ_0, 0, 1));
    nanoTime.advance(config.partialFrameTimeout().minusNanos(1));
    send(Arrays.copyOfRange(READ_0, 1, 8));

    assertArrayEquals(READ_0_RESPONSE, pty.read(7));
  }

  @Test
  void partialRequestIsDiscardedAfterTimeoutAndNextRequestIsAnswered() throws Exception {
    startServer(new SerialPortServerTransport(config, nanoTime));

    send(Arrays.copyOfRange(READ_0, 0, 1));
    nanoTime.advance(config.partialFrameTimeout());
    send(READ_1);

    assertArrayEquals(READ_1_RESPONSE, pty.read(7));
    assertSilent();
  }

  @Test
  void silenceIsMeasuredFromLastReceivedBytes() throws Exception {
    startServer(new SerialPortServerTransport(config, nanoTime));
    Duration partOfTimeout = config.partialFrameTimeout().multipliedBy(6).dividedBy(10);

    send(Arrays.copyOfRange(READ_1, 0, 1));
    nanoTime.advance(partOfTimeout);
    send(Arrays.copyOfRange(READ_1, 1, 2));
    nanoTime.advance(partOfTimeout);
    send(Arrays.copyOfRange(READ_1, 2, 8));

    assertArrayEquals(READ_1_RESPONSE, pty.read(7));
  }

  @Test
  void unparseableRequestIsDiscardedAfterTimeout() throws Exception {
    startServer(new SerialPortServerTransport(config, nanoTime));

    send(DIAGNOSTICS);
    nanoTime.awaitQuiet();
    nanoTime.advance(config.partialFrameTimeout());
    send(READ_1);

    assertArrayEquals(READ_1_RESPONSE, pty.read(7));
    assertSilent();
  }

  @Test
  void bytesAfterUnparseableRequestAreDiscardedUntilTimeout() throws Exception {
    startServer(new SerialPortServerTransport(config, nanoTime));

    send(DIAGNOSTICS);
    nanoTime.awaitQuiet();
    nanoTime.advance(config.partialFrameTimeout().minusNanos(1));
    send(READ_0);
    assertSilent();

    nanoTime.advance(config.partialFrameTimeout());
    send(READ_1);
    assertArrayEquals(READ_1_RESPONSE, pty.read(7));
    assertSilent();
  }

  @Test
  void oversizedBurstIsDiscarded() throws Exception {
    startServer(new SerialPortServerTransport(config, nanoTime));

    // More bytes than any request frame; the listener must keep receiving after them.
    send(new byte[300]);
    nanoTime.awaitQuiet();
    nanoTime.advance(config.partialFrameTimeout());
    send(READ_1);

    assertArrayEquals(READ_1_RESPONSE, pty.read(7));
    assertSilent();
  }

  @Test
  void restartedServerDoesNotKeepPartialRequest() throws Exception {
    startServer(new SerialPortServerTransport(config, nanoTime));
    send(Arrays.copyOfRange(READ_0, 0, 1));

    server.stop();
    server.start();

    send(READ_1);
    assertArrayEquals(READ_1_RESPONSE, pty.read(7));
  }

  @Test
  void defaultTimeoutRecoversWithSystemTime() throws Exception {
    startServer(new SerialPortServerTransport(config));

    pty.write(Arrays.copyOfRange(READ_0, 0, 1));
    Thread.sleep(100);
    pty.write(READ_1);

    assertArrayEquals(READ_1_RESPONSE, pty.read(7));
    assertSilent();
  }

  private void startServer(SerialPortServerTransport transport) throws Exception {
    var image = new ProcessImage();
    image.with(tx -> tx.writeHoldingRegisters(m -> m.put(1, new byte[] {0x12, 0x34})));
    var services =
        new ReadWriteModbusServices() {
          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            return Optional.of(image);
          }
        };
    server = ModbusRtuServer.create(transport, services);
    server.start();
  }

  /** Write {@code data} and wait until the server receives it. */
  private void send(byte[] data) throws Exception {
    pty.write(data);
    nanoTime.awaitRead();
  }

  private void assertSilent() throws Exception {
    assertArrayEquals(new byte[0], pty.read(0));
  }

  private static byte[] hex(String value) {
    return HexFormat.of().parseHex(value);
  }
}
