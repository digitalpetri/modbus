package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class WriteSingleRegisterResponseTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (int value : new int[]{0, 1, 0xFFFF}) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var response = new WriteSingleRegisterResponse(address, value);

        WriteSingleRegisterResponse.Serializer.encode(response, buffer);

        buffer.flip();

        WriteSingleRegisterResponse decoded =
            WriteSingleRegisterResponse.Serializer.decode(buffer);

        assertEquals(response, decoded);
      }
    }
  }

}
