/*
 * Copyright 2016 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.modbus.codec;

import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.responses.*;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;

public class ModbusResponseEncoder implements ModbusPduEncoder {

    @Override
    public ByteBuf encode(ModbusPdu modbusPdu, ByteBuf buffer) throws EncoderException {
        try {
            if (modbusPdu instanceof ExceptionResponse) {
                return encodeExceptionResponse((ExceptionResponse) modbusPdu, buffer);
            } else {
                switch (modbusPdu.getFunctionCode()) {
                    case ReadCoils:
                        return encodeReadCoils((ReadCoilsResponse) modbusPdu, buffer);

                    case ReadDiscreteInputs:
                        return encodeReadDiscreteInputs((ReadDiscreteInputsResponse) modbusPdu, buffer);

                    case ReadHoldingRegisters:
                        return encodeReadHoldingRegisters((ReadHoldingRegistersResponse) modbusPdu, buffer);

                    case ReadInputRegisters:
                        return encodeReadInputRegisters((ReadInputRegistersResponse) modbusPdu, buffer);

                    case WriteSingleCoil:
                        return encodeWriteSingleCoil((WriteSingleCoilResponse) modbusPdu, buffer);

                    case WriteSingleRegister:
                        return encodeWriteSingleRegister((WriteSingleRegisterResponse) modbusPdu, buffer);

                    case WriteMultipleCoils:
                        return encodeWriteMultipleCoils((WriteMultipleCoilsResponse) modbusPdu, buffer);

                    case WriteMultipleRegisters:
                        return encodeWriteMultipleRegisters((WriteMultipleRegistersResponse) modbusPdu, buffer);

                    case MaskWriteRegister:
                        return encodeMaskWriteRegister((MaskWriteRegisterResponse) modbusPdu, buffer);

                    case ReadWriteMultipleRegisters:
                        return encodeReadWriteMultipleRegisters((ReadWriteMultipleRegistersResponse) modbusPdu, buffer);

                    default:
                        throw new EncoderException("FunctionCode not supported: " + modbusPdu.getFunctionCode());
                }
            }
        } finally {
            ReferenceCountUtil.release(modbusPdu);
        }
    }

    private ByteBuf encodeExceptionResponse(ExceptionResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode() + 0x80);
        buffer.writeByte(response.getExceptionCode().getCode());

        return buffer;
    }

    private ByteBuf encodeReadCoils(ReadCoilsResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode());
        buffer.writeByte(response.getCoilStatus().readableBytes());
        buffer.writeBytes(response.getCoilStatus());

        return buffer;
    }

    private ByteBuf encodeReadDiscreteInputs(ReadDiscreteInputsResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode());
        buffer.writeByte(response.getInputStatus().readableBytes());
        buffer.writeBytes(response.getInputStatus());

        return buffer;
    }

    private ByteBuf encodeReadHoldingRegisters(ReadHoldingRegistersResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode());
        buffer.writeByte(response.getRegisters().readableBytes());
        buffer.writeBytes(response.getRegisters());

        return buffer;
    }

    private ByteBuf encodeReadInputRegisters(ReadInputRegistersResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode());
        buffer.writeByte(response.getRegisters().readableBytes());
        buffer.writeBytes(response.getRegisters());

        return buffer;
    }

    private ByteBuf encodeWriteSingleCoil(WriteSingleCoilResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode());
        buffer.writeShort(response.getAddress());
        buffer.writeShort(response.getValue());

        return buffer;
    }

    private ByteBuf encodeWriteSingleRegister(WriteSingleRegisterResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode());
        buffer.writeShort(response.getAddress());
        buffer.writeShort(response.getValue());

        return buffer;
    }

    private ByteBuf encodeWriteMultipleCoils(WriteMultipleCoilsResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode());
        buffer.writeShort(response.getAddress());
        buffer.writeShort(response.getQuantity());

        return buffer;
    }

    private ByteBuf encodeWriteMultipleRegisters(WriteMultipleRegistersResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode());
        buffer.writeShort(response.getAddress());
        buffer.writeShort(response.getQuantity());

        return buffer;
    }

    private ByteBuf encodeMaskWriteRegister(MaskWriteRegisterResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode());
        buffer.writeShort(response.getAddress());
        buffer.writeShort(response.getAndMask());
        buffer.writeShort(response.getOrMask());

        return buffer;
    }

    private ByteBuf encodeReadWriteMultipleRegisters(ReadWriteMultipleRegistersResponse response, ByteBuf buffer) {
        buffer.writeByte(response.getFunctionCode().getCode());
        buffer.writeByte(response.getRegisters().readableBytes());
        buffer.writeBytes(response.getRegisters());

        return buffer;
    }
}
