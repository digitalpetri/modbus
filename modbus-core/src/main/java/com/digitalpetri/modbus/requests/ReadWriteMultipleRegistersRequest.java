/*
 * Copyright 2016 Kevin Herron
 * Copyright 2020 SeRo Systems GmbH
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

package com.digitalpetri.modbus.requests;

import java.nio.ByteBuffer;

import com.digitalpetri.modbus.FunctionCode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * This function code is used to write a block of contiguous registers (1 to 123 registers) in a remote device.
 * <p>
 * The requested written values are specified in the request data field. Data is packed as two bytes per register.
 */
public class ReadWriteMultipleRegistersRequest extends ByteBufModbusRequest {

    private final int readAddress;
    private final int readQuantity;
    private final int writeAddress;
    private final int writeQuantity;

    /**
     * @param readAddress   0x0000 to 0xFFFF (0 to 65535)
     * @param readQuantity  0x0001 to 0x007B (1 to 123)
     * @param writeAddress  0x0000 to 0xFFFF (0 to 65535)
     * @param writeQuantity 0x0001 to 0x007B (1 to 123)
     * @param values        buffer of at least N bytes, where N = quantity * 2
     */
    public ReadWriteMultipleRegistersRequest(int readAddress, int readQuantity, int writeAddress, int writeQuantity,
                                             byte[] values) {
        this(readAddress, readQuantity, writeAddress, writeQuantity, Unpooled.wrappedBuffer(values));
    }

    /**
     * @param readAddress   0x0000 to 0xFFFF (0 to 65535)
     * @param readQuantity  0x0001 to 0x007B (1 to 123)
     * @param writeAddress  0x0000 to 0xFFFF (0 to 65535)
     * @param writeQuantity 0x0001 to 0x007B (1 to 123)
     * @param values        buffer of at least N bytes, where N = quantity * 2
     */
    public ReadWriteMultipleRegistersRequest(int readAddress, int readQuantity, int writeAddress, int writeQuantity,
                                             ByteBuffer values) {
        this(readAddress, readQuantity, writeAddress, writeQuantity, Unpooled.wrappedBuffer(values));
    }

    /**
     * Create a request using a {@link ByteBuf}. The buffer will have its reference count decremented after encoding.
     *
     * @param readAddress   0x0000 to 0xFFFF (0 to 65535)
     * @param readQuantity  0x0001 to 0x007B (1 to 123)
     * @param writeAddress  0x0000 to 0xFFFF (0 to 65535)
     * @param writeQuantity 0x0001 to 0x007B (1 to 123)
     * @param values        buffer of at least N bytes, where N = quantity * 2
     */
    public ReadWriteMultipleRegistersRequest(int readAddress, int readQuantity, int writeAddress, int writeQuantity,
                                             ByteBuf values) {
        super(values, FunctionCode.ReadWriteMultipleRegisters);

        this.readAddress = readAddress;
        this.readQuantity = readQuantity;

        this.writeAddress = writeAddress;
        this.writeQuantity = writeQuantity;
    }

    public int getReadAddress() {
        return readAddress;
    }

    public int getReadQuantity() {
        return readQuantity;
    }

    public int getWriteAddress() {
        return writeAddress;
    }

    public int getWriteQuantity() {
        return writeQuantity;
    }

    public ByteBuf getValues() {
        return super.content();
    }

}
