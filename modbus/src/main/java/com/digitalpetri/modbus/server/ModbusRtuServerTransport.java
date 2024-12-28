package com.digitalpetri.modbus.server;

import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusRtuRequestContext;

/**
 * Modbus/RTU server transport; a {@link ModbusServerTransport} that sends and receives {@link
 * ModbusRtuFrame}s.
 */
public interface ModbusRtuServerTransport
    extends ModbusServerTransport<ModbusRtuRequestContext, ModbusRtuFrame> {}
