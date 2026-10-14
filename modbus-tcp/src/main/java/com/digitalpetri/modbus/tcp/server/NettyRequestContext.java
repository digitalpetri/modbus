package com.digitalpetri.modbus.tcp.server;

import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusRtuTlsRequestContext;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpTlsRequestContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;
import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * Combined {@link ModbusTcpTlsRequestContext} and {@link ModbusRtuTlsRequestContext} implementation
 * for Netty-based transports.
 *
 * <p>Missing, empty, or malformed certificate role extensions yield {@link Optional#empty()}. The
 * application's authorization handler decides whether to authorize a client without a role.
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

      clientRole = decodeClientRole(bs);

      attr.set(clientRole);
    }

    if (clientRole.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(clientRole);
    }
  }

  static String decodeClientRole(byte[] extensionValue) {
    if (extensionValue == null) {
      return "";
    }

    try {
      // X509Certificate.getExtensionValue() wraps the encoded UTF8String in an OCTET STRING.
      ByteBuffer octets = readDerValue(ByteBuffer.wrap(extensionValue), 0x04);
      ByteBuffer utf8 = readDerValue(octets, 0x0c);

      // The decoder reports invalid UTF-8 instead of inserting replacement characters.
      return StandardCharsets.UTF_8.newDecoder().decode(utf8).toString();
    } catch (IllegalArgumentException | CharacterCodingException e) {
      return "";
    }
  }

  private static ByteBuffer readDerValue(ByteBuffer data, int expectedTag) {
    if (data.remaining() < 2 || Byte.toUnsignedInt(data.get()) != expectedTag) {
      throw new IllegalArgumentException("Invalid DER tag or missing length");
    }

    int firstLengthByte = Byte.toUnsignedInt(data.get());
    long length = firstLengthByte;

    if ((firstLengthByte & 0x80) != 0) {
      int lengthBytes = firstLengthByte & 0x7f;

      if (lengthBytes == 0 || lengthBytes > 4 || lengthBytes > data.remaining()) {
        throw new IllegalArgumentException("Invalid DER length");
      }
      if (data.get(data.position()) == 0) {
        throw new IllegalArgumentException("Non-minimal DER length");
      }

      length = 0;
      for (int i = 0; i < lengthBytes; i++) {
        length = (length << 8) | Byte.toUnsignedInt(data.get());
      }

      if (length < 128) {
        throw new IllegalArgumentException("Non-minimal DER length");
      }
    }

    // Each layer must contain exactly one value, with no truncation or trailing data.
    if (length != data.remaining()) {
      throw new IllegalArgumentException("DER length does not match the available data");
    }

    return data.slice();
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
