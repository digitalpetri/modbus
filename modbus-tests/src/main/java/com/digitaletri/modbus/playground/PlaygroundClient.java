package com.digitaletri.modbus.playground;

import com.digitalpetri.modbus.Netty;
import com.digitalpetri.modbus.client.ModbusRtuClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.client.NettyTcpClientTransport;
import com.digitalpetri.modbus.client.NettyTimeoutScheduler;
import com.digitalpetri.modbus.client.SerialPortClientTransport;
import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.fazecast.jSerialComm.SerialPort;
import io.netty.util.ResourceLeakDetector;

public class PlaygroundClient {

  public static void main(String[] args) throws Exception {
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);

    var transport = NettyTcpClientTransport.create(
        cfg -> {
          cfg.hostname = "localhost";
          cfg.port = 12685;
          cfg.connectPersistent = true;
        }
    );

    var client = ModbusTcpClient.create(
        transport,
        cfg ->
            cfg.timeoutScheduler = new NettyTimeoutScheduler(Netty.sharedWheelTimer())
    );

    client.connect();
    try {
      for (int i = 0; i < 65535; i++) {
        var response = client.readHoldingRegisters(
            0,
            new ReadHoldingRegistersRequest(i, 8)
        );
        if (i % 8 == 0) {
          String range = String.format("%d..%d", i, i + 8 - 1);
          String message = String.format("%10s\t%s", range, response);
          System.out.println(message);
        }
      }
    } finally {
      client.disconnect();
    }
  }

  private static void rtuClient() throws ModbusException {
    var transport = SerialPortClientTransport.create(
        cfg -> {
          cfg.serialPort = "/dev/ttyUSB0";
          cfg.baudRate = 115200;
          cfg.dataBits = 8;
          cfg.stopBits = SerialPort.ONE_STOP_BIT;
          cfg.parity = SerialPort.NO_PARITY;
        }
    );

    var client = ModbusRtuClient.create(transport);

    client.connect();
    try {
      ReadHoldingRegistersResponse response = client.readHoldingRegisters(
          1,
          new ReadHoldingRegistersRequest(0, 10)
      );

      System.out.println("Response: " + response);
    } finally {
      client.disconnect();
    }
  }

}
