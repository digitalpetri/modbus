package com.digitalpetri.modbus.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusRtuClient;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.server.ModbusRtuServer;
import com.digitalpetri.modbus.server.ModbusServer;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.client.NettyClientTransportConfig;
import com.digitalpetri.modbus.tcp.client.NettyRtuClientTransport;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import com.digitalpetri.modbus.tcp.security.SecurityUtil;
import com.digitalpetri.modbus.tcp.server.NettyRtuServerTransport;
import com.digitalpetri.modbus.tcp.server.NettyServerTransportConfig;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.digitalpetri.modbus.test.CertificateUtil.KeyPairCert;
import com.digitalpetri.modbus.test.CertificateUtil.Role;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.ReferenceCountedOpenSslContext;
import io.netty.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProtocols;
import io.netty.handler.ssl.SslProvider;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class NettyTlsConfigurationTest {

  private static final String TLS_12_CIPHER = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
  private static final String TLS_13_CIPHER = "TLS_AES_128_GCM_SHA256";

  private static KeyPairCert clientIdentity;
  private static KeyPairCert serverIdentity;
  private static KeyManagerFactory clientKeys;
  private static KeyManagerFactory serverKeys;
  private static TrustManagerFactory trust;

  @BeforeAll
  static void createIdentities() throws Exception {
    KeyPairCert authority = CertificateUtil.generateCaCertificate();
    clientIdentity = CertificateUtil.generateCaSignedCertificate(Role.CLIENT, authority);
    serverIdentity = CertificateUtil.generateCaSignedCertificate(Role.SERVER, authority);
    clientKeys =
        SecurityUtil.createKeyManagerFactory(
            clientIdentity.keyPair().getPrivate(), clientIdentity.certificate());
    serverKeys =
        SecurityUtil.createKeyManagerFactory(
            serverIdentity.keyPair().getPrivate(), serverIdentity.certificate());
    trust = SecurityUtil.createTrustManagerFactory(authority.certificate());
  }

  @Test
  void tcpConfiguredProtocolsAndCiphers() throws Exception {
    configuredProtocolsAndCiphers(false);
  }

  @Test
  void rtuConfiguredProtocolsAndCiphers() throws Exception {
    configuredProtocolsAndCiphers(true);
  }

  private void configuredProtocolsAndCiphers(boolean rtu) throws Exception {
    for (String protocol : List.of(SslProtocols.TLS_v1_2, SslProtocols.TLS_v1_3)) {
      String cipher = protocol.equals(SslProtocols.TLS_v1_2) ? TLS_12_CIPHER : TLS_13_CIPHER;
      Consumer<SslContextBuilder> policy =
          b -> b.sslProvider(SslProvider.JDK).protocols(protocol).ciphers(List.of(cipher));

      try (var connection = new Connection(rtu, policy, policy, false)) {
        connection.connectAndRead();
        assertPolicy(connection.clientHandler.get(5, TimeUnit.SECONDS), protocol, cipher);
        assertPolicy(connection.serverHandler.get(5, TimeUnit.SECONDS), protocol, cipher);
        assertEquals(1, connection.clientHookCalls.get());
        assertEquals(1, connection.serverHookCalls.get());

        connection.disconnect();
        connection.connectAndRead();
        assertEquals(2, connection.clientHookCalls.get());
        assertEquals(2, connection.serverHookCalls.get());
      }
    }
  }

  private static void assertPolicy(SslHandler handler, String protocol, String cipher) {
    assertArrayEquals(new String[] {protocol}, handler.engine().getEnabledProtocols());
    assertArrayEquals(new String[] {cipher}, handler.engine().getEnabledCipherSuites());
    assertEquals(protocol, handler.engine().getSession().getProtocol());
    assertEquals(cipher, handler.engine().getSession().getCipherSuite());
    assertFalse(handler.engine() instanceof ReferenceCountedOpenSslEngine);
  }

  @Test
  void legacyConstructorsKeepMutualTlsDefaults() throws Exception {
    assertEquals(SslProvider.JDK, SslContext.defaultClientProvider());
    assertEquals(SslProvider.JDK, SslContext.defaultServerProvider());
    for (boolean rtu : List.of(false, true)) {
      try (var connection = new Connection(rtu, b -> {}, b -> {}, true)) {
        connection.connectAndRead();
        SslHandler serverHandler = connection.serverHandler.get(5, TimeUnit.SECONDS);
        assertTrue(serverHandler.engine().getNeedClientAuth());
        assertTrue(
            Arrays.asList(serverHandler.engine().getEnabledProtocols())
                .containsAll(List.of(SslProtocols.TLS_v1_2, SslProtocols.TLS_v1_3)));
      }
    }
  }

  @Test
  void serversStillRejectClientsWithoutCertificates() throws Exception {
    for (boolean rtu : List.of(false, true)) {
      try (var connection =
          new Connection(rtu, b -> b.keyManager((KeyManagerFactory) null), b -> {}, false)) {
        assertThrows(Exception.class, connection::connectAndRead);
        SslHandler serverHandler = connection.serverHandler.get(5, TimeUnit.SECONDS);
        assertTrue(serverHandler.engine().getNeedClientAuth());
        ExecutionException failure =
            assertThrows(
                ExecutionException.class,
                () -> serverHandler.handshakeFuture().get(5, TimeUnit.SECONDS));
        assertInstanceOf(SSLException.class, failure.getCause());
      }
    }
  }

  @Test
  void incompatibleProtocolPoliciesRejectHandshake() throws Exception {
    for (boolean rtu : List.of(false, true)) {
      assertHandshakeRejected(
          rtu,
          b -> b.sslProvider(SslProvider.JDK).protocols(SslProtocols.TLS_v1_2),
          b -> b.sslProvider(SslProvider.JDK).protocols(SslProtocols.TLS_v1_3));
    }
  }

  @Test
  void incompatibleCipherPoliciesRejectHandshake() throws Exception {
    for (boolean rtu : List.of(false, true)) {
      assertHandshakeRejected(
          rtu,
          b ->
              b.sslProvider(SslProvider.JDK)
                  .protocols(SslProtocols.TLS_v1_2)
                  .ciphers(List.of(TLS_12_CIPHER)),
          b ->
              b.sslProvider(SslProvider.JDK)
                  .protocols(SslProtocols.TLS_v1_2)
                  .ciphers(List.of("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")));
    }
  }

  private static void assertHandshakeRejected(
      boolean rtu,
      Consumer<SslContextBuilder> clientPolicy,
      Consumer<SslContextBuilder> serverPolicy)
      throws Exception {
    try (var connection = new Connection(rtu, clientPolicy, serverPolicy, false)) {
      assertThrows(Exception.class, connection::connectAndRead);
      SslHandler serverHandler = connection.serverHandler.get(5, TimeUnit.SECONDS);
      ExecutionException failure =
          assertThrows(
              ExecutionException.class,
              () -> serverHandler.handshakeFuture().get(5, TimeUnit.SECONDS));
      assertInstanceOf(SSLException.class, failure.getCause());
      assertEquals(1, connection.clientHookCalls.get());
      assertEquals(1, connection.serverHookCalls.get());
    }
  }

  @Test
  void serverHookFailureDoesNotPreventNextConnection() throws Exception {
    for (boolean rtu : List.of(false, true)) {
      var attempts = new AtomicInteger();
      Consumer<SslContextBuilder> serverPolicy =
          b -> {
            if (attempts.getAndIncrement() == 0) {
              throw new IllegalStateException("test TLS configuration failure");
            }
          };
      try (var connection = new Connection(rtu, b -> {}, serverPolicy, false)) {
        assertThrows(Exception.class, connection::connectAndRead);
        connection.disconnect();
        connection.connectAndRead();
        assertEquals(2, attempts.get());
      }
    }
  }

  @Test
  @Tag("native-tls")
  void tcpNativeContextReferencesAreReleased() throws Exception {
    nativeContextReferencesAreReleased(false);
  }

  @Test
  @Tag("native-tls")
  void rtuNativeContextReferencesAreReleased() throws Exception {
    nativeContextReferencesAreReleased(true);
  }

  private void nativeContextReferencesAreReleased(boolean rtu) throws Exception {
    assertTrue(
        OpenSsl.isAvailable(), () -> "native TLS unavailable: " + OpenSsl.unavailabilityCause());
    Consumer<SslContextBuilder> policy =
        b ->
            b.sslProvider(SslProvider.OPENSSL_REFCNT)
                .protocols(SslProtocols.TLS_v1_2)
                .ciphers(List.of(TLS_12_CIPHER));
    ReferenceCountedOpenSslContext clientContext;
    ReferenceCountedOpenSslContext serverContext;
    ReferenceCountedOpenSslEngine clientEngine;
    ReferenceCountedOpenSslEngine serverEngine;
    try (var connection = new Connection(rtu, policy, policy, false)) {
      connection.connectAndRead();
      clientEngine =
          assertInstanceOf(
              ReferenceCountedOpenSslEngine.class,
              connection.clientHandler.get(5, TimeUnit.SECONDS).engine());
      serverEngine =
          assertInstanceOf(
              ReferenceCountedOpenSslEngine.class,
              connection.serverHandler.get(5, TimeUnit.SECONDS).engine());
      clientContext = parentContext(clientEngine);
      serverContext = parentContext(serverEngine);
      // Only the engines should retain the per-connection contexts after initialization.
      assertEquals(1, clientContext.refCnt());
      assertEquals(1, serverContext.refCnt());
      assertEquals(SslProtocols.TLS_v1_2, clientEngine.getSession().getProtocol());
      assertEquals(TLS_12_CIPHER, clientEngine.getSession().getCipherSuite());
    }
    assertEquals(0, clientEngine.refCnt());
    assertEquals(0, serverEngine.refCnt());
    assertEquals(0, clientContext.refCnt());
    assertEquals(0, serverContext.refCnt());
  }

  private static ReferenceCountedOpenSslContext parentContext(ReferenceCountedOpenSslEngine engine)
      throws Exception {
    // Netty does not expose the context reference count through its engine API.
    Field field = ReferenceCountedOpenSslEngine.class.getDeclaredField("parentContext");
    field.setAccessible(true);
    return (ReferenceCountedOpenSslContext) field.get(engine);
  }

  private static class Connection implements AutoCloseable {

    private final NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger clientHookCalls = new AtomicInteger();
    private final AtomicInteger serverHookCalls = new AtomicInteger();
    private final CompletableFuture<Integer> port = new CompletableFuture<>();
    private CompletableFuture<SslHandler> clientHandler = new CompletableFuture<>();
    private CompletableFuture<SslHandler> serverHandler = new CompletableFuture<>();
    private final ModbusServer server;
    private final ModbusClient client;

    private Connection(
        boolean rtu,
        Consumer<SslContextBuilder> clientPolicy,
        Consumer<SslContextBuilder> serverPolicy,
        boolean legacy)
        throws Exception {
      var serverBuilder = new NettyServerTransportConfig.Builder();
      serverBuilder
          .setBindAddress("localhost")
          .setPort(0)
          .setEventLoopGroup(eventLoop)
          .setExecutor(executor)
          .setTlsEnabled(true)
          .setKeyManagerFactory(serverKeys)
          .setTrustManagerFactory(trust)
          .setSslContextCustomizer(
              b -> {
                serverHookCalls.incrementAndGet();
                serverPolicy.accept(b);
              })
          .setPipelineCustomizer(p -> serverHandler.complete(p.get(SslHandler.class)))
          .setBootstrapCustomizer(
              b ->
                  b.handler(
                      new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                          port.complete(
                              ((InetSocketAddress) ctx.channel().localAddress()).getPort());
                          ctx.fireChannelActive();
                        }
                      }));
      NettyServerTransportConfig serverConfig = serverBuilder.build();
      if (legacy) {
        serverConfig =
            new NettyServerTransportConfig(
                serverConfig.bindAddress(),
                serverConfig.port(),
                serverConfig.eventLoopGroup(),
                serverConfig.executor(),
                serverConfig.bootstrapCustomizer(),
                serverConfig.pipelineCustomizer(),
                serverConfig.tlsEnabled(),
                serverConfig.keyManagerFactory(),
                serverConfig.trustManagerFactory());
      }
      var image = new ProcessImage();
      var services =
          new ReadWriteModbusServices() {
            @Override
            protected Optional<ProcessImage> getProcessImage(int unitId) {
              return Optional.of(image);
            }
          };
      server =
          rtu
              ? ModbusRtuServer.create(new NettyRtuServerTransport(serverConfig), services)
              : ModbusTcpServer.create(new NettyTcpServerTransport(serverConfig), services);
      try {
        server.start();
        var clientBuilder = new NettyClientTransportConfig.Builder();
        clientBuilder
            .setHostname("localhost")
            .setPort(port.get(5, TimeUnit.SECONDS))
            .setEventLoopGroup(eventLoop)
            .setExecutor(executor)
            .setConnectPersistent(false)
            .setTlsEnabled(true)
            .setKeyManagerFactory(clientKeys)
            .setTrustManagerFactory(trust)
            .setSslContextCustomizer(
                b -> {
                  clientHookCalls.incrementAndGet();
                  clientPolicy.accept(b);
                })
            .setPipelineCustomizer(p -> clientHandler.complete(p.get(SslHandler.class)));
        NettyClientTransportConfig clientConfig = clientBuilder.build();
        if (legacy) {
          clientConfig =
              new NettyClientTransportConfig(
                  clientConfig.hostname(),
                  clientConfig.port(),
                  clientConfig.connectTimeout(),
                  clientConfig.connectPersistent(),
                  clientConfig.reconnectLazy(),
                  clientConfig.eventLoopGroup(),
                  clientConfig.executor(),
                  clientConfig.bootstrapCustomizer(),
                  clientConfig.pipelineCustomizer(),
                  clientConfig.channelFsmCustomizer(),
                  clientConfig.tlsEnabled(),
                  clientConfig.keyManagerFactory(),
                  clientConfig.trustManagerFactory());
        }
        client =
            rtu
                ? ModbusRtuClient.create(new NettyRtuClientTransport(clientConfig))
                : ModbusTcpClient.create(new NettyTcpClientTransport(clientConfig));
      } catch (Exception e) {
        try {
          server.stop();
        } finally {
          executor.shutdownNow();
          eventLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
        throw e;
      }
    }

    private void connectAndRead() throws Exception {
      clientHandler = new CompletableFuture<>();
      serverHandler = new CompletableFuture<>();
      client.connect();
      SslHandler clientTls = clientHandler.get(5, TimeUnit.SECONDS);
      clientTls.handshakeFuture().get(5, TimeUnit.SECONDS);
      SslHandler serverTls = serverHandler.get(5, TimeUnit.SECONDS);
      serverTls.handshakeFuture().get(5, TimeUnit.SECONDS);
      assertArrayEquals(
          new Certificate[] {serverIdentity.certificate()},
          clientTls.engine().getSession().getPeerCertificates());
      assertArrayEquals(
          new Certificate[] {clientIdentity.certificate()},
          serverTls.engine().getSession().getPeerCertificates());
      assertArrayEquals(
          new byte[] {0, 0},
          client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 1)).registers());
    }

    private void disconnect() throws Exception {
      client.disconnect();
      if (serverHandler.isDone()) {
        serverHandler
            .get(5, TimeUnit.SECONDS)
            .handshakeFuture()
            .get(5, TimeUnit.SECONDS)
            .closeFuture()
            .get(5, TimeUnit.SECONDS);
      }
    }

    @Override
    public void close() throws Exception {
      try {
        client.disconnect();
      } finally {
        try {
          server.stop();
        } finally {
          executor.shutdownNow();
          eventLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
      }
    }
  }
}
