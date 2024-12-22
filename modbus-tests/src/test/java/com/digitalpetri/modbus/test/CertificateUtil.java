package com.digitalpetri.modbus.test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Locale;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class CertificateUtil {

  private CertificateUtil() {}

  public static KeyMaterial generateSelfSignedClientCertificate(Role role) {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();

      X500NameBuilder nameBuilder = new X500NameBuilder();
      if (role == Role.CLIENT) {
        nameBuilder.addRDN(BCStyle.CN, "Modbus Client");
      } else {
        nameBuilder.addRDN(BCStyle.CN, "Modbus Server");
      }
      X500Name name = nameBuilder.build();

      BigInteger certSerialNumber = new BigInteger(Long.toString(System.currentTimeMillis()));

      SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(
          keyPair.getPublic().getEncoded()
      );

      X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(
          name,
          certSerialNumber,
          new Date(),
          new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L),
          Locale.ENGLISH,
          name,
          subjectPublicKeyInfo
      );

      BasicConstraints basicConstraints = new BasicConstraints(false);
      certificateBuilder.addExtension(Extension.basicConstraints, false, basicConstraints);

      // Key Usage
      certificateBuilder.addExtension(
          Extension.keyUsage,
          false,
          new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment)
      );

      // Extended Key Usage
      certificateBuilder.addExtension(
          Extension.extendedKeyUsage,
          false,
          new ExtendedKeyUsage(
              role == Role.CLIENT ?
                  KeyPurposeId.id_kp_clientAuth : KeyPurposeId.id_kp_serverAuth
          )
      );

      // Authority Key Identifier
      certificateBuilder.addExtension(
          Extension.authorityKeyIdentifier,
          false,
          new JcaX509ExtensionUtils()
              .createAuthorityKeyIdentifier(keyPair.getPublic())
      );

      // Subject Key Identifier
      certificateBuilder.addExtension(
          Extension.subjectKeyIdentifier,
          false,
          new JcaX509ExtensionUtils()
              .createSubjectKeyIdentifier(keyPair.getPublic())
      );

      // Subject Alternative Name
      if (role == Role.SERVER) {
        certificateBuilder.addExtension(
            Extension.subjectAlternativeName,
            false,
            new GeneralNames(new GeneralName[]{new GeneralName(GeneralName.dNSName, "localhost")})
        );
      }

      ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
          .setProvider(new BouncyCastleProvider())
          .build(keyPair.getPrivate());

      X509CertificateHolder certificateHolder = certificateBuilder.build(contentSigner);

      X509Certificate certificate =
          new JcaX509CertificateConverter().getCertificate(certificateHolder);

      return new KeyMaterial(keyPair, certificate);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public enum Role {
    CLIENT, SERVER
  }

  public record KeyMaterial(KeyPair keyPair, X509Certificate certificate) {}

}
