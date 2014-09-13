package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;

public class ReadDiscreteInputsRequest extends ModbusRequest {

    private final int startAddress;
    private final int inputQuantity;

    public ReadDiscreteInputsRequest(int startAddress, int inputQuantity) {
        super(FunctionCode.ReadDiscreteInputs);

        this.startAddress = startAddress;
        this.inputQuantity = inputQuantity;
    }

    public int getStartAddress() {
        return startAddress;
    }

    public int getInputQuantity() {
        return inputQuantity;
    }

}
