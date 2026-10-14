package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ReadHoldingRegistersResponseTest {

  @Test
  void decoderRejectsTrailingBytes() {
    ByteBuffer buffer = ByteBuffer.wrap(new byte[] {0x03, 0x02, 0x12, 0x34, 0x56});

    assertThrows(
        IllegalArgumentException.class,
        () -> ReadHoldingRegistersResponse.Serializer.decode(buffer));
  }

  @Test
  void serializer() {
    byte[] registers = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
    var response = new ReadHoldingRegistersResponse(registers);

    ByteBuffer buffer = ByteBuffer.allocate(256);
    ReadHoldingRegistersResponse.Serializer.encode(response, buffer);

    buffer.flip();

    ReadHoldingRegistersResponse decoded = ReadHoldingRegistersResponse.Serializer.decode(buffer);

    assertEquals(response, decoded);
  }
}
