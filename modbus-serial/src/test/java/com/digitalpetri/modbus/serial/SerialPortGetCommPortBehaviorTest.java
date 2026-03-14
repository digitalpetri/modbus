package com.digitalpetri.modbus.serial;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.exceptions.ModbusConnectException;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.serial.client.SerialPortClientTransport;
import com.digitalpetri.modbus.serial.server.SerialPortServerTransport;
import com.fazecast.jSerialComm.SerialPort;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Tests documenting the behavior of {@link SerialPort#getCommPort(String)} with non-existent ports
 * and how the serial transport classes handle it.
 *
 * <p>The behavior of {@code getCommPort} differs by platform:
 *
 * <ul>
 *   <li><b>Linux/macOS:</b> validates that the port descriptor exists on the filesystem (checking
 *       the path as-is, then with a {@code /dev/} prefix). Throws {@code
 *       SerialPortInvalidPortException} immediately if the file does not exist.
 *   <li><b>Windows:</b> does <em>not</em> validate port existence — rewrites the descriptor to
 *       {@code \\.\COMx} format and creates the {@link SerialPort} object successfully. The failure
 *       is deferred to {@code openPort()}, which returns {@code false}.
 * </ul>
 *
 * <p>This behavioral difference is why the transport classes defer the {@code getCommPort} call to
 * connect/bind time rather than calling it in the constructor. On Linux/macOS the deferred call
 * allows the exception to be caught and wrapped as a {@link ModbusConnectException}. On Windows the
 * constructor would have succeeded either way, but the deferred approach keeps the behavior
 * consistent across platforms.
 */
class SerialPortGetCommPortBehaviorTest {

  private static final String BOGUS_UNIX_PORT = "/dev/this_port_does_not_exist_xyz";
  private static final String BOGUS_WINDOWS_PORT = "COM999";

  @Nested
  @EnabledOnOs({OS.LINUX, OS.MAC})
  class GetCommPortOnLinuxMac {

    @Test
    void getCommPortThrowsForNonExistentPortFile() {
      // On Linux/Mac, getCommPort checks File.exists() for the port descriptor.
      // A path that doesn't exist on the filesystem causes an immediate exception.
      assertThrows(RuntimeException.class, () -> SerialPort.getCommPort(BOGUS_UNIX_PORT));
    }

    @Test
    void getCommPortThrowsForWindowsStyleDescriptor() {
      // A Windows-style descriptor like "COM999" also fails on Linux/Mac because
      // it doesn't exist on the filesystem (not under /dev/ either).
      assertThrows(RuntimeException.class, () -> SerialPort.getCommPort(BOGUS_WINDOWS_PORT));
    }
  }

  @Nested
  @EnabledOnOs(OS.WINDOWS)
  class GetCommPortOnWindows {

    @Test
    void getCommPortDoesNotThrowForNonExistentComPort() {
      // On Windows, getCommPort rewrites the descriptor to \\.\COMx format
      // and does not validate that the port exists. The SerialPort object is
      // created successfully; failure is deferred to openPort().
      SerialPort sp = assertDoesNotThrow(() -> SerialPort.getCommPort(BOGUS_WINDOWS_PORT));
      assertNotNull(sp);
    }

    @Test
    void getCommPortWithNonExistentComPortFailsToOpen() {
      SerialPort sp = SerialPort.getCommPort(BOGUS_WINDOWS_PORT);
      assertFalse(sp.openPort(), "opening a non-existent COM port should fail");
    }

    @Test
    void getCommPortDoesNotThrowForUnixStyleDescriptor() {
      // On Windows, getCommPort rewrites any descriptor to \\.\<name> format
      // without filesystem validation, so even a Unix-style path succeeds.
      SerialPort sp = assertDoesNotThrow(() -> SerialPort.getCommPort(BOGUS_UNIX_PORT));
      assertNotNull(sp);
    }

    @Test
    void getCommPortWithUnixStyleDescriptorFailsToOpen() {
      SerialPort sp = SerialPort.getCommPort(BOGUS_UNIX_PORT);
      assertFalse(sp.openPort(), "opening a Unix-style port on Windows should fail");
    }
  }

  @Nested
  class ClientTransport {

    @Test
    void constructorDoesNotCallGetCommPort() {
      // Construction must always succeed regardless of port name.
      // getCommPort is deferred to getSerialPort()/connect().
      assertDoesNotThrow(
          () -> SerialPortClientTransport.create(cfg -> cfg.setSerialPort(BOGUS_UNIX_PORT)));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void getSerialPortThrowsOnLinuxMac() {
      SerialPortClientTransport transport =
          SerialPortClientTransport.create(cfg -> cfg.setSerialPort(BOGUS_UNIX_PORT));

      // On Linux/Mac, getCommPort throws for non-existent ports,
      // which getSerialPort wraps as ModbusException.
      ModbusException ex = assertThrows(ModbusException.class, transport::getSerialPort);
      assertTrue(ex.getMessage().contains(BOGUS_UNIX_PORT));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void getSerialPortSucceedsOnWindows() throws ModbusException {
      SerialPortClientTransport transport =
          SerialPortClientTransport.create(cfg -> cfg.setSerialPort(BOGUS_WINDOWS_PORT));

      // On Windows, getCommPort succeeds for non-existent COM ports,
      // so getSerialPort also succeeds. Failure is deferred to connect/openPort.
      assertDoesNotThrow(transport::getSerialPort);
    }

    @Test
    void connectFailsWithModbusConnectException() {
      // On all platforms, connect with a bogus port results in ModbusConnectException.
      // On Linux/Mac: getSerialPort() throws, caught and wrapped.
      // On Windows: getSerialPort() succeeds, but openPort() returns false.
      String port = isWindows() ? BOGUS_WINDOWS_PORT : BOGUS_UNIX_PORT;
      SerialPortClientTransport transport =
          SerialPortClientTransport.create(cfg -> cfg.setSerialPort(port));

      CompletableFuture<Void> future = transport.connect();
      ExecutionException ex = assertThrows(ExecutionException.class, future::get);
      assertInstanceOf(ModbusConnectException.class, ex.getCause());
    }
  }

  @Nested
  class ServerTransport {

    @Test
    void constructorDoesNotCallGetCommPort() {
      assertDoesNotThrow(
          () -> SerialPortServerTransport.create(cfg -> cfg.setSerialPort(BOGUS_UNIX_PORT)));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void getSerialPortThrowsOnLinuxMac() {
      SerialPortServerTransport transport =
          SerialPortServerTransport.create(cfg -> cfg.setSerialPort(BOGUS_UNIX_PORT));

      ModbusException ex = assertThrows(ModbusException.class, transport::getSerialPort);
      assertTrue(ex.getMessage().contains(BOGUS_UNIX_PORT));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void getSerialPortSucceedsOnWindows() {
      SerialPortServerTransport transport =
          SerialPortServerTransport.create(cfg -> cfg.setSerialPort(BOGUS_WINDOWS_PORT));

      assertDoesNotThrow(transport::getSerialPort);
    }

    @Test
    void bindFailsWithModbusConnectException() {
      String port = isWindows() ? BOGUS_WINDOWS_PORT : BOGUS_UNIX_PORT;
      SerialPortServerTransport transport =
          SerialPortServerTransport.create(cfg -> cfg.setSerialPort(port));

      CompletableFuture<Void> future = transport.bind().toCompletableFuture();
      ExecutionException ex = assertThrows(ExecutionException.class, future::get);
      assertInstanceOf(ModbusConnectException.class, ex.getCause());
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }
}
