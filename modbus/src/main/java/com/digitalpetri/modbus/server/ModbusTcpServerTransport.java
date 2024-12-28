package com.digitalpetri.modbus.server;

import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpRequestContext;

/**
 * Modbus/TCP server transport; a {@link ModbusServerTransport} that sends and receives {@link
 * ModbusTcpFrame}s.
 */
public interface ModbusTcpServerTransport
    extends ModbusServerTransport<ModbusTcpRequestContext, ModbusTcpFrame> {}
