package com.digitalpetri.modbus;

public class UnsupportedPdu implements ModbusPdu {

    private final FunctionCode functionCode;

    public UnsupportedPdu(FunctionCode functionCode) {
        this.functionCode = functionCode;
    }

    @Override
    public FunctionCode getFunctionCode() {
        return functionCode;
    }

}
