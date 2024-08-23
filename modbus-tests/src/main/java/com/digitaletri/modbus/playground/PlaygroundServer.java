package com.digitaletri.modbus.playground;

import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.server.ModbusRequestContext;
import com.digitalpetri.modbus.server.ModbusServices;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.NettyTcpServerTransport;
import io.netty.util.ResourceLeakDetector;
import java.nio.ByteBuffer;

public class PlaygroundServer {

  public static void main(String[] args) throws Exception {
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);

    var services = new ModbusServices() {
      @Override
      public ReadHoldingRegistersResponse readHoldingRegisters(
          ModbusRequestContext context,
          int unitId,
          ReadHoldingRegistersRequest request
      ) {

        byte[] registers = new byte[request.quantity() * 2];

        var bb = ByteBuffer.wrap(registers);
        for (int i = 0; i < request.quantity(); i++) {
          bb.putShort((short) ((request.address() + i) % 65536));
        }

        return new ReadHoldingRegistersResponse(registers);
      }
    };

    var transport = NettyTcpServerTransport.create(cfg -> {
      cfg.bindAddress = "localhost";
      cfg.port = 12685;
    });

    var server = ModbusTcpServer.create(transport, services);

    server.start();
    Thread.sleep(Integer.MAX_VALUE);
    server.stop();
  }

}
