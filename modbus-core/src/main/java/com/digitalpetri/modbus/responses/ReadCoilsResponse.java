package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.FunctionCode;
import io.netty.buffer.ByteBuf;

public class ReadCoilsResponse extends ByteBufModbusResponse {

    public ReadCoilsResponse(ByteBuf coilStatus) {
        super(coilStatus, FunctionCode.ReadCoils);
    }

    /**
     * The coils in the response message are packed as one coil per bit of the data field. Status is indicated as 1=ON
     * and 0=OFF. The LSB of the first data byte contains the output addressed in the query. The other coils follow
     * toward the high order end of this byte, and from low order to high order in subsequent bytes.
     * <p>
     * If the returned output quantity is not a multiple of eight, the remaining bits in the final data byte will be
     * padded with zeros (toward the high order end of the byte).
     *
     * @return the {@link ByteBuf} containing coil status, 8 coils per byte.
     */
    public ByteBuf getCoilStatus() {
        return super.content();
    }

}
