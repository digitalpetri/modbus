package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;

/**
 * This function code is used to read from 1 to 2000 contiguous status of discrete inputs in a remote device. The
 * Request PDU specifies the starting address, i.e. the address of the first input specified, and the number of inputs.
 * In the PDU Discrete Inputs are addressed starting at zero. Therefore Discrete inputs numbered 1-16 are addressed as
 * 0-15.
 */
public class ReadDiscreteInputsRequest extends SimpleModbusRequest {

    private final int address;
    private final int quantity;

    /**
     * @param address  0x0000 to 0xFFFF (0 to 65535)
     * @param quantity 0x0001 to 0x07D0 (1 to 2000)
     */
    public ReadDiscreteInputsRequest(int address, int quantity) {
        super(FunctionCode.ReadDiscreteInputs);

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
