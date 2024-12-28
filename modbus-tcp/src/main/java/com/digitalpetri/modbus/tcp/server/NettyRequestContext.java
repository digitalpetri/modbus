package com.digitalpetri.modbus.tcp.server;

import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusRtuTlsRequestContext;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpTlsRequestContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;
import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * Combined {@link ModbusTcpTlsRequestContext} and {@link ModbusRtuTlsRequestContext} implementation
 * for Netty-based transports.
 */
class NettyRequestContext implements ModbusTcpTlsRequestContext, ModbusRtuTlsRequestContext {

  private static final AttributeKey<String> CLIENT_ROLE = AttributeKey.valueOf("clientRole");

  private static final AttributeKey<X509Certificate[]> CLIENT_CERTIFICATE_CHAIN =
      AttributeKey.valueOf("clientCertificateChain");

  private final ChannelHandlerContext ctx;

  NettyRequestContext(ChannelHandlerContext ctx) {
    this.ctx = ctx;
  }

  @Override
  public SocketAddress localAddress() {
    return ctx.channel().localAddress();
  }

  @Override
  public SocketAddress remoteAddress() {
    return ctx.channel().remoteAddress();
  }

  @Override
  public Optional<String> clientRole() {
    Attribute<String> attr = ctx.channel().attr(CLIENT_ROLE);

    String clientRole = attr.get();

    if (clientRole == null) {
      X509Certificate x509Certificate = clientCertificateChain()[0];

      byte[] bs = x509Certificate.getExtensionValue("1.3.6.1.4.1.50316.802.1");

      if (bs != null && bs.length >= 4) {
        // Strip the leading tag and length bytes.
        clientRole = new String(bs, 4, bs.length - 4);
      } else {
        clientRole = "";
      }

      attr.set(clientRole);
    }

    if (clientRole.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(clientRole);
    }
  }

  @Override
  public X509Certificate[] clientCertificateChain() {
    Attribute<X509Certificate[]> attr = ctx.channel().attr(CLIENT_CERTIFICATE_CHAIN);

    X509Certificate[] clientCertificateChain = attr.get();

    if (clientCertificateChain == null) {
      try {
        SslHandler handler = ctx.channel().pipeline().get(SslHandler.class);
        Certificate[] peerCertificates = handler.engine().getSession().getPeerCertificates();

        clientCertificateChain =
            Arrays.stream(peerCertificates)
                .map(cert -> (X509Certificate) cert)
                .toArray(X509Certificate[]::new);

        attr.set(clientCertificateChain);
      } catch (SSLPeerUnverifiedException e) {
        throw new RuntimeException(e);
      }
    }

    return clientCertificateChain;
  }
}
