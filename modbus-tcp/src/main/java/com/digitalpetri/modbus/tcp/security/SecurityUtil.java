package com.digitalpetri.modbus.tcp.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

public class SecurityUtil {

  /**
   * Create a {@link KeyManagerFactory} from a private key and certificates.
   *
   * @param privateKey the private key.
   * @param certificates the certificates.
   * @return a {@link KeyManagerFactory}.
   * @throws GeneralSecurityException if an error occurs.
   * @throws IOException if an error occurs.
   */
  public static KeyManagerFactory createKeyManagerFactory(
      PrivateKey privateKey,
      X509Certificate... certificates
  ) throws GeneralSecurityException, IOException {

    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null);

    keyStore.setKeyEntry("key", privateKey, new char[0], certificates);

    return createKeyManagerFactory(keyStore, new char[0]);
  }

  /**
   * Create a {@link KeyManagerFactory} from a {@link KeyStore}.
   *
   * @param keyStore the {@link KeyStore}.
   * @param keyStorePassword the password for the {@link KeyStore}.
   * @return a {@link KeyManagerFactory}.
   * @throws GeneralSecurityException if an error occurs.
   */
  public static KeyManagerFactory createKeyManagerFactory(
      KeyStore keyStore,
      char[] keyStorePassword
  ) throws GeneralSecurityException {

    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

    keyManagerFactory.init(keyStore, keyStorePassword);

    return keyManagerFactory;
  }

  /**
   * Create a {@link TrustManagerFactory} from certificates.
   *
   * @param certificates the certificates.
   * @return a {@link TrustManagerFactory}.
   * @throws GeneralSecurityException if an error occurs.
   * @throws IOException if an error occurs.
   */
  public static TrustManagerFactory createTrustManagerFactory(
      X509Certificate... certificates
  ) throws GeneralSecurityException, IOException {

    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null);

    for (int i = 0; i < certificates.length; i++) {
      keyStore.setCertificateEntry("cert" + i, certificates[i]);
    }

    return createTrustManagerFactory(keyStore);
  }

  /**
   * Create a {@link TrustManagerFactory} from a {@link KeyStore}.
   *
   * @param keyStore the {@link KeyStore}.
   * @return a {@link TrustManagerFactory}.
   * @throws GeneralSecurityException if an error occurs.
   */
  public static TrustManagerFactory createTrustManagerFactory(
      KeyStore keyStore
  ) throws GeneralSecurityException {

    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

    trustManagerFactory.init(keyStore);

    return trustManagerFactory;
  }

}
