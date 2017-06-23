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

package com.digitalpetri.modbus;

public enum FunctionCode {

    ReadCoils(0x01),
    ReadDiscreteInputs(0x02),
    ReadHoldingRegisters(0x03),
    ReadInputRegisters(0x04),
    WriteSingleCoil(0x05),
    WriteSingleRegister(0x06),
    ReadExceptionStatus(0x07),
    Diagnostics(0x08),
    GetCommEventCounter(0x0B),
    GetCommEventLog(0x0C),
    WriteMultipleCoils(0x0F),
    WriteMultipleRegisters(0x10),
    ReportSlaveId(0x11),
    ReadFileRecord(0x14),
    WriteFileRecord(0x15),
    MaskWriteRegister(0x16),
    ReadWriteMultipleRegisters(0x17),
    ReadFifoQueue(0x18),
    EncapsulatedInterfaceTransport(0x2B);

    private final int code;

    FunctionCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static FunctionCode fromCode(int code) {
        switch(code) {
            case 0x01: return ReadCoils;
            case 0x02: return ReadDiscreteInputs;
            case 0x03: return ReadHoldingRegisters;
            case 0x04: return ReadInputRegisters;
            case 0x05: return WriteSingleCoil;
            case 0x06: return WriteSingleRegister;
            case 0x07: return ReadExceptionStatus;
            case 0x08: return Diagnostics;
            case 0x0B: return GetCommEventCounter;
            case 0x0C: return GetCommEventLog;
            case 0x0F: return WriteMultipleCoils;
            case 0x10: return WriteMultipleRegisters;
            case 0x11: return ReportSlaveId;
            case 0x14: return ReadFileRecord;
            case 0x15: return WriteFileRecord;
            case 0x16: return MaskWriteRegister;
            case 0x17: return ReadWriteMultipleRegisters;
            case 0x18: return ReadFifoQueue;
            case 0x2B: return EncapsulatedInterfaceTransport;
        }

        return null;
    }

    public static boolean isExceptionCode(int code) {
        return fromCode(code - 0x80) != null;
    }

}
