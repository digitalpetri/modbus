# Secure a Modbus TCP client with TLS

Use this guide to connect a Modbus TCP client with mutual TLS. The built-in Netty transport
requires both a client key manager and a trust manager when TLS is enabled, and the built-in
server requires a trusted client certificate. For server-side TLS configuration, see the
[Netty server transport reference](../../reference/transport-configuration.md#netty-server-transport).

## Prerequisites

You need:

- the `modbus-tcp` dependency;
- a PKCS#12 key store containing the client's private key and certificate chain;
- a PKCS#12 trust store containing the certificate authorities used to verify the server's
  certificate chain (the server side, in turn, must trust the client's issuer); and
- the TLS endpoint hostname and port. The transport defaults to port 802 when TLS is enabled.

Provision identities and trust anchors according to your site's PKI policy. Do not copy test keys
or self-signed test certificates into production.

## Load key material and enable TLS

The program below expects four arguments: host, port, client-key-store path, and trust-store path.
It uses `MODBUS_KEYSTORE_PASSWORD` for both PKCS#12 files and the private-key entry.

```java
package example;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.tcp.Netty;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.digitalpetri.modbus.tcp.security.SecurityUtil;
import io.netty.handler.ssl.SslHandler;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import javax.net.ssl.SSLParameters;

public final class TlsModbusTcpExample {

  public static void main(String[] args) throws Exception {
    if (args.length != 4) {
      throw new IllegalArgumentException(
          "usage: TlsModbusTcpExample HOST PORT CLIENT.p12 TRUST.p12");
    }

    String passwordValue = System.getenv("MODBUS_KEYSTORE_PASSWORD");
    if (passwordValue == null) {
      throw new IllegalStateException("MODBUS_KEYSTORE_PASSWORD is not set");
    }
    char[] password = passwordValue.toCharArray();

    try {
      KeyStore clientKeys = loadPkcs12(Path.of(args[2]), password);
      KeyStore trustAnchors = loadPkcs12(Path.of(args[3]), password);

      var keyManagerFactory =
          SecurityUtil.createKeyManagerFactory(clientKeys, password);
      var trustManagerFactory =
          SecurityUtil.createTrustManagerFactory(trustAnchors);

      var transport =
          NettyTcpClientTransport.create(
              cfg -> {
                cfg.setHostname(args[0]);
                cfg.setPort(Integer.parseInt(args[1]));
                cfg.setConnectPersistent(false);
                cfg.setTlsEnabled(true);
                cfg.setKeyManagerFactory(keyManagerFactory);
                cfg.setTrustManagerFactory(trustManagerFactory);
                cfg.setPipelineCustomizer(
                    pipeline -> {
                      SslHandler sslHandler = pipeline.get(SslHandler.class);
                      sslHandler.setHandshakeTimeoutMillis(5_000);
                      SSLParameters parameters = sslHandler.engine().getSSLParameters();
                      parameters.setEndpointIdentificationAlgorithm("HTTPS");
                      sslHandler.engine().setSSLParameters(parameters);
                    });
              });

      var client = ModbusTcpClient.create(transport);

      try (var ignored = client.open()) {
        var response =
            client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 1));
        System.out.println("received " + response.registers().length + " register bytes");
      }
    } finally {
      Arrays.fill(password, '\0');
      Netty.releaseSharedResources();
      Modbus.releaseSharedResources();
    }
  }

  private static KeyStore loadPkcs12(Path path, char[] password) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(path)) {
      keyStore.load(input, password);
    }
    return keyStore;
  }
}
```

The transport negotiates TLS 1.2 or TLS 1.3, completes the TLS handshake as part of `connect()`,
and then exchanges ordinary Modbus TCP frames inside the encrypted connection. The built-in
transport supplies the host to the TLS engine but does not enable endpoint identification by
default. The pipeline customizer above enables Java's `HTTPS` hostname check so `HOST` must match
the server certificate's identity. It also changes Netty's TLS handshake timeout from its current
10-second default to 5 seconds. Retain the identity check and choose a handshake bound that
matches the deployment.

The example also disables persistent initial retries. If connection, trust, or hostname validation
fails before `open()` returns, the client will not keep reconnecting after control enters the
process-level cleanup block.

## Clean up securely

Disconnect the client even after a failed request. Clear password arrays when they are no longer
needed, restrict key-store filesystem permissions, and release the shared Netty and Modbus
resources only at whole-application shutdown.

## Verify the result

Run against a server configured to trust the client certificate. A successful request prints:

```text
received 2 register bytes
```

Then test the negative path with a trust store that does not trust the server. Connection or the
first request must fail; a connection that succeeds with an untrusted chain indicates a PKI or
deployment configuration problem.

## Common failure symptoms

| Symptom | Check |
| --- | --- |
| Configuration fails before connecting | Both key and trust manager factories were supplied after enabling TLS |
| TLS handshake fails with an unknown CA or path error | Client trust store contains the server issuer chain and server trusts the client issuer |
| TLS handshake fails with a hostname error | `HOST` matches a DNS name or IP identity allowed by the server certificate |
| TLS handshake fails with no suitable certificate | Client key store contains a private-key entry and its certificate chain |
| TCP port accepts a connection but Modbus requests time out | Client and server disagree about TLS versus cleartext, or the unit ID is wrong |
| Works with test certificates but not deployed certificates | Validity period, key usage, issuer chain, deployed private key, and site PKI policy |

The integration tests exercise mutual trust, untrusted client/server rejection, and self-signed
test identities in
[`ModbusSecurityIT`](../../../modbus-tests/src/test/java/com/digitalpetri/modbus/test/ModbusSecurityIT.java).

## Related reference

- [Transports, framing, and security](../../concepts/transports-framing-and-security.md)
- [Transport configuration](../../reference/transport-configuration.md#netty-tcp-client-transport)
- [Errors and exceptions](../../reference/errors-and-exceptions.md)
- [Security utility Javadocs](../../reference/api-reference.md#transport-and-security-api)
