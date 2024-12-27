package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class WriteSingleRegisterRequestTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (int value : new int[] {0, 1, 0xFFFF}) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var request = new WriteSingleRegisterRequest(address, value);
        WriteSingleRegisterRequest.Serializer.encode(request, buffer);

        buffer.flip();

        WriteSingleRegisterRequest decoded = WriteSingleRegisterRequest.Serializer.decode(buffer);

        assertEquals(request, decoded);
      }
    }
  }
}
