package com.digitalpetri.modbus.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpRequestContext;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.RawModbusTcpRequest;
import com.digitalpetri.modbus.server.RawModbusTcpResponse;
import com.digitalpetri.modbus.server.RawModbusTcpServices;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.Netty;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.digitalpetri.modbus.tcp.client.NettyTimeoutScheduler;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ModbusTcpRawClientServerIT {

  ModbusTcpClient client;
  ModbusTcpServer server;

  @AfterEach
  void teardown() throws Exception {
    if (client != null) {
      client.disconnect();
    }
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void sendRawRoundTripsVendorPdu() throws Exception {
    var services = new TestRawModbusTcpServices();
    startClientServer(services);

    byte[] responsePdu = client.sendRaw(1, new byte[] {(byte) 0x5A, 0x00, 0x01});

    assertArrayEquals(new byte[] {(byte) 0xDA, 0x04}, responsePdu);
    RawModbusTcpRequest request = services.vendorRequest.get();
    assertNotNull(request);
    assertArrayEquals(new byte[] {(byte) 0x5A, 0x00, 0x01}, request.pdu());
  }

  @Test
  void sendRawRoundTripsEmptyPdu() throws Exception {
    var services = new TestRawModbusTcpServices();
    startClientServer(services);

    byte[] responsePdu = client.sendRaw(1, new byte[0]);

    assertArrayEquals(new byte[0], responsePdu);
    RawModbusTcpRequest request = services.emptyRequest.get();
    assertNotNull(request);
    assertArrayEquals(new byte[0], request.pdu());
  }

  @Test
  void typedFallbackStillWorksWithRawServices() throws Exception {
    var services = new TestRawModbusTcpServices();
    startClientServer(services);

    ReadHoldingRegistersResponse response =
        client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 2));

    assertTrue(services.standardReadDeclined.get());
    assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, response.registers());
  }

  private void startClientServer(RawModbusTcpServices services) throws Exception {
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

        server = ModbusTcpServer.create(serverTransport, services);
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
    var clientTransport =
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

  private static class TestRawModbusTcpServices extends ReadWriteModbusServices
      implements RawModbusTcpServices {

    private final ProcessImage processImage = new ProcessImage();
    private final AtomicBoolean standardReadDeclined = new AtomicBoolean();
    private final AtomicReference<RawModbusTcpRequest> emptyRequest = new AtomicReference<>();
    private final AtomicReference<RawModbusTcpRequest> vendorRequest = new AtomicReference<>();

    @Override
    public Optional<RawModbusTcpResponse> handleRawTcpRequest(
        ModbusTcpRequestContext context, RawModbusTcpRequest request) {

      byte[] pdu = request.pdu();
      if (pdu.length == 0) {
        emptyRequest.set(request);
        return Optional.of(new RawModbusTcpResponse(new byte[0]));
      }

      if (pdu.length > 0 && (pdu[0] & 0xFF) == 0x5A) {
        vendorRequest.set(request);
        return Optional.of(new RawModbusTcpResponse(new byte[] {(byte) 0xDA, 0x04}));
      }

      if (pdu.length > 0 && (pdu[0] & 0xFF) == 0x03) {
        standardReadDeclined.set(true);
      }

      return Optional.empty();
    }

    @Override
    protected Optional<ProcessImage> getProcessImage(int unitId) {
      return Optional.of(processImage);
    }
  }
}
