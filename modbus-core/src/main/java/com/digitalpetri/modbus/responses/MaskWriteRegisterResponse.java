package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.FunctionCode;

/**
 * The normal response is an echo of the request. The response is returned after the register has been written.
 */
public class MaskWriteRegisterResponse extends SimpleModbusResponse {

    private final int address;
    private final int andMask;
    private final int orMask;

    /**
     * @param address 0x0000 to 0xFFFF (0 to 65535)
     * @param andMask 0x0000 to 0xFFFF (0 to 65535)
     * @param orMask  0x0000 to 0xFFFF (0 to 65535)
     */
    public MaskWriteRegisterResponse(int address, int andMask, int orMask) {
        super(FunctionCode.MaskWriteRegister);

        this.address = address;
        this.andMask = andMask;
        this.orMask = orMask;
    }

    public int getAddress() {
        return address;
    }

    public int getAndMask() {
        return andMask;
    }

    public int getOrMask() {
        return orMask;
    }

}
