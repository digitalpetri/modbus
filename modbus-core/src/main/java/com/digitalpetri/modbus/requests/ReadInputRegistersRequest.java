package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;

public class ReadInputRegistersRequest extends ModbusRequest {

    private final int startAddress;
    private final int quantity;

    public ReadInputRegistersRequest(int startAddress, int quantity) {
        super(FunctionCode.ReadInputRegisters);

        this.startAddress = startAddress;
        this.quantity = quantity;
    }

    public int getStartAddress() {
        return startAddress;
    }

    public int getQuantity() {
        return quantity;
    }

}
