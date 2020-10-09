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

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.UnsupportedPdu;
import com.digitalpetri.modbus.requests.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.requests.ReadCoilsRequest;
import com.digitalpetri.modbus.requests.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.requests.ReadInputRegistersRequest;
import com.digitalpetri.modbus.requests.ReadWriteMultipleRegistersRequest;
import com.digitalpetri.modbus.requests.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.requests.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.requests.WriteSingleCoilRequest;
import com.digitalpetri.modbus.requests.WriteSingleRegisterRequest;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public class ModbusRequestDecoder implements ModbusPduDecoder {

    @Override
    public ModbusPdu decode(ByteBuf buffer) throws DecoderException {
        int code = buffer.readByte();

        FunctionCode functionCode = FunctionCode
            .fromCode(code)
            .orElseThrow(() -> new DecoderException("invalid function code: " + code));

        return decodeResponse(functionCode, buffer);
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

            case ReadWriteMultipleRegisters:
                return decodeReadWriteMultipleRegisters(buffer);

            default:
                return new UnsupportedPdu(functionCode);
        }
    }

    private ReadCoilsRequest decodeReadCoils(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int quantity = buffer.readUnsignedShort();

        return new ReadCoilsRequest(address, quantity);
    }

    private ReadDiscreteInputsRequest decodeReadDiscreteInputs(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int quantity = buffer.readUnsignedShort();

        return new ReadDiscreteInputsRequest(address, quantity);
    }

    private ReadHoldingRegistersRequest decodeReadHoldingRegisters(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int quantity = buffer.readUnsignedShort();

        return new ReadHoldingRegistersRequest(address, quantity);
    }

    private ReadInputRegistersRequest decodeReadInputRegisters(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int quantity = buffer.readUnsignedShort();

        return new ReadInputRegistersRequest(address, quantity);
    }

    private WriteSingleCoilRequest decodeWriteSingleCoil(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        boolean value = buffer.readUnsignedShort() == 0xFF00;

        return new WriteSingleCoilRequest(address, value);
    }

    private WriteSingleRegisterRequest decodeWriteSingleRegister(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int value = buffer.readUnsignedShort();

        return new WriteSingleRegisterRequest(address, value);
    }

    private WriteMultipleCoilsRequest decodeWriteMultipleCoils(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int quantity = buffer.readUnsignedShort();
        int byteCount = buffer.readUnsignedByte();
        ByteBuf values = buffer.readSlice(byteCount).retain();

        return new WriteMultipleCoilsRequest(address, quantity, values);
    }

    private WriteMultipleRegistersRequest decodeWriteMultipleRegisters(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int quantity = buffer.readUnsignedShort();
        int byteCount = buffer.readUnsignedByte();
        ByteBuf values = buffer.readSlice(byteCount).retain();

        return new WriteMultipleRegistersRequest(address, quantity, values);
    }

    private MaskWriteRegisterRequest decodeMaskWriteRegister(ByteBuf buffer) {
        int address = buffer.readUnsignedShort();
        int andMask = buffer.readUnsignedShort();
        int orMask = buffer.readUnsignedShort();

        return new MaskWriteRegisterRequest(address, andMask, orMask);
    }

    private ReadWriteMultipleRegistersRequest decodeReadWriteMultipleRegisters(ByteBuf buffer) {
        int readAddress = buffer.readUnsignedShort();
        int readQuantity = buffer.readUnsignedShort();
        int writeAddress = buffer.readUnsignedShort();
        int writeQuantity = buffer.readUnsignedShort();
        int byteCount = buffer.readUnsignedByte();
        ByteBuf values = buffer.readSlice(byteCount).retain();

        return new ReadWriteMultipleRegistersRequest(readAddress, readQuantity, writeAddress, writeQuantity, values);
    }

}
