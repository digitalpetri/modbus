package com.digitalpetri.modbus;

import com.digitalpetri.modbus.responses.ExceptionResponse;

public class ModbusResponseException extends Exception {

    private final ExceptionResponse response;

    public ModbusResponseException(ExceptionResponse response) {
        this.response = response;
    }

    public ExceptionResponse getResponse() {
        return response;
    }

    @Override
    public String getMessage() {
        return String.format("functionCode=%s, exceptionCode=%s",
                             response.getFunctionCode(), response.getExceptionCode());
    }

}
