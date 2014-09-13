package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;

/**
 * This function is used to read the contents of a contiguous block of holding registers in a remote device. The
 * Request PDU specifies the starting register address and the number of registers. In the PDU Registers are addressed
 * starting at zero. Therefore registers numbered 1-16 are addressed as 0-15.
 */
public class ReadHoldingRegistersRequest extends SimpleModbusRequest {

    private final int address;
    private final int quantity;

    /**
     * @param address  0x0000 to 0xFFFF (0 to 65535)
     * @param quantity 0x0001 to 0x007D (1 to 125)
     */
    public ReadHoldingRegistersRequest(int address, int quantity) {
        super(FunctionCode.ReadHoldingRegisters);

        this.address = address;
        this.quantity = quantity;
    }

    public int getAddress() {
        return address;
    }

    public int getQuantity() {
        return quantity;
    }

}
