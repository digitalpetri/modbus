package com.digitalpetri.modbus;

import java.util.Optional;

public enum FunctionCode {

    ReadCoils(0x01),
    ReadDiscreteInputs(0x02),
    ReadHoldingRegisters(0x03),
    ReadInputRegisters(0x04),
    WriteSingleCoil(0x05),
    WriteSingleRegister(0x06),
    WriteMultipleCoils(0x0F),
    WriteMultipleRegisters(0x10),
    MaskWriteRegister(0x16),
    UnsupportedFunction(0x00);

    private final int code;

    FunctionCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Optional<FunctionCode> fromCode(int code) {
        switch(code) {
            case 0x01: return Optional.of(ReadCoils);
            case 0x02: return Optional.of(ReadDiscreteInputs);
            case 0x03: return Optional.of(ReadHoldingRegisters);
            case 0x04: return Optional.of(ReadInputRegisters);
        }
        return Optional.empty();
    }

    public static boolean isExceptionCode(int code) {
        return fromCode(code - 0x80).isPresent();
    }
}
