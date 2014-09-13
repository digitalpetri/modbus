package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;

/**
 * This function is used to write a single output to either ON or OFF in a remote device.
 * <p>
 * The requested ON/OFF state is specified by a constant in the request data field. A value of 0xFF00 requests the
 * output to be ON. A value of 0x0000 requests it to be OFF. All other values are illegal and will not affect the output.
 * <p>
 * The Request PDU specifies the address of the coil to be forced. Coils are addressed starting at zero. Therefore coil
 * numbered 1 is addressed as 0. The requested ON/OFF state is specified by a constant in the Coil Value field. A value
 * of 0XFF00 requests the coil to be ON. A value of 0X0000 requests the coil to be off. All other values are illegal and
 * will not affect the coil.
 */
public class WriteSingleCoilRequest extends SimpleModbusRequest {

    private final int address;
    private final int value;

    /**
     * @param address 0x0000 to 0xFFFF (0 to 65535)
     * @param value   true or false (0xFF00 or 0x0000)
     */
    public WriteSingleCoilRequest(int address, boolean value) {
        super(FunctionCode.WriteSingleCoil);

        this.address = address;
        this.value = value ? 0xFF00 : 0x0000;
    }

    public int getAddress() {
        return address;
    }

    public int getValue() {
        return value;
    }

}
