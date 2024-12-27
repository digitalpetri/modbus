package com.digitalpetri.modbus.test;

import static com.digitalpetri.modbus.test.CertificateUtil.createKeyManagerFactory;
import static com.digitalpetri.modbus.test.CertificateUtil.createTrustManagerFactory;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.server.ModbusServer;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.digitalpetri.modbus.test.CertificateUtil.KeyPairCert;
import com.digitalpetri.modbus.test.CertificateUtil.Role;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModbusSecurityIT {

  KeyPairCert authority1Keys = CertificateUtil.generateCaCertificate();
  KeyPairCert authority2Keys = CertificateUtil.generateCaCertificate();

  KeyPairCert client1Keys =
      CertificateUtil.generateCaSignedCertificate(Role.CLIENT, authority1Keys);
  KeyPairCert client2Keys =
      CertificateUtil.generateCaSignedCertificate(Role.CLIENT, authority2Keys);

  KeyPairCert server1Keys =
      CertificateUtil.generateCaSignedCertificate(Role.SERVER, authority1Keys);
  KeyPairCert server2Keys =
      CertificateUtil.generateCaSignedCertificate(Role.SERVER, authority2Keys);

  @Test
  void clientServerMutualTrust() throws Exception {
    ModbusClient client = setupClientWithKeys(client1Keys, authority1Keys);
    ModbusServer server = setupServerWithKeys(server1Keys, authority1Keys);

    server.start();

    Assertions.assertDoesNotThrow(
        () -> {
          client.connect();
          client.readCoils(1, new ReadCoilsRequest(0, 1));
        });

    server.stop();
  }

  @Test
  void clientServerMutualTrust2() throws Exception {
    ModbusClient client = setupClientWithKeys(client2Keys, authority2Keys);
    ModbusServer server = setupServerWithKeys(server2Keys, authority2Keys);

    server.start();

    Assertions.assertDoesNotThrow(
        () -> {
          try {
            client.connect();
            client.readCoils(1, new ReadCoilsRequest(0, 1));
          } finally {
            client.disconnect();
          }
        });

    server.stop();
  }

  @Test
  void clientRejectsUntrustedServerCertificate() throws Exception {
    ModbusClient client = setupClientWithKeys(client1Keys, authority1Keys);
    ModbusServer server = setupServerWithKeys(server2Keys, authority1Keys, authority2Keys);

    server.start();

    Assertions.assertThrows(
        Exception.class,
        () -> {
          try {
            client.connect();
            client.readCoils(1, new ReadCoilsRequest(0, 1));
          } finally {
            client.disconnect();
          }
        });

    server.stop();
  }

  @Test
  void serverRejectsUntrustedClientCertificate() throws Exception {
    ModbusClient client = setupClientWithKeys(client1Keys, authority1Keys, authority2Keys);
    ModbusServer server = setupServerWithKeys(server2Keys, authority2Keys);

    server.start();

    Assertions.assertThrows(
        Exception.class,
        () -> {
          try {
            client.connect();
            client.readCoils(1, new ReadCoilsRequest(0, 1));
          } finally {
            client.disconnect();
          }
        });

    server.stop();
  }

  ModbusClient setupClientWithKeys(KeyPairCert clientKeys, KeyPairCert... authorityKeys) {
    var transport = NettyTcpClientTransport.create(cfg -> {
      cfg.hostname = "localhost";
      cfg.port = 50200;
      cfg.connectPersistent = false;

      cfg.tlsEnabled = true;
      cfg.keyManagerFactory = createKeyManagerFactory(
          clientKeys.keyPair(),
          clientKeys.certificate()
      );
      cfg.trustManagerFactory = createTrustManagerFactory(
          Arrays.stream(authorityKeys)
              .map(KeyPairCert::certificate)
              .toArray(X509Certificate[]::new)
      );
    });

    return ModbusTcpClient.create(transport);
  }

  ModbusServer setupServerWithKeys(KeyPairCert serverKeys, KeyPairCert... authorityKeys) {
    var serverTransport = NettyTcpServerTransport.create(cfg -> {
      cfg.bindAddress = "localhost";
      cfg.port = 50200;

      cfg.tlsEnabled = true;
      cfg.keyManagerFactory = createKeyManagerFactory(
          serverKeys.keyPair(),
          serverKeys.certificate()
      );
      cfg.trustManagerFactory = createTrustManagerFactory(
          Arrays.stream(authorityKeys)
              .map(KeyPairCert::certificate)
              .toArray(X509Certificate[]::new)
      );
    });

    var processImage = new ProcessImage();
    var modbusServices = new ReadWriteModbusServices() {
      @Override
      protected Optional<ProcessImage> getProcessImage(int unitId) {
        return Optional.of(processImage);
      }
    };

    return ModbusTcpServer.create(serverTransport, modbusServices);
  }

}
