package com.digitalpetri.modbus.server;

import static com.digitalpetri.modbus.server.ModbusRtuServerTransport.ModbusRtuRequestContext;
import static com.digitalpetri.modbus.server.ModbusTcpServerTransport.ModbusTcpRequestContext;

/**
 * A transport-agnostic super-interface that transport implementations can subclass to smuggle
 * transport-specific context information to the application layer.
 */
public sealed interface ModbusRequestContext
    permits ModbusRtuRequestContext, ModbusTcpRequestContext {}
