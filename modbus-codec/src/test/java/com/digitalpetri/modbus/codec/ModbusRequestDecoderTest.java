package com.digitalpetri.modbus.codec;

import com.digitalpetri.modbus.requests.WriteMultipleRegistersRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class ModbusRequestDecoderTest {

    @Test
    public void testDecodeWriteMultipleRegistersRequest() {
        ModbusRequestEncoder encoder = new ModbusRequestEncoder();

        WriteMultipleRegistersRequest request = new WriteMultipleRegistersRequest(
            0,
            64,
            new byte[128]
        );

        ByteBuf encoded = encoder.encode(request, Unpooled.buffer());

        ModbusRequestDecoder decoder = new ModbusRequestDecoder();

        WriteMultipleRegistersRequest decoded = (WriteMultipleRegistersRequest) decoder.decode(encoded);

        assertNotNull(decoded);
        assertEquals(decoded.getAddress(), 0);
        assertEquals(decoded.getQuantity(), 64);
        assertEquals(decoded.getValues().readableBytes(), 128);
    }

}