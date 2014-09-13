package com.digitalpetri.modbus.codec;

import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.UnsupportedPdu;
import com.digitalpetri.modbus.responses.ExceptionResponse;
import com.digitalpetri.modbus.responses.ReadCoilsResponse;
import com.digitalpetri.modbus.responses.ReadDiscreteInputsResponse;
import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.responses.ReadInputRegistersResponse;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public class ModbusResponseDecoder implements ModbusPduDecoder {

    @Override
    public ModbusPdu decode(ByteBuf buffer) throws DecoderException {
        int code = buffer.readByte();

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
        int code = buffer.readByte();

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

}
