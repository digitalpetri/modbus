package com.digitalpetri.modbus.server;


import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusRtuRequestContext;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpRequestContext;
import java.net.SocketAddress;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * A transport-agnostic super-interface that transport implementations can subclass to smuggle
 * transport-specific context information to the application layer.
 */
public sealed interface ModbusRequestContext
    permits ModbusRtuRequestContext, ModbusTcpRequestContext {

  non-sealed interface ModbusTcpRequestContext extends ModbusRequestContext {

    /**
     * Get the local address that received the request.
     *
     * @return the local address that received the request.
     */
    SocketAddress localAddress();

    /**
     * Get the remote address of the client that sent the request.
     *
     * @return the remote address of the client that sent the request.
     */
    SocketAddress remoteAddress();

  }

  interface ModbusTcpTlsRequestContext extends ModbusTcpRequestContext {

    /**
     * Get the client's Role from its X509 certificate.
     *
     * @return the client's Role from its X509 certificate.
     */
    Optional<String> clientRole();

    /**
     * Get the client's X509 certificate chain.
     *
     * @return the client's X509 certificate chain.
     */
    X509Certificate[] clientCertificateChain();

  }

  non-sealed interface ModbusRtuRequestContext extends ModbusRequestContext {}

  interface ModbusRtuTcpRequestContext
      extends ModbusRtuRequestContext, ModbusTcpRequestContext {}

  interface ModbusRtuTlsRequestContext
      extends ModbusRtuRequestContext, ModbusTcpTlsRequestContext {}

}
