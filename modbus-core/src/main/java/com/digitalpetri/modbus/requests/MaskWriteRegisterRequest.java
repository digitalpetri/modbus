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

package com.digitalpetri.modbus.requests;

import com.digitalpetri.modbus.FunctionCode;

/**
 * This function is used to modify the contents of a specified holding register using a combination of an AND mask, an
 * OR mask, and the register's current contents. The function can be used to set or clear individual bits in the
 * register.
 * <p>
 * The request specifies the holding register to be written, the data to be used as the AND mask, and the data to be
 * used as the OR mask. Registers are addressed starting at zero. Therefore registers 1-16 are addressed as 0-15.
 * <p>
 * The function’s algorithm is:
 * Result = (Current Contents AND And_Mask) OR (Or_Mask AND (NOT And_Mask))
 */
public class MaskWriteRegisterRequest extends SimpleModbusRequest {

    private final int address;
    private final int andMask;
    private final int orMask;

    /**
     * @param address 0x0000 to 0xFFFF (0 to 65535)
     * @param andMask 0x0000 to 0xFFFF (0 to 65535)
     * @param orMask  0x0000 to 0xFFFF (0 to 65535)
     */
    public MaskWriteRegisterRequest(int address, int andMask, int orMask) {
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
