package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.FunctionCode;
import io.netty.buffer.ByteBuf;

public class ReadDiscreteInputsResponse extends ByteBufModbusResponse {

    public ReadDiscreteInputsResponse(ByteBuf data) {
        super(data, FunctionCode.ReadDiscreteInputs);
    }

    /**
     * The discrete inputs in the response message are packed as one input per bit of the data field. Status is
     * indicated as 1=ON; 0=OFF. The LSB of the first data byte contains the input addressed in the query. The other
     * inputs follow toward the high order end of this byte, and from low order to high order in subsequent bytes.
     * <p>
     * If the returned input quantity is not a multiple of eight, the remaining bits in the final data byte will be
     * padded with zeros (toward the high order end of the byte).
     *
     * @return the {@link ByteBuf} containing input status, 8 inputs per byte.
     */
    public ByteBuf getInputStatus() {
        return super.content();
    }

}
