package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.FunctionCode;

/**
 * The normal response returns the function code, starting address, and quantity of registers written.
 */
public class WriteMultipleRegistersResponse extends SimpleModbusResponse {

    private final int address;
    private final int quantity;

    /**
     * @param address  0x0000 to 0xFFFF (0 to 65535)
     * @param quantity 0x0001 to 0x007B (1 to 123)
     */
    public WriteMultipleRegistersResponse(int address, int quantity) {
        super(FunctionCode.WriteMultipleRegisters);

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
