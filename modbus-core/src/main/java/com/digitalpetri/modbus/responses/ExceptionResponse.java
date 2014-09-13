package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.FunctionCode;

public class ExceptionResponse extends SimpleModbusResponse {

    private final ExceptionCode exceptionCode;

    public ExceptionResponse(FunctionCode functionCode, ExceptionCode exceptionCode) {
        super(functionCode);

        this.exceptionCode = exceptionCode;
    }

    public ExceptionCode getExceptionCode() {
        return exceptionCode;
    }

}
