package com.digitalpetri.modbus.serial.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.exceptions.ModbusNotConnectedException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SerialPortClientTransportTest {

  @Test
  void sendBeforeConnectFailsWithNotConnected() {
    var transport = SerialPortClientTransport.create(cfg -> cfg.setSerialPort("/dev/null"));
    var frame = new ModbusRtuFrame(1, ByteBuffer.allocate(5), ByteBuffer.allocate(2));

    assertFalse(transport.isConnected());

    ExecutionException failure =
        assertThrows(
            ExecutionException.class,
            () -> transport.send(frame).toCompletableFuture().get(1, TimeUnit.SECONDS));
    assertInstanceOf(ModbusNotConnectedException.class, failure.getCause());
  }
}
