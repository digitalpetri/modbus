package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.FunctionCode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

public abstract class ByteBufModbusResponse extends DefaultByteBufHolder implements ModbusResponse {

    private final FunctionCode functionCode;

    protected ByteBufModbusResponse(ByteBuf data, FunctionCode functionCode) {
        super(data);

        this.functionCode = functionCode;
    }

    @Override
    public FunctionCode getFunctionCode() {
        return functionCode;
    }

}
