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
 * The normal response returns the function code, starting address, and quantity of coils forced.
 */
public class WriteMultipleCoilsResponse extends SimpleModbusResponse {

    private final int address;
    private final int quantity;

    /**
     * @param address  0x0000 to 0xFFFF (0 to 65535)
     * @param quantity 0x0001 to 0x07B0 (1 to 2000)
     */
    public WriteMultipleCoilsResponse(int address, int quantity) {
        super(FunctionCode.WriteMultipleCoils);

        this.address = address;
        this.quantity = quantity;
    }

    public int getAddress() {
        return address;
    }

    public int getQuantity() {
        return quantity;
    }

}
