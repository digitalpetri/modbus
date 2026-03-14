package com.digitalpetri.modbus.serial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.serial.client.SerialPortClientTransport;
import com.digitalpetri.modbus.serial.server.SerialPortServerTransport;
import com.fazecast.jSerialComm.SerialPort;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class Rs485ConfigTest {

  @Nested
  class ConfigDefaults {

    @Test
    void defaultRs485Parameters() {
      var config = SerialPortTransportConfig.create(cfg -> cfg.setSerialPort("/dev/tty"));

      assertFalse(config.rs485Mode());
      assertTrue(config.rs485RtsActiveHigh());
      assertFalse(config.rs485Termination());
      assertFalse(config.rs485RxDuringTx());
      assertEquals(0, config.rs485DelayBefore());
      assertEquals(0, config.rs485DelayAfter());
    }
  }

  @Nested
  class ConfigCustomValues {

    @Test
    void customRs485Parameters() {
      var config =
          SerialPortTransportConfig.create(
              cfg -> {
                cfg.setSerialPort("/dev/tty");
                cfg.setRs485Mode(true);
                cfg.setRs485RtsActiveHigh(false);
                cfg.setRs485Termination(true);
                cfg.setRs485RxDuringTx(true);
                cfg.setRs485DelayBefore(100);
                cfg.setRs485DelayAfter(200);
              });

      assertTrue(config.rs485Mode());
      assertFalse(config.rs485RtsActiveHigh());
      assertTrue(config.rs485Termination());
      assertTrue(config.rs485RxDuringTx());
      assertEquals(100, config.rs485DelayBefore());
      assertEquals(200, config.rs485DelayAfter());
    }
  }

  /**
   * Tests that verify the RS-485 parameters are applied to the {@link SerialPort} when RS-485 mode
   * is enabled in the transport configuration.
   *
   * <p>Uses reflection to inspect jSerialComm's private RS-485 fields since no public getters are
   * available.
   */
  @Nested
  class TransportRs485 {

    private static final String TEST_PORT = isWindows() ? "COM1" : "/dev/tty";

    @Test
    void clientTransportSetsRs485Parameters() throws Exception {
      var transport =
          SerialPortClientTransport.create(
              cfg -> {
                cfg.setSerialPort(TEST_PORT);
                cfg.setRs485Mode(true);
                cfg.setRs485RtsActiveHigh(false);
                cfg.setRs485Termination(true);
                cfg.setRs485RxDuringTx(true);
                cfg.setRs485DelayBefore(100);
                cfg.setRs485DelayAfter(200);
              });

      SerialPort sp = transport.getSerialPort();
      assertRs485Fields(sp, true, false, true, true, 100, 200);
    }

    @Test
    void serverTransportSetsRs485Parameters() throws Exception {
      var transport =
          SerialPortServerTransport.create(
              cfg -> {
                cfg.setSerialPort(TEST_PORT);
                cfg.setRs485Mode(true);
                cfg.setRs485RtsActiveHigh(false);
                cfg.setRs485Termination(true);
                cfg.setRs485RxDuringTx(true);
                cfg.setRs485DelayBefore(100);
                cfg.setRs485DelayAfter(200);
              });

      SerialPort sp = transport.getSerialPort();
      assertRs485Fields(sp, true, false, true, true, 100, 200);
    }

    @Test
    void clientTransportDoesNotSetRs485WhenDisabled() throws Exception {
      var transport =
          SerialPortClientTransport.create(
              cfg -> {
                cfg.setSerialPort(TEST_PORT);
                cfg.setRs485Mode(false);
                cfg.setRs485RtsActiveHigh(false);
                cfg.setRs485Termination(true);
                cfg.setRs485RxDuringTx(true);
                cfg.setRs485DelayBefore(100);
                cfg.setRs485DelayAfter(200);
              });

      SerialPort sp = transport.getSerialPort();
      // setRs485ModeParameters should NOT be called, so SerialPort fields stay at defaults
      assertRs485Fields(sp, false, true, false, false, 0, 0);
    }

    @Test
    void serverTransportDoesNotSetRs485WhenDisabled() throws Exception {
      var transport =
          SerialPortServerTransport.create(
              cfg -> {
                cfg.setSerialPort(TEST_PORT);
                cfg.setRs485Mode(false);
                cfg.setRs485RtsActiveHigh(false);
                cfg.setRs485Termination(true);
                cfg.setRs485RxDuringTx(true);
                cfg.setRs485DelayBefore(100);
                cfg.setRs485DelayAfter(200);
              });

      SerialPort sp = transport.getSerialPort();
      assertRs485Fields(sp, false, true, false, false, 0, 0);
    }

    private static void assertRs485Fields(
        SerialPort sp,
        boolean expectedMode,
        boolean expectedActiveHigh,
        boolean expectedTermination,
        boolean expectedRxDuringTx,
        int expectedDelayBefore,
        int expectedDelayAfter)
        throws Exception {

      assertEquals(expectedMode, getField(sp, "rs485Mode"), "rs485Mode");
      assertEquals(expectedActiveHigh, getField(sp, "rs485ActiveHigh"), "rs485ActiveHigh");
      assertEquals(
          expectedTermination, getField(sp, "rs485EnableTermination"), "rs485EnableTermination");
      assertEquals(expectedRxDuringTx, getField(sp, "rs485RxDuringTx"), "rs485RxDuringTx");
      assertEquals(expectedDelayBefore, getField(sp, "rs485DelayBefore"), "rs485DelayBefore");
      assertEquals(expectedDelayAfter, getField(sp, "rs485DelayAfter"), "rs485DelayAfter");
    }

    private static Object getField(SerialPort sp, String fieldName) throws Exception {
      Field field = SerialPort.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(sp);
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }
}
