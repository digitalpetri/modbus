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

package com.digitalpetri.modbus.codec;

import io.netty.buffer.ByteBuf;

public class MbapHeader {

    public static final int PROTOCOL_ID = 0;
    public static final int LENGTH = 7;

    private final short transactionId;
    private final int protocolId;
    private final int length;
    private final short unitId;

    public MbapHeader(short transactionId, int length, short unitId) {
        this(transactionId, PROTOCOL_ID, length, unitId);
    }

    public MbapHeader(short transactionId, int protocolId, int length, short unitId) {
        this.transactionId = transactionId;
        this.protocolId = protocolId;
        this.length = length;
        this.unitId = unitId;
    }

    public short getTransactionId() {
        return transactionId;
    }

    public int getProtocolId() {
        return protocolId;
    }

    public int getLength() {
        return length;
    }

    public short getUnitId() {
        return unitId;
    }

    public static MbapHeader decode(ByteBuf buffer) {
        return new MbapHeader(
                buffer.readShort(),
                buffer.readUnsignedShort(),
                buffer.readUnsignedShort(),
                buffer.readUnsignedByte()
        );
    }

    public static ByteBuf encode(MbapHeader header, ByteBuf buffer) {
        buffer.writeShort(header.transactionId);
        buffer.writeShort(header.protocolId);
        buffer.writeShort(header.length);
        buffer.writeByte(header.unitId);

        return buffer;
    }

}
