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

import com.digitalpetri.modbus.codec.ModbusResponseDecoder;
import com.digitalpetri.modbus.codec.ModbusResponseEncoder;
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
import io.netty.buffer.Unpooled;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class ModbusResponseSerializationTest {

    private final ModbusResponseEncoder encoder = new ModbusResponseEncoder();
    private final ModbusResponseDecoder decoder = new ModbusResponseDecoder();

    @Test
    public void testReadCoilsResponse() {
        ReadCoilsResponse response = new ReadCoilsResponse(Unpooled.buffer().writeByte(1));
        response.retain().content().markReaderIndex();

        ByteBuf encoded = encoder.encode(response, Unpooled.buffer());
        ReadCoilsResponse decoded = (ReadCoilsResponse) decoder.decode(encoded);

        response.content().resetReaderIndex();

        assertEquals(response.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(response.getCoilStatus(), decoded.getCoilStatus());
    }

    @Test
    public void testReadDiscreteInputsResponse() {
        ReadDiscreteInputsResponse response = new ReadDiscreteInputsResponse(Unpooled.buffer().writeByte(1));
        response.retain().content().markReaderIndex();

        ByteBuf encoded = encoder.encode(response, Unpooled.buffer());
        ReadDiscreteInputsResponse decoded = (ReadDiscreteInputsResponse) decoder.decode(encoded);

        response.content().resetReaderIndex();

        assertEquals(response.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(response.getInputStatus(), decoded.getInputStatus());
    }

    @Test
    public void testReadHoldingRegistersResponse() {
        ReadHoldingRegistersResponse response = new ReadHoldingRegistersResponse(Unpooled.buffer().writeByte(1).writeByte(2));
        response.retain().content().markReaderIndex();

        ByteBuf encoded = encoder.encode(response, Unpooled.buffer());
        ReadHoldingRegistersResponse decoded = (ReadHoldingRegistersResponse) decoder.decode(encoded);

        response.content().resetReaderIndex();

        assertEquals(response.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(response.getRegisters(), decoded.getRegisters());
    }

    @Test
    public void testReadInputRegistersResponse() {
        ReadInputRegistersResponse response = new ReadInputRegistersResponse(Unpooled.buffer().writeByte(1).writeByte(2));
        response.retain().content().markReaderIndex();

        ByteBuf encoded = encoder.encode(response, Unpooled.buffer());
        ReadInputRegistersResponse decoded = (ReadInputRegistersResponse) decoder.decode(encoded);

        response.content().resetReaderIndex();

        assertEquals(response.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(response.getRegisters(), decoded.getRegisters());
    }

    @Test
    public void testWriteSingleCoilResponse() {
        WriteSingleCoilResponse response = new WriteSingleCoilResponse(0, 0xFF00);

        ByteBuf encoded = encoder.encode(response, Unpooled.buffer());
        WriteSingleCoilResponse decoded = (WriteSingleCoilResponse) decoder.decode(encoded);

        assertEquals(response.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(response.getAddress(), decoded.getAddress());
        assertEquals(response.getValue(), decoded.getValue());
    }

    @Test
    public void testWriteSingleRegisterResponse() {
        WriteSingleRegisterResponse response = new WriteSingleRegisterResponse(0, 0xFF00);

        ByteBuf encoded = encoder.encode(response, Unpooled.buffer());
        WriteSingleRegisterResponse decoded = (WriteSingleRegisterResponse) decoder.decode(encoded);

        assertEquals(response.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(response.getAddress(), decoded.getAddress());
        assertEquals(response.getValue(), decoded.getValue());
    }

    @Test
    public void testWriteMultipleCoilsResponse() {
        WriteMultipleCoilsResponse response = new WriteMultipleCoilsResponse(0, 10);

        ByteBuf encoded = encoder.encode(response, Unpooled.buffer());
        WriteMultipleCoilsResponse decoded = (WriteMultipleCoilsResponse) decoder.decode(encoded);

        assertEquals(response.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(response.getAddress(), decoded.getAddress());
        assertEquals(response.getQuantity(), decoded.getQuantity());
    }

    @Test
    public void testWriteMultipleRegistersResponse() {
        WriteMultipleRegistersResponse response = new WriteMultipleRegistersResponse(0, 10);

        ByteBuf encoded = encoder.encode(response, Unpooled.buffer());
        WriteMultipleRegistersResponse decoded = (WriteMultipleRegistersResponse) decoder.decode(encoded);

        assertEquals(response.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(response.getAddress(), decoded.getAddress());
        assertEquals(response.getQuantity(), decoded.getQuantity());
    }

    @Test
    public void testMaskWriteRegisterResponse() {
        MaskWriteRegisterResponse response = new MaskWriteRegisterResponse(0, 0x1234, 0xFFFF);

        ByteBuf encoded = encoder.encode(response, Unpooled.buffer());
        MaskWriteRegisterResponse decoded = (MaskWriteRegisterResponse) decoder.decode(encoded);

        assertEquals(response.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(response.getAddress(), decoded.getAddress());
        assertEquals(response.getAndMask(), decoded.getAndMask());
        assertEquals(response.getOrMask(), decoded.getOrMask());
    }

}
