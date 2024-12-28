package com.digitalpetri.modbus.test;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.server.ModbusServer;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.digitalpetri.modbus.tcp.security.SecurityUtil;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.digitalpetri.modbus.test.CertificateUtil.KeyPairCert;
import com.digitalpetri.modbus.test.CertificateUtil.Role;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
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

  KeyPairCert selfSignedClientKeys = CertificateUtil.generateSelfSignedCertificate(Role.CLIENT);
  KeyPairCert selfSignedServerKeys = CertificateUtil.generateSelfSignedCertificate(Role.SERVER);

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

  @Test
  void selfSignedClientAndServer() throws Exception {
    ModbusClient client = setupClientWithKeys(selfSignedClientKeys, selfSignedServerKeys);
    ModbusServer server = setupServerWithKeys(selfSignedServerKeys, selfSignedClientKeys);

    server.start();

    Assertions.assertDoesNotThrow(
        () -> {
          client.connect();
          client.readCoils(1, new ReadCoilsRequest(0, 1));
        });

    server.stop();
  }

  ModbusClient setupClientWithKeys(KeyPairCert clientKeys, KeyPairCert... authorityKeys)
      throws Exception {

    KeyManagerFactory keyManagerFactory =
        SecurityUtil.createKeyManagerFactory(
            clientKeys.keyPair().getPrivate(), clientKeys.certificate());

    TrustManagerFactory trustManagerFactory =
        SecurityUtil.createTrustManagerFactory(
            Arrays.stream(authorityKeys)
                .map(KeyPairCert::certificate)
                .toArray(X509Certificate[]::new));

    var transport =
        NettyTcpClientTransport.create(
            cfg -> {
              cfg.hostname = "localhost";
              cfg.port = 50200;
              cfg.connectPersistent = false;

              cfg.tlsEnabled = true;
              cfg.keyManagerFactory = keyManagerFactory;
              cfg.trustManagerFactory = trustManagerFactory;
            });

    return ModbusTcpClient.create(transport);
  }

  ModbusServer setupServerWithKeys(KeyPairCert serverKeys, KeyPairCert... authorityKeys)
      throws Exception {

    KeyManagerFactory keyManagerFactory =
        SecurityUtil.createKeyManagerFactory(
            serverKeys.keyPair().getPrivate(), serverKeys.certificate());

    TrustManagerFactory trustManagerFactory =
        SecurityUtil.createTrustManagerFactory(
            Arrays.stream(authorityKeys)
                .map(KeyPairCert::certificate)
                .toArray(X509Certificate[]::new));

    var serverTransport =
        NettyTcpServerTransport.create(
            cfg -> {
              cfg.bindAddress = "localhost";
              cfg.port = 50200;

              cfg.tlsEnabled = true;
              cfg.keyManagerFactory = keyManagerFactory;
              cfg.trustManagerFactory = trustManagerFactory;
            });

    var processImage = new ProcessImage();
    var modbusServices =
        new ReadWriteModbusServices() {
          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            return Optional.of(processImage);
          }
        };

    return ModbusTcpServer.create(serverTransport, modbusServices);
  }
}
