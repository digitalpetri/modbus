package com.digitalpetri.modbus.server.authz;

import java.security.cert.X509Certificate;
import java.util.Optional;

public interface AuthzContext {

  /**
   * Get the role of the client attempting the operation, if available.
   *
   * @return the role of the client attempting the operation, if available.
   */
  Optional<String> clientRole();

  /**
   * Get the client certificate chain.
   *
   * @return the client certificate chain.
   */
  X509Certificate[] clientCertificateChain();

}
