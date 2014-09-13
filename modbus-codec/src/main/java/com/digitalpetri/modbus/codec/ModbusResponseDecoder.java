/*
 * Copyright 2014 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.modbus.codec;

import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.UnsupportedPdu;
import com.digitalpetri.modbus.responses.ExceptionResponse;
import com.digitalpetri.modbus.responses.MaskWriteRegisterResponse;
import com.digitalpetri.modbus.responses.ReadCoilsResponse;
import com.digitalpetri.modbus.responses.ReadDiscreteInputsResponse;
import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.responses.ReadInputRegistersResponse;
import com.digitalpetri.modbus.responses.WriteMultipleCoilsResponse;
import com.digitalpetri.modbus.responses.WriteMultipleRegistersResponse;
import com.digitalpetri.modbus.responses.WriteSingleCoilResponse;
import com.digitalpetri.modbus.responses.WriteSingleRegisterResponse;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public class ModbusResponseDecoder implements ModbusPduDecoder {

    @Override
    public ModbusPdu decode(ByteBuf buffer) throws DecoderException {
        int code = buffer.readUnsignedByte();

        if (FunctionCode.isExceptionCode(code)) {
            FunctionCode functionCode = FunctionCode
                    .fromCode(code - 0x80)
                    .orElseThrow(() -> new DecoderException("invalid function code: " + (code - 0x80)));

            return decodeException(functionCode, buffer);
        } else {
            FunctionCode functionCode = FunctionCode
                    .fromCode(code)
                    .orElseThrow(() -> new DecoderException("invalid function code: " + code));

            return decodeResponse(functionCode, buffer);
        }
    }

    private ModbusPdu decodeException(FunctionCode functionCode, ByteBuf buffer) throws DecoderException {
        int code = buffer.readUnsignedByte();

        ExceptionCode exceptionCode = ExceptionCode
                .fromCode(code)
                .orElseThrow(() -> new DecoderException("invalid exception code: " + code));

        return new ExceptionResponse(functionCode, exceptionCode);
    }

    private ModbusPdu decodeResponse(FunctionCode functionCode, ByteBuf buffer) throws DecoderException {
        switch (functionCode) {
            case ReadCoils:
                return decodeReadCoils(buffer);

            case ReadDiscreteInputs:
                return decodeReadDiscreteInputs(buffer);

            case ReadHoldingRegisters:
                return decodeReadHoldingRegisters(buffer);

            case ReadInputRegisters:
                return decodeReadInputRegisters(buffer);

            case WriteSingleCoil:
                return decodeWriteSingleCoil(buffer);

            case WriteSingleRegister:
                return decodeWriteSingleRegister(buffer);

            case WriteMultipleCoils:
                return decodeWriteMultipleCoils(buffer);

            case WriteMultipleRegisters:
                return decodeWriteMultipleRegisters(buffer);

            case MaskWriteRegister:
                return decodeMaskWriteRegister(buffer);

            default:
                return new UnsupportedPdu(functionCode);
        }
    }

    public ReadCoilsResponse decodeReadCoils(ByteBuf buffer) {
        int byteCount = buffer.readUnsignedByte();
        ByteBuf coilStatus = buffer.readSlice(byteCount).retain();

        return new ReadCoilsResponse(coilStatus);
    }

    public ReadDiscreteInputsResponse decodeReadDiscreteInputs(ByteBuf buffer) {
        int byteCount = buffer.readUnsignedByte();
        ByteBuf inputStatus = buffer.readSlice(byteCount).retain();

        return new ReadDiscreteInputsResponse(inputStatus);
    }

    public ReadHoldingRegistersResponse decodeReadHoldingRegisters(ByteBuf buffer) {
        int byteCount = buffer.readUnsignedByte();
        ByteBuf registers = buffer.readSlice(byteCount).retain();

        return new ReadHoldingRegistersResponse(registers);
    }

    public ReadInputRegistersResponse decodeReadInputRegisters(ByteBuf buffer) {
        int byteCount = buffer.readUnsignedByte();
        ByteBuf registers = buffer.readSlice(byteCount).retain();

        return new ReadInputRegistersResponse(registers);
    }

    public WriteSingleCoilResponse decodeWriteSingleCoil(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int value = buffer.readUnsignedShort();

        return new WriteSingleCoilResponse(address, value);
    }

    public WriteSingleRegisterResponse decodeWriteSingleRegister(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int value = buffer.readUnsignedShort();

        return new WriteSingleRegisterResponse(address, value);
    }

    public WriteMultipleCoilsResponse decodeWriteMultipleCoils(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int quantity = buffer.readUnsignedShort();

        return new WriteMultipleCoilsResponse(address, quantity);
    }

    public WriteMultipleRegistersResponse decodeWriteMultipleRegisters(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int quantity = buffer.readUnsignedShort();

        return new WriteMultipleRegistersResponse(address, quantity);
    }

    public MaskWriteRegisterResponse decodeMaskWriteRegister(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int andMask = buffer.readUnsignedShort();
        int orMask = buffer.readUnsignedShort();

        return new MaskWriteRegisterResponse(address, andMask, orMask);
    }

}
