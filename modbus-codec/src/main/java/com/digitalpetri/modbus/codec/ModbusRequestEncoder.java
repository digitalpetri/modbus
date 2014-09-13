package com.digitalpetri.modbus.codec;

import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.requests.ReadCoilsRequest;
import com.digitalpetri.modbus.requests.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.requests.ReadInputRegistersRequest;
import io.netty.buffer.ByteBuf;

public class ModbusRequestEncoder implements ModbusPduEncoder {

    @Override
    public ByteBuf encode(ModbusPdu modbusPdu, ByteBuf buffer) {
        switch(modbusPdu.getFunctionCode()) {
            case ReadCoils:
                return encodeReadCoils((ReadCoilsRequest) modbusPdu, buffer);

            case ReadDiscreteInputs:
                return encodeReadDiscreteInputs((ReadDiscreteInputsRequest) modbusPdu, buffer);

            case ReadHoldingRegisters:
                return encodeReadHoldingRegisters((ReadHoldingRegistersRequest) modbusPdu, buffer);

            case ReadInputRegisters:
                return encodeReadInputRegisters((ReadInputRegistersRequest) modbusPdu, buffer);

            default:
                return buffer;
        }
    }

    public ByteBuf encodeReadCoils(ReadCoilsRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getStartAddress());
        buffer.writeShort(request.getCoilQuantity());

        return buffer;
    }

    public ByteBuf encodeReadDiscreteInputs(ReadDiscreteInputsRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getStartAddress());
        buffer.writeShort(request.getInputQuantity());

        return buffer;
    }

    public ByteBuf encodeReadHoldingRegisters(ReadHoldingRegistersRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getStartAddress());
        buffer.writeShort(request.getQuantity());

        return buffer;
    }

    public ByteBuf encodeReadInputRegisters(ReadInputRegistersRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getStartAddress());
        buffer.writeShort(request.getQuantity());

        return buffer;
    }

}
