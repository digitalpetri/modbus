package com.digitalpetri.modbus.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.ModbusPduSerializer.DefaultRequestSerializer;
import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.internal.util.Hex;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.server.ModbusServer;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.Netty;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport.ConnectionListener;
import com.digitalpetri.modbus.tcp.client.NettyTimeoutScheduler;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ModbusTcpClientServerIT extends ClientServerIT {

  ModbusTcpClient client;
  ModbusTcpServer server;

  NettyTcpClientTransport clientTransport;

  @BeforeEach
  void setup() throws Exception {
    var processImage = new ProcessImage();
    var modbusServices =
        new ReadWriteModbusServices() {
          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            return Optional.of(processImage);
          }
        };

    int serverPort = -1;

    for (int i = 50200; i < 65536; i++) {
      try {
        final var port = i;
        var serverTransport =
            NettyTcpServerTransport.create(
                cfg -> {
                  cfg.bindAddress = "localhost";
                  cfg.port = port;
                });

        System.out.println("trying port " + port);
        server = ModbusTcpServer.create(serverTransport, modbusServices);
        server.start();
        serverPort = port;
        break;
      } catch (Exception e) {
        server = null;
      }
    }

    if (server == null) {
      throw new Exception("Failed to start server");
    }

    final var port = serverPort;
    clientTransport =
        NettyTcpClientTransport.create(
            cfg -> {
              cfg.hostname = "localhost";
              cfg.port = port;
              cfg.connectPersistent = false;
            });

    client =
        ModbusTcpClient.create(
            clientTransport,
            cfg -> cfg.timeoutScheduler = new NettyTimeoutScheduler(Netty.sharedWheelTimer()));
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

  @Test
  void sendRaw() throws Exception {
    var request = new ReadHoldingRegistersRequest(0, 10);
    ByteBuffer buffer = ByteBuffer.allocate(256);
    DefaultRequestSerializer.INSTANCE.encode(request, buffer);

    byte[] requestedPduBytes = new byte[buffer.position()];
    buffer.flip();
    buffer.get(requestedPduBytes);

    System.out.println("requestedPduBytes: " + Hex.format(requestedPduBytes));

    byte[] responsePduBytes = client.sendRaw(0, requestedPduBytes);

    System.out.println("responsePduBytes: " + Hex.format(responsePduBytes));
  }

  @Test
  void connectionListener() throws Exception {
    var onConnection = new CountDownLatch(1);
    var onConnectionLost = new CountDownLatch(1);

    clientTransport.addConnectionListener(
        new ConnectionListener() {
          @Override
          public void onConnection() {
            onConnection.countDown();
          }

          @Override
          public void onConnectionLost() {
            onConnectionLost.countDown();
          }
        });

    assertTrue(client.isConnected());

    client.disconnect();
    assertTrue(onConnectionLost.await(1, TimeUnit.SECONDS));

    client.connect();
    assertTrue(onConnection.await(1, TimeUnit.SECONDS));
  }
}
