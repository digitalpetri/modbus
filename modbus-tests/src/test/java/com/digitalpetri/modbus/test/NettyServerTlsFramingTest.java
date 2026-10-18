package com.digitalpetri.modbus.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.server.ModbusRtuServer;
import com.digitalpetri.modbus.server.ModbusServer;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.security.SecurityUtil;
import com.digitalpetri.modbus.tcp.server.NettyRtuServerTransport;
import com.digitalpetri.modbus.tcp.server.NettyServerTransportConfig;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import com.digitalpetri.modbus.test.CertificateUtil.KeyPairCert;
import com.digitalpetri.modbus.test.CertificateUtil.Role;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Partial request frame recovery over TLS with the default partial frame timeout of 100 ms: a
 * request split by 20 ms of silence is answered, and a partial request followed by 300 ms of
 * silence is discarded so the next complete request on the same connection is answered.
 */
@Timeout(30)
class NettyServerTlsFramingTest {

  private static final KeyPairCert AUTHORITY = CertificateUtil.generateCaCertificate();
  private static final KeyPairCert SERVER =
      CertificateUtil.generateCaSignedCertificate(Role.SERVER, AUTHORITY);
  private static final KeyPairCert CLIENT =
      CertificateUtil.generateCaSignedCertificate(Role.CLIENT, AUTHORITY);

  private final CompletableFuture<Integer> port = new CompletableFuture<>();
  private ModbusServer server;
  private SSLSocket socket;

  @AfterEach
  void tearDown() throws Exception {
    if (socket != null) {
      socket.close();
    }
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void modbusTcpRecoversFromPartialRequest() throws Exception {
    server = ModbusTcpServer.create(new NettyTcpServerTransport(serverConfig()), services());
    server.start();
    connect();

    byte[] request = hex("002a00000006010300000001");
    write(request, 0, 6);
    Thread.sleep(20);
    write(request, 6, 6);
    assertResponse(hex("002a000000050103020000"));

    write(hex("002b00000006010300000001"), 0, 6);
    Thread.sleep(300);
    write(hex("002c00000006010300000001"), 0, 12);
    assertResponse(hex("002c000000050103020000"));
    assertSilent();
  }

  @Test
  void rtuOverTcpRecoversFromPartialRequest() throws Exception {
    server = ModbusRtuServer.create(new NettyRtuServerTransport(serverConfig()), services());
    server.start();
    connect();

    byte[] request = hex("010300000001840a");
    write(request, 0, 4);
    Thread.sleep(20);
    write(request, 4, 4);
    assertResponse(hex("0103020000b844"));

    write(request, 0, 4);
    Thread.sleep(300);
    write(request, 0, 8);
    assertResponse(hex("0103020000b844"));
    assertSilent();
  }

  private NettyServerTransportConfig serverConfig() throws Exception {
    KeyManagerFactory keyManagerFactory =
        SecurityUtil.createKeyManagerFactory(SERVER.keyPair().getPrivate(), SERVER.certificate());
    TrustManagerFactory trustManagerFactory =
        SecurityUtil.createTrustManagerFactory(AUTHORITY.certificate());

    return NettyServerTransportConfig.create(
        b ->
            b.setBindAddress("127.0.0.1")
                .setPort(0)
                .setTlsEnabled(true)
                .setKeyManagerFactory(keyManagerFactory)
                .setTrustManagerFactory(trustManagerFactory)
                .setBootstrapCustomizer(
                    bootstrap ->
                        bootstrap.handler(
                            new ChannelInboundHandlerAdapter() {
                              @Override
                              public void channelActive(ChannelHandlerContext ctx) {
                                port.complete(
                                    ((InetSocketAddress) ctx.channel().localAddress()).getPort());
                                ctx.fireChannelActive();
                              }
                            })));
  }

  private static ReadWriteModbusServices services() {
    var image = new ProcessImage();
    return new ReadWriteModbusServices() {
      @Override
      protected Optional<ProcessImage> getProcessImage(int unitId) {
        return Optional.of(image);
      }
    };
  }

  private void connect() throws Exception {
    KeyManagerFactory keyManagerFactory =
        SecurityUtil.createKeyManagerFactory(CLIENT.keyPair().getPrivate(), CLIENT.certificate());
    TrustManagerFactory trustManagerFactory =
        SecurityUtil.createTrustManagerFactory(AUTHORITY.certificate());

    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

    Integer serverPort = port.get(5, TimeUnit.SECONDS);
    assertNotNull(serverPort);
    socket = (SSLSocket) context.getSocketFactory().createSocket("127.0.0.1", serverPort);
    // Send each write immediately; with Nagle, a write can wait for the server's delayed ACK.
    socket.setTcpNoDelay(true);
    socket.setSoTimeout(2000);
    socket.startHandshake();
  }

  /** Write part of {@code data}; each write is sent as its own TLS record. */
  private void write(byte[] data, int offset, int length) throws Exception {
    OutputStream out = socket.getOutputStream();
    out.write(data, offset, length);
    out.flush();
  }

  private void assertResponse(byte[] expected) throws Exception {
    InputStream in = socket.getInputStream();
    assertArrayEquals(expected, in.readNBytes(expected.length));
  }

  private void assertSilent() throws Exception {
    socket.setSoTimeout(150);
    try {
      assertThrows(SocketTimeoutException.class, () -> socket.getInputStream().read());
    } finally {
      socket.setSoTimeout(2000);
    }
  }

  private static byte[] hex(String value) {
    return HexFormat.of().parseHex(value);
  }
}
