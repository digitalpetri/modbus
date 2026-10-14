package com.digitalpetri.modbus.tcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.digitalpetri.modbus.test.CertificateUtil;
import com.digitalpetri.modbus.test.CertificateUtil.KeyPairCert;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import java.security.cert.X509Certificate;
import java.util.Optional;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

class NettyRequestContextCertificateTest {

  private static final KeyPairCert AUTHORITY_KEYS = CertificateUtil.generateCaCertificate();

  @Test
  void validRoleIsCached() throws Exception {
    String role = "Opérateur 操作员 😀".repeat(16);
    X509Certificate certificate = certificateWithRole(new DERUTF8String(role).getEncoded());

    assertCachedRole(certificate, Optional.of(role));
  }

  @Test
  void absentRoleIsEmptyAndCached() throws Exception {
    assertCachedRole(AUTHORITY_KEYS.certificate(), Optional.empty());
  }

  @Test
  void emptyRoleIsEmptyAndCached() throws Exception {
    assertCachedRole(certificateWithRole(new DERUTF8String("").getEncoded()), Optional.empty());
  }

  @Test
  void malformedRoleIsEmptyAndCached() throws Exception {
    // An OCTET STRING containing "Operator" is not the required UTF8String.
    X509Certificate certificate =
        certificateWithRole(ByteBufUtil.decodeHexDump("04084f70657261746f72"));

    assertCachedRole(certificate, Optional.empty());
  }

  private static void assertCachedRole(X509Certificate certificate, Optional<String> expected)
      throws Exception {
    var handler = new ChannelInboundHandlerAdapter();
    var channel = new EmbeddedChannel(handler);
    X509Certificate[] chain = {certificate};
    channel.attr(AttributeKey.<X509Certificate[]>valueOf("clientCertificateChain")).set(chain);

    try {
      var context = new NettyRequestContext(channel.pipeline().context(handler));
      assertEquals(expected, context.clientRole());

      // Subsequent request contexts on this channel must use the cached result, including absence.
      chain[0] =
          expected.isPresent()
              ? AUTHORITY_KEYS.certificate()
              : certificateWithRole(new DERUTF8String("Operator").getEncoded());

      var nextContext = new NettyRequestContext(channel.pipeline().context(handler));
      assertEquals(expected, nextContext.clientRole());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  private static X509Certificate certificateWithRole(byte[] encodedRole) throws Exception {
    var builder =
        new X509v3CertificateBuilder(
            new X509CertificateHolder(AUTHORITY_KEYS.certificate().getEncoded()));
    builder.addExtension(new ASN1ObjectIdentifier("1.3.6.1.4.1.50316.802.1"), false, encodedRole);

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withRSA").build(AUTHORITY_KEYS.keyPair().getPrivate());

    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }
}
