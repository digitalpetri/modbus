package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;

public class ReadCoilsRequest extends ModbusRequest {

    private final int startAddress;
    private final int coilQuantity;

    public ReadCoilsRequest(int startAddress, int coilQuantity) {
        super(FunctionCode.ReadCoils);

        this.startAddress = startAddress;
        this.coilQuantity = coilQuantity;
    }

    public int getStartAddress() {
        return startAddress;
    }

    public int getCoilQuantity() {
        return coilQuantity;
    }

}
