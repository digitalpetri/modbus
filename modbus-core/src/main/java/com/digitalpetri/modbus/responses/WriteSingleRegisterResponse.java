package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.FunctionCode;

/**
 * The normal response is an echo of the request, returned after the register contents have been written.
 */
public class WriteSingleRegisterResponse extends SimpleModbusResponse {

    private final int address;
    private final int value;

    /**
     * @param address 0x0000 to 0xFFFF (0 to 65535)
     * @param value   0x0000 to 0xFFFF (0 to 65535)
     */
    public WriteSingleRegisterResponse(int address, int value) {
        super(FunctionCode.WriteSingleRegister);

        this.address = address;
        this.value = value;
    }

    public int getAddress() {
        return address;
    }

    public int getValue() {
        return value;
    }

}
