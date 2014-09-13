package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;

/**
 * This function is used to write to a single holding register in a remote device.
 * <p>
 * The Request PDU specifies the address of the register to be written. Registers are addressed starting at zero.
 * Therefore register numbered 1 is addressed as 0.
 */
public class WriteSingleRegisterRequest extends SimpleModbusRequest {

    private final int address;
    private final int value;

    /**
     * @param address 0x0000 to 0xFFFF (0 to 65535)
     * @param value   0x0000 to 0xFFFF (0 to 65535)
     */
    public WriteSingleRegisterRequest(int address, int value) {
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
