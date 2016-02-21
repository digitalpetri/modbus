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

package com.digitalpetri.modbus.responses;

import com.digitalpetri.modbus.FunctionCode;
import io.netty.buffer.ByteBuf;

public class ReadInputRegistersResponse extends ByteBufModbusResponse {

    public ReadInputRegistersResponse(ByteBuf registers) {
        super(registers, FunctionCode.ReadInputRegisters);
    }

    /**
     * The register data in the response message are packed as two bytes per register, with the binary contents right
     * justified within each byte. For each register, the first byte contains the high order bits and the second
     * contains the low order bits.
     *
     * @return the {@link ByteBuf} containing register data, two bytes per register.
     */
    public ByteBuf getRegisters() {
        return super.content();
    }

}
