package com.digitalpetri.modbus;

import java.util.Optional;

public enum ExceptionCode {

    IllegalFunction(0x01),
    IllegalDataAddress(0x02),
    IllegalDataValue(0x03),
    ServerDeviceFailure(0x04),
    Acknowledge(0x05),
    ServerDeviceBusy(0x06),
    MemoryParityError(0x08),
    GatewayPathUnavailable(0x0A),
    GatewayTargetDeviceFailedToResponse(0x0B);

    private final int code;

    ExceptionCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Optional<ExceptionCode> fromCode(int code) {
        return null;
    }

}
