package com.digitalpetri.modbus.test;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.server.ModbusServer;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.digitalpetri.modbus.test.CertificateUtil.KeyPairCert;
import com.digitalpetri.modbus.test.CertificateUtil.Role;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
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

    int serverPort = -1;

    for (int i = 50200; i < 65536; i++) {
      try {
        final var port = i;
        var serverTransport = NettyTcpServerTransport.create(cfg -> {
          cfg.bindAddress = "localhost";
          cfg.port = port;

          cfg.tlsEnabled = true;
          cfg.keyManagerFactory = createKeyManagerFactory(
              serverKeyPairCert.keyPair(),
              serverKeyPairCert.certificate()
          );
          cfg.trustManagerFactory = createTrustManagerFactory(authorityKeyPairCert.certificate());
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

    final var port = serverPort;
    clientTransport = NettyTcpClientTransport.create(cfg -> {
      cfg.hostname = "localhost";
      cfg.port = port;
      cfg.connectPersistent = false;

      cfg.tlsEnabled = true;
      cfg.keyManagerFactory = createKeyManagerFactory(
          clientKeyPairCert.keyPair(),
          clientKeyPairCert.certificate()
      );
      cfg.trustManagerFactory = createTrustManagerFactory(authorityKeyPairCert.certificate());
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

  static KeyManagerFactory createKeyManagerFactory(
      KeyPair keyPair,
      X509Certificate certificate
  ) {

    try {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null);
      keyStore.setKeyEntry("alias", keyPair.getPrivate(), new char[0],
          new X509Certificate[]{certificate});

      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
          KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, new char[0]);

      return keyManagerFactory;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static TrustManagerFactory createTrustManagerFactory(X509Certificate... certificates) {
    try {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null);

      for (int i = 0; i < certificates.length; i++) {
        X509Certificate certificate = certificates[i];

        keyStore.setCertificateEntry("alias" + i, certificate);
      }

      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
          TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(keyStore);

      return trustManagerFactory;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
