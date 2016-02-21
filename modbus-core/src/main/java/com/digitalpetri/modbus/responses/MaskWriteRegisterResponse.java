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

/**
 * The normal response is an echo of the request. The response is returned after the register has been written.
 */
public class MaskWriteRegisterResponse extends SimpleModbusResponse {

    private final int address;
    private final int andMask;
    private final int orMask;

    /**
     * @param address 0x0000 to 0xFFFF (0 to 65535)
     * @param andMask 0x0000 to 0xFFFF (0 to 65535)
     * @param orMask  0x0000 to 0xFFFF (0 to 65535)
     */
    public MaskWriteRegisterResponse(int address, int andMask, int orMask) {
        super(FunctionCode.MaskWriteRegister);

        this.address = address;
        this.andMask = andMask;
        this.orMask = orMask;
    }

    public int getAddress() {
        return address;
    }

    public int getAndMask() {
        return andMask;
    }

    public int getOrMask() {
        return orMask;
    }

}
