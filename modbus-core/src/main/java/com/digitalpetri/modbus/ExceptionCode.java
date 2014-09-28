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

package com.digitalpetri.modbus;

import java.util.Optional;

public enum ExceptionCode {

    IllegalFunction(0x01),
    IllegalDataAddress(0x02),
    IllegalDataValue(0x03),
    SlaveDeviceFailure(0x04),
    Acknowledge(0x05),
    SlaveDeviceBusy(0x06),
    MemoryParityError(0x08),
    GatewayPathUnavailable(0x0A),
    GatewayTargetDeviceFailedToResponse(0x0B);

    private final int code;

    ExceptionCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Optional<ExceptionCode> fromCode(int code) {
        switch (code) {
            case 0x01: return Optional.of(IllegalFunction);
            case 0x02: return Optional.of(IllegalDataAddress);
            case 0x03: return Optional.of(IllegalDataValue);
            case 0x04: return Optional.of(SlaveDeviceFailure);
            case 0x05: return Optional.of(Acknowledge);
            case 0x06: return Optional.of(SlaveDeviceBusy);
            case 0x08: return Optional.of(MemoryParityError);
            case 0x0A: return Optional.of(GatewayPathUnavailable);
            case 0x0B: return Optional.of(GatewayTargetDeviceFailedToResponse);
        }

        return Optional.empty();
    }

}
