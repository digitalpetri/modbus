package com.digitalpetri.modbus.test;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.server.ModbusServer;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.digitalpetri.modbus.tcp.security.SecurityUtil;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.digitalpetri.modbus.test.CertificateUtil.KeyPairCert;
import com.digitalpetri.modbus.test.CertificateUtil.Role;
import java.util.Optional;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.BeforeEach;

public class ModbusTcpTlsClientServerIT extends ClientServerIT {

  ModbusTcpClient client;
  ModbusTcpServer server;

  KeyPairCert authorityKeyPairCert = CertificateUtil.generateCaCertificate();

  KeyPairCert clientKeyPairCert =
      CertificateUtil.generateCaSignedCertificate(Role.CLIENT, authorityKeyPairCert);

  KeyPairCert serverKeyPairCert =
      CertificateUtil.generateCaSignedCertificate(Role.SERVER, authorityKeyPairCert);

  NettyTcpClientTransport clientTransport;

  @BeforeEach
  void setup() throws Exception {
    var processImage = new ProcessImage();
    var modbusServices = new ReadWriteModbusServices() {
      @Override
      protected Optional<ProcessImage> getProcessImage(int unitId) {
        return Optional.of(processImage);
      }
    };

    KeyManagerFactory serverKeyManagerFactory = SecurityUtil.createKeyManagerFactory(
        serverKeyPairCert.keyPair().getPrivate(),
        serverKeyPairCert.certificate()
    );
    TrustManagerFactory serverTrustManagerFactory = SecurityUtil.createTrustManagerFactory(
        authorityKeyPairCert.certificate()
    );

    int serverPort = -1;

    for (int i = 50200; i < 65536; i++) {
      try {
        final var port = i;
        var serverTransport = NettyTcpServerTransport.create(cfg -> {
          cfg.bindAddress = "localhost";
          cfg.port = port;

          cfg.tlsEnabled = true;
          cfg.keyManagerFactory = serverKeyManagerFactory;
          cfg.trustManagerFactory = serverTrustManagerFactory;
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

    KeyManagerFactory clientKeyManagerFactory = SecurityUtil.createKeyManagerFactory(
        clientKeyPairCert.keyPair().getPrivate(),
        clientKeyPairCert.certificate()
    );
    TrustManagerFactory clientTrustManagerFactory = SecurityUtil.createTrustManagerFactory(
        authorityKeyPairCert.certificate()
    );

    final var port = serverPort;
    clientTransport = NettyTcpClientTransport.create(cfg -> {
      cfg.hostname = "localhost";
      cfg.port = port;
      cfg.connectPersistent = false;

      cfg.tlsEnabled = true;
      cfg.keyManagerFactory = clientKeyManagerFactory;
      cfg.trustManagerFactory = clientTrustManagerFactory;
    });

    client = ModbusTcpClient.create(clientTransport);
    client.connect();
  }

  @Override
  ModbusClient getClient() {
    return client;
  }

  @Override
  ModbusServer getServer() {
    return server;
  }

}
