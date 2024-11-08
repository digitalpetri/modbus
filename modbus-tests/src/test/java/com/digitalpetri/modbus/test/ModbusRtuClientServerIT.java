package com.digitalpetri.modbus.test;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusRtuClient;
import com.digitalpetri.modbus.serial.client.SerialPortClientTransport;
import com.digitalpetri.modbus.serial.server.SerialPortServerTransport;
import com.digitalpetri.modbus.server.ModbusRtuServer;
import com.digitalpetri.modbus.server.ModbusServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.fazecast.jSerialComm.SerialPort;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf("serialPortsConfigured")
public class ModbusRtuClientServerIT extends ClientServerIT {

  ModbusRtuClient client;
  ModbusRtuServer server;

  @BeforeEach
  void setup() throws Exception {
    var processImage = new ProcessImage();
    var modbusServices = new ReadWriteModbusServices() {
      @Override
      protected Optional<ProcessImage> getProcessImage(int unitId) {
        return Optional.of(processImage);
      }
    };

    server = ModbusRtuServer.create(
        SerialPortServerTransport.create(
            cfg -> {
              cfg.serialPort = System.getProperty("modbus.serverSerialPort");
              cfg.baudRate = 115200;
              cfg.dataBits = 8;
              cfg.parity = SerialPort.NO_PARITY;
              cfg.stopBits = 1;
            }
        ),
        modbusServices
    );
    server.start();

    client = ModbusRtuClient.create(
        SerialPortClientTransport.create(
            cfg -> {
              cfg.serialPort = System.getProperty("modbus.clientSerialPort");
              cfg.baudRate = 115200;
              cfg.dataBits = 8;
              cfg.parity = SerialPort.NO_PARITY;
              cfg.stopBits = 1;
            }
        )
    );
    client.connect();
  }

  @AfterEach
  void teardown() throws Exception {
    if (client != null) {
      client.disconnect();
    }
    if (server != null) {
      server.stop();
    }
  }

  @Override
  ModbusClient getClient() {
    return client;
  }

  @Override
  ModbusServer getServer() {
    return server;
  }

  static boolean serialPortsConfigured() {
    return System.getProperty("modbus.clientSerialPort") != null
        && System.getProperty("modbus.serverSerialPort") != null;
  }

}
