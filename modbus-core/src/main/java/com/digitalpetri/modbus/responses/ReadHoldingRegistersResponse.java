package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.FunctionCode;
import io.netty.buffer.ByteBuf;

public class ReadHoldingRegistersResponse extends ByteBufModbusResponse {

    public ReadHoldingRegistersResponse(ByteBuf registers) {
        super(registers, FunctionCode.ReadHoldingRegisters);
    }

    /**
     * The register data in the response message are packed as two bytes per register, with the binary contents right
     * justified within each byte. For each register, the first byte contains the high order bits and the second
     * contains the low order bits.
     *
     * @return the {@link ByteBuf} containing register data, two bytes per register.
     */
    public ByteBuf getRegisters() {
        return super.content();
    }

}
