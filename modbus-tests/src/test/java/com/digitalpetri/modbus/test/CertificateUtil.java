package com.digitalpetri.modbus.test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Locale;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
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
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class CertificateUtil {

  private CertificateUtil() {}

  public static KeyPairCert generateSelfSignedCertificate(Role role) {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();

      var nameBuilder = new X500NameBuilder();
      if (role == Role.CLIENT) {
        nameBuilder.addRDN(BCStyle.CN, "Modbus Client");
      } else {
        nameBuilder.addRDN(BCStyle.CN, "Modbus Server");
      }
      X500Name name = nameBuilder.build();

      var certSerialNumber = new BigInteger(Long.toString(System.currentTimeMillis()));

      SubjectPublicKeyInfo subjectPublicKeyInfo =
          SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

      var certificateBuilder = new X509v3CertificateBuilder(
          name,
          certSerialNumber,
          new Date(),
          new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L),
          name,
          subjectPublicKeyInfo
      );

      var basicConstraints = new BasicConstraints(false);
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
              role == Role.CLIENT
                  ? KeyPurposeId.id_kp_clientAuth : KeyPurposeId.id_kp_serverAuth
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

      // Modbus Security Role OID
      if (role == Role.CLIENT) {
        certificateBuilder.addExtension(
            new ASN1ObjectIdentifier("1.3.6.1.4.1.50316.802.1"),
            false,
            new DEROctetString("Operator".getBytes())
        );
      }

      var contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
          .setProvider(new BouncyCastleProvider())
          .build(keyPair.getPrivate());

      X509CertificateHolder certificateHolder = certificateBuilder.build(contentSigner);

      X509Certificate certificate =
          new JcaX509CertificateConverter().getCertificate(certificateHolder);

      return new KeyPairCert(keyPair, certificate);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static KeyPairCert generateCaSignedCertificate(Role role, KeyPairCert caKeyPairCert) {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();

      var nameBuilder = new X500NameBuilder();
      if (role == Role.CLIENT) {
        nameBuilder.addRDN(BCStyle.CN, "Modbus Client");
      } else {
        nameBuilder.addRDN(BCStyle.CN, "Modbus Server");
      }
      X500Name name = nameBuilder.build();

      var certSerialNumber = new BigInteger(Long.toString(System.currentTimeMillis()));

      SubjectPublicKeyInfo subjectPublicKeyInfo =
          SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

      var certificateBuilder = new X509v3CertificateBuilder(
          new X500Name(caKeyPairCert.certificate().getSubjectX500Principal().getName()),
          certSerialNumber,
          new Date(),
          new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L),
          name,
          subjectPublicKeyInfo
      );

      var basicConstraints = new BasicConstraints(false);
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
              role == Role.CLIENT
                  ? KeyPurposeId.id_kp_clientAuth : KeyPurposeId.id_kp_serverAuth
          )
      );

      // Authority Key Identifier
      certificateBuilder.addExtension(
          Extension.authorityKeyIdentifier,
          false,
          new JcaX509ExtensionUtils()
              .createAuthorityKeyIdentifier(caKeyPairCert.certificate())
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

      // Modbus Security Role OID
      if (role == Role.CLIENT) {
        certificateBuilder.addExtension(
            new ASN1ObjectIdentifier("1.3.6.1.4.1.50316.802.1"),
            false,
            new DEROctetString("Operator".getBytes())
        );
      }

      var contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
          .setProvider(new BouncyCastleProvider())
          .build(caKeyPairCert.keyPair().getPrivate());

      X509CertificateHolder certificateHolder = certificateBuilder.build(contentSigner);

      X509Certificate certificate =
          new JcaX509CertificateConverter().getCertificate(certificateHolder);

      return new KeyPairCert(keyPair, certificate);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static KeyPairCert generateCaCertificate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();

      var nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
      nameBuilder.addRDN(BCStyle.CN, "Modbus CA");
      X500Name name = nameBuilder.build();

      var certSerialNumber = new BigInteger(Long.toString(System.currentTimeMillis()));

      SubjectPublicKeyInfo subjectPublicKeyInfo =
          SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

      var certificateBuilder = new X509v3CertificateBuilder(
          name,
          certSerialNumber,
          new Date(),
          new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L),
          Locale.ENGLISH,
          name,
          subjectPublicKeyInfo
      );

      var basicConstraints = new BasicConstraints(true);
      certificateBuilder.addExtension(Extension.basicConstraints, true, basicConstraints);

      // Key Usage
      certificateBuilder.addExtension(
          Extension.keyUsage,
          true,
          new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign)
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

      var contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
          .setProvider(new BouncyCastleProvider())
          .build(keyPair.getPrivate());

      X509CertificateHolder certificateHolder = certificateBuilder.build(contentSigner);

      X509Certificate certificate =
          new JcaX509CertificateConverter().getCertificate(certificateHolder);

      return new KeyPairCert(keyPair, certificate);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static KeyManagerFactory createKeyManagerFactory(
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

  public static TrustManagerFactory createTrustManagerFactory(X509Certificate... certificates) {
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

  public enum Role {
    CLIENT, SERVER
  }

  public record KeyPairCert(KeyPair keyPair, X509Certificate certificate) {}

}
