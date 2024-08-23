package com.digitalpetri.modbus.server;

import static com.digitalpetri.modbus.server.ModbusRtuServerTransport.ModbusRtuRequestContext;

import com.digitalpetri.modbus.ModbusRtuFrame;
import java.net.SocketAddress;

/**
 * Modbus/RTU server transport; a {@link ModbusServerTransport} that sends and receives
 * {@link ModbusRtuFrame}s.
 */
public interface ModbusRtuServerTransport extends
    ModbusServerTransport<ModbusRtuRequestContext, ModbusRtuFrame> {

  non-sealed interface ModbusRtuRequestContext extends ModbusRequestContext {}

  interface ModbusRtuTcpRequestContext extends ModbusRtuRequestContext {

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
