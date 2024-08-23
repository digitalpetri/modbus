package com.digitalpetri.modbus.server;

import static com.digitalpetri.modbus.server.ModbusTcpServerTransport.ModbusTcpRequestContext;

import com.digitalpetri.modbus.ModbusTcpFrame;
import java.net.SocketAddress;

/**
 * Modbus/TCP server transport; a {@link ModbusServerTransport} that sends and receives
 * {@link ModbusTcpFrame}s.
 */
public interface ModbusTcpServerTransport
    extends ModbusServerTransport<ModbusTcpRequestContext, ModbusTcpFrame> {

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

}
