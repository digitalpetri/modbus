package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;

public class ReadHoldingRegistersRequest extends ModbusRequest {

    private final int startAddress;
    private final int quantity;

    public ReadHoldingRegistersRequest(int startAddress, int quantity) {
        super(FunctionCode.ReadHoldingRegisters);

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
