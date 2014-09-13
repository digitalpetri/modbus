package com.digitalpetri.modbus.codec;

import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.requests.ReadCoilsRequest;
import com.digitalpetri.modbus.requests.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.requests.ReadInputRegistersRequest;
import com.digitalpetri.modbus.requests.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.requests.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.requests.WriteSingleCoilRequest;
import com.digitalpetri.modbus.requests.WriteSingleRegisterRequest;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;

public class ModbusRequestEncoder implements ModbusPduEncoder {

    @Override
    public ByteBuf encode(ModbusPdu modbusPdu, ByteBuf buffer) {
        try {
            switch (modbusPdu.getFunctionCode()) {
                case ReadCoils:
                    return encodeReadCoils((ReadCoilsRequest) modbusPdu, buffer);

                case ReadDiscreteInputs:
                    return encodeReadDiscreteInputs((ReadDiscreteInputsRequest) modbusPdu, buffer);

                case ReadHoldingRegisters:
                    return encodeReadHoldingRegisters((ReadHoldingRegistersRequest) modbusPdu, buffer);

                case ReadInputRegisters:
                    return encodeReadInputRegisters((ReadInputRegistersRequest) modbusPdu, buffer);

                case WriteSingleCoil:
                    return encodeWriteSingleCoil((WriteSingleCoilRequest) modbusPdu, buffer);

                case WriteSingleRegister:
                    return encodeWriteSingleRegister((WriteSingleRegisterRequest) modbusPdu, buffer);

                case WriteMultipleCoils:
                    return encodeWriteMultipleCoils((WriteMultipleCoilsRequest) modbusPdu, buffer);

                case WriteMultipleRegisters:
                    return encodeWriteMultipleRegisters((WriteMultipleRegistersRequest) modbusPdu, buffer);

                default:
                    return buffer;
            }
        } finally {
            ReferenceCountUtil.release(modbusPdu);
        }
    }

    public ByteBuf encodeReadCoils(ReadCoilsRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getAddress());
        buffer.writeShort(request.getQuantity());

        return buffer;
    }

    public ByteBuf encodeReadDiscreteInputs(ReadDiscreteInputsRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getAddress());
        buffer.writeShort(request.getQuantity());

        return buffer;
    }

    public ByteBuf encodeReadHoldingRegisters(ReadHoldingRegistersRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getAddress());
        buffer.writeShort(request.getQuantity());

        return buffer;
    }

    public ByteBuf encodeReadInputRegisters(ReadInputRegistersRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getAddress());
        buffer.writeShort(request.getQuantity());

        return buffer;
    }

    public ByteBuf encodeWriteSingleCoil(WriteSingleCoilRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getAddress());
        buffer.writeShort(request.getValue());

        return buffer;
    }

    public ByteBuf encodeWriteSingleRegister(WriteSingleRegisterRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getAddress());
        buffer.writeShort(request.getValue());

        return buffer;
    }

    public ByteBuf encodeWriteMultipleCoils(WriteMultipleCoilsRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getAddress());
        buffer.writeShort(request.getQuantity());

        int byteCount = (request.getQuantity() / 8) + (request.getQuantity() % 8);
        buffer.writeByte(byteCount);

        buffer.writeBytes(request.getValues(), byteCount);

        return buffer;
    }

    public ByteBuf encodeWriteMultipleRegisters(WriteMultipleRegistersRequest request, ByteBuf buffer) {
        buffer.writeByte(request.getFunctionCode().getCode());
        buffer.writeShort(request.getAddress());
        buffer.writeShort(request.getQuantity());

        int byteCount = request.getQuantity() * 2;
        buffer.writeByte(byteCount);

        buffer.writeBytes(request.getValues(), byteCount);

        return buffer;
    }

}
