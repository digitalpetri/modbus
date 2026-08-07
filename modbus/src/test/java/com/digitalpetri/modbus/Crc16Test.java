package com.digitalpetri.modbus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class Crc16Test {

  @Test
  void crc16() {
    // https://crccalc.com/?crc=123456789&method=crc16&datatype=hex&outtype=0
    Crc16 crc = new Crc16();
    crc.update(ByteBuffer.wrap(new byte[] {0x12, 0x34, 0x56, 0x78, 0x09}));
    int value = crc.getValue();

    assertEquals(0x2590, value);
  }

  @Test
  void crc16WithBufferPosition() {
    // A buffer whose position is advanced past leading bytes must be CRC'd
    // over only the remaining bytes [position, limit). Prefixing the known
    // vector with two bytes and skipping them must yield the same CRC. See #157.
    Crc16 crc = new Crc16();
    ByteBuffer buffer = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x12, 0x34, 0x56, 0x78, 0x09});
    buffer.position(2);
    crc.update(buffer);

    assertEquals(0x2590, crc.getValue());
  }

  @Test
  void reset() {
    Crc16 crc = new Crc16();
    crc.update(ByteBuffer.wrap(new byte[] {0x12, 0x34, 0x56, 0x78, 0x09}));
    crc.reset();

    assertEquals(0xFFFF, crc.getValue());
  }
}
