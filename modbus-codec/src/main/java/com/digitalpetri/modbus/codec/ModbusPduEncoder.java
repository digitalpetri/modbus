package com.digitalpetri.modbus.codec;

import com.digitalpetri.modbus.ModbusPdu;
import io.netty.buffer.ByteBuf;

public interface ModbusPduEncoder {

    ByteBuf encode(ModbusPdu modbusPdu, ByteBuf buffer);

}
