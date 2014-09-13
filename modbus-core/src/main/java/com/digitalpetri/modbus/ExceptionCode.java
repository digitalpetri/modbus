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
        switch (code) {
            case 0x01: return Optional.of(IllegalFunction);
            case 0x02: return Optional.of(IllegalDataAddress);
            case 0x03: return Optional.of(IllegalDataValue);
            case 0x04: return Optional.of(ServerDeviceFailure);
            case 0x05: return Optional.of(Acknowledge);
            case 0x06: return Optional.of(ServerDeviceBusy);
            case 0x08: return Optional.of(MemoryParityError);
            case 0x0A: return Optional.of(GatewayPathUnavailable);
            case 0x0B: return Optional.of(GatewayTargetDeviceFailedToResponse);
        }

        return Optional.empty();
    }

}
