package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.FunctionCode;

/**
 * The normal response is an echo of the request, returned after the coil state has been written.
 */
public class WriteSingleCoilResponse extends SimpleModbusResponse {

    private final int address;
    private final int value;

    /**
     * @param address 0x0000 to 0xFFFF (0 to 65535)
     * @param value   true or false (0xFF00 or 0x0000)
     */
    public WriteSingleCoilResponse(int address, int value) {
        super(FunctionCode.WriteSingleCoil);

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
