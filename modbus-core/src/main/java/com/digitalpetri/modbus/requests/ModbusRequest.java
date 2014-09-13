package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.ModbusPdu;

public abstract class ModbusRequest implements ModbusPdu {

    private final FunctionCode functionCode;

    protected ModbusRequest(FunctionCode functionCode) {
        this.functionCode = functionCode;
    }

    @Override
    public FunctionCode getFunctionCode() {
        return functionCode;
    }

}
