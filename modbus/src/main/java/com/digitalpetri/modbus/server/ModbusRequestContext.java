package com.digitalpetri.modbus.server;


import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusRtuRequestContext;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpRequestContext;
import com.digitalpetri.modbus.server.authz.AuthzContext;
import java.net.SocketAddress;

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

  interface ModbusTcpTlsRequestContext extends ModbusTcpRequestContext, AuthzContext {}

  non-sealed interface ModbusRtuRequestContext extends ModbusRequestContext {}

  interface ModbusRtuTcpRequestContext
      extends ModbusRtuRequestContext, ModbusTcpRequestContext {}

  interface ModbusRtuTlsRequestContext
      extends ModbusRtuRequestContext, ModbusTcpTlsRequestContext {}

}
