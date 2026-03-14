package com.digitalpetri.modbus.serial;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
 *   <li><b>Windows:</b> does <em>not</em> check file existence — rewrites the descriptor to {@code
 *       \\.\COMx} format and defers validation to the native {@code retrievePortDetails()} call,
 *       which throws {@code SerialPortInvalidPortException} for invalid COM ports.
 * </ul>
 *
 * <p>In both cases the result is the same: {@code getCommPort} throws for non-existent ports. The
 * transport classes defer this call to connect/bind time so the exception can be caught and wrapped
 * as a {@link ModbusConnectException}.
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
  }

  @Nested
  @EnabledOnOs(OS.WINDOWS)
  class GetCommPortOnWindows {

    @Test
    void getCommPortThrowsForInvalidComPort() {
      // On Windows, getCommPort rewrites the descriptor to \\.\COMx format
      // without checking file existence. The native retrievePortDetails() call
      // throws for COM ports that don't exist.
      assertThrows(RuntimeException.class, () -> SerialPort.getCommPort(BOGUS_WINDOWS_PORT));
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
    void getSerialPortWrapsExceptionAsModbusException() {
      String port = isWindows() ? BOGUS_WINDOWS_PORT : BOGUS_UNIX_PORT;
      SerialPortClientTransport transport =
          SerialPortClientTransport.create(cfg -> cfg.setSerialPort(port));

      ModbusException ex = assertThrows(ModbusException.class, transport::getSerialPort);
      assertTrue(ex.getMessage().contains(port));
    }

    @Test
    void connectFailsWithModbusConnectException() {
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
    void getSerialPortWrapsExceptionAsModbusException() {
      String port = isWindows() ? BOGUS_WINDOWS_PORT : BOGUS_UNIX_PORT;
      SerialPortServerTransport transport =
          SerialPortServerTransport.create(cfg -> cfg.setSerialPort(port));

      ModbusException ex = assertThrows(ModbusException.class, transport::getSerialPort);
      assertTrue(ex.getMessage().contains(port));
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
