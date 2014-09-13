package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

public abstract class ByteBufModbusRequest extends DefaultByteBufHolder implements ModbusRequest {

    private final FunctionCode functionCode;

    public ByteBufModbusRequest(ByteBuf data, FunctionCode functionCode) {
        super(data);

        this.functionCode = functionCode;
    }

    @Override
    public FunctionCode getFunctionCode() {
        return functionCode;
    }

}
