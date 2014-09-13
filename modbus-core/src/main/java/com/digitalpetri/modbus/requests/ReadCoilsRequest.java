package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;

/**
 * This function is used to read from 1 to 2000 contiguous status of coils in a remote device. The Request PDU
 * specifies the starting address, i.e. the address of the first coil specified, and the number of coils. In the PDU
 * Coils are addressed starting at zero. Therefore coils numbered 1-16 are addressed as 0-15.
 */
public class ReadCoilsRequest extends SimpleModbusRequest {

    private final int address;
    private final int quantity;

    /**
     * @param address  0x0000 to 0xFFFF (0 to 65535)
     * @param quantity 0x0001 to 0x07D0 (1 to 2000)
     */
    public ReadCoilsRequest(int address, int quantity) {
        super(FunctionCode.ReadCoils);

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
