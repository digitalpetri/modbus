package com.digitalpetri.modbus.codec;

import com.digitalpetri.modbus.ModbusPdu;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public interface ModbusPduDecoder {

    ModbusPdu decode(ByteBuf buffer) throws DecoderException;

}
