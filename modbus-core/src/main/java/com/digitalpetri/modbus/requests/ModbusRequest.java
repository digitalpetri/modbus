package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.ModbusPdu;

public interface ModbusRequest extends ModbusPdu {

    FunctionCode getFunctionCode();

}
