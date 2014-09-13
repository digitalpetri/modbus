package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.FunctionCode;

public abstract class SimpleModbusResponse implements ModbusResponse {

    private final FunctionCode functionCode;

    protected SimpleModbusResponse(FunctionCode functionCode) {
        this.functionCode = functionCode;
    }

    @Override
    public FunctionCode getFunctionCode() {
        return functionCode;
    }

}
