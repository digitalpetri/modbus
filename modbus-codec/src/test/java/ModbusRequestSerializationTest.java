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

import com.digitalpetri.modbus.codec.ModbusRequestDecoder;
import com.digitalpetri.modbus.codec.ModbusRequestEncoder;
import com.digitalpetri.modbus.requests.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.requests.ReadCoilsRequest;
import com.digitalpetri.modbus.requests.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.requests.ReadInputRegistersRequest;
import com.digitalpetri.modbus.requests.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.requests.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.requests.WriteSingleCoilRequest;
import com.digitalpetri.modbus.requests.WriteSingleRegisterRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class ModbusRequestSerializationTest {

    private final ModbusRequestEncoder encoder = new ModbusRequestEncoder();
    private final ModbusRequestDecoder decoder = new ModbusRequestDecoder();

    @DataProvider
    private Object[][] getAddressAndQuantity() {
        return new Object[][]{
                {0, 1},
                {0, 125},
                {0, 2000},
                {65535, 1},
                {65535, 125},
                {65535, 2000}
        };
    }

    @DataProvider
    private Object[][] getAddressAndRegisterValue() {
        return new Object[][]{
                {0, 1},
                {0, 125},
                {0, 2000},
                {0, 65535},
                {65535, 1},
                {65535, 125},
                {65535, 2000},
                {65535, 65535}
        };
    }

    @DataProvider
    private Object[][] getAddressAndQuantityAndValues() {
        return new Object[][]{
                {0, 1, new byte[]{0x01}},
                {0, 2, new byte[]{0x02}},
                {0, 3, new byte[]{0x04}},
                {0, 4, new byte[]{0x08}},
                {0, 5, new byte[]{0x10}},
                {0, 6, new byte[]{0x20}},
                {0, 7, new byte[]{0x40}},
                {0, 8, new byte[]{(byte) 0x80}},
                {0, 9, new byte[]{0x01, 0x01}},
                {0, 10, new byte[]{0x01, 0x02}},
                {0, 11, new byte[]{0x01, 0x04}},
                {0, 12, new byte[]{0x01, 0x08}},
                {0, 13, new byte[]{0x01, 0x10}},
                {0, 14, new byte[]{0x01, 0x20}},
                {0, 15, new byte[]{0x01, 0x40}},
                {0, 16, new byte[]{0x01, (byte) 0x80}},
                {0, 17, new byte[]{0x02, 0x01, 0x01}}
        };
    }

    @Test(dataProvider = "getAddressAndQuantity")
    public void testReadCoilsRequest(int address, int quantity) {
        ReadCoilsRequest request = new ReadCoilsRequest(address, quantity);

        ByteBuf encoded = encoder.encode(request, Unpooled.buffer());
        ReadCoilsRequest decoded = (ReadCoilsRequest) decoder.decode(encoded);

        assertEquals(request.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(request.getAddress(), decoded.getAddress());
        assertEquals(request.getQuantity(), decoded.getQuantity());
    }

    @Test(dataProvider = "getAddressAndQuantity")
    public void testReadDiscreteInputsRequest(int address, int quantity) {
        ReadDiscreteInputsRequest request = new ReadDiscreteInputsRequest(address, quantity);

        ByteBuf encoded = encoder.encode(request, Unpooled.buffer());
        ReadDiscreteInputsRequest decoded = (ReadDiscreteInputsRequest) decoder.decode(encoded);

        assertEquals(request.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(request.getAddress(), decoded.getAddress());
        assertEquals(request.getQuantity(), decoded.getQuantity());
    }

    @Test(dataProvider = "getAddressAndQuantity")
    public void testReadHoldingRegistersRequest(int address, int quantity) {
        ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(address, quantity);

        ByteBuf encoded = encoder.encode(request, Unpooled.buffer());
        ReadHoldingRegistersRequest decoded = (ReadHoldingRegistersRequest) decoder.decode(encoded);

        assertEquals(request.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(request.getAddress(), decoded.getAddress());
        assertEquals(request.getQuantity(), decoded.getQuantity());
    }

    @Test(dataProvider = "getAddressAndQuantity")
    public void testReadInputRegistersRequest(int address, int quantity) {
        ReadInputRegistersRequest request = new ReadInputRegistersRequest(address, quantity);

        ByteBuf encoded = encoder.encode(request, Unpooled.buffer());
        ReadInputRegistersRequest decoded = (ReadInputRegistersRequest) decoder.decode(encoded);

        assertEquals(request.getFunctionCode(), decoded.getFunctionCode());
        assertEquals(request.getAddress(), decoded.getAddress());
        assertEquals(request.getQuantity(), decoded.getQuantity());
    }

    @Test
    public void testWriteSingleCoilRequest() {
        boolean[] values = new boolean[]{false, true};

        for (int address = 0; address <= 65535; address++) {
            for (boolean value : values) {
                WriteSingleCoilRequest request = new WriteSingleCoilRequest(address, value);

                ByteBuf encoded = encoder.encode(request, Unpooled.buffer());
                WriteSingleCoilRequest decoded = (WriteSingleCoilRequest) decoder.decode(encoded);

                assertEquals(request.getAddress(), decoded.getAddress());
                assertEquals(request.getValue(), decoded.getValue());
            }
        }
    }

    @Test(dataProvider = "getAddressAndRegisterValue")
    public void testWriteSingleRegisterRequest(int address, int value) {
        WriteSingleRegisterRequest request = new WriteSingleRegisterRequest(address, value);

        ByteBuf encoded = encoder.encode(request, Unpooled.buffer());
        WriteSingleRegisterRequest decoded = (WriteSingleRegisterRequest) decoder.decode(encoded);

        assertEquals(request.getAddress(), decoded.getAddress());
        assertEquals(request.getValue(), decoded.getValue());
    }

    @Test(dataProvider = "getAddressAndQuantityAndValues")
    public void testWriteMultipleCoilsRequest(int address, int quantity, byte[] values) {
        WriteMultipleCoilsRequest request = new WriteMultipleCoilsRequest(address, quantity, values);
        request.retain().content().markReaderIndex();

        ByteBuf encoded = encoder.encode(request, Unpooled.buffer());
        WriteMultipleCoilsRequest decoded = (WriteMultipleCoilsRequest) decoder.decode(encoded);

        request.content().resetReaderIndex();

        assertEquals(request.getAddress(), decoded.getAddress());
        assertEquals(request.getQuantity(), decoded.getQuantity());
        assertEquals(request.getValues(), decoded.getValues());
    }

    @Test
    public void testWriteMultipleRegistersRequest() {
        WriteMultipleRegistersRequest request = new WriteMultipleRegistersRequest(0, 2, new byte[]{1, 2, 3, 4});
        request.retain().content().markReaderIndex();

        ByteBuf encoded = encoder.encode(request, Unpooled.buffer());
        WriteMultipleRegistersRequest decoded = (WriteMultipleRegistersRequest) decoder.decode(encoded);

        request.content().resetReaderIndex();

        assertEquals(request.getAddress(), decoded.getAddress());
        assertEquals(request.getQuantity(), decoded.getQuantity());
        assertEquals(request.getValues(), decoded.getValues());
    }

    @Test
    public void testMaskWriteRegisterRequest() {
        MaskWriteRegisterRequest request = new MaskWriteRegisterRequest(0, 0x0000, 0xFFFF);

        ByteBuf encoded = encoder.encode(request, Unpooled.buffer());
        MaskWriteRegisterRequest decoded = (MaskWriteRegisterRequest) decoder.decode(encoded);

        assertEquals(request.getAddress(), decoded.getAddress());
        assertEquals(request.getAndMask(), decoded.getAndMask());
        assertEquals(request.getOrMask(), decoded.getOrMask());
    }

}
