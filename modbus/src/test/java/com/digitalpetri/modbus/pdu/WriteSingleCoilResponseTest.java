package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class WriteSingleCoilResponseTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (boolean value : new boolean[] {true, false}) {
        var response = new WriteSingleCoilResponse(address, value);
        ByteBuffer buffer = ByteBuffer.allocate(256);

        WriteSingleCoilResponse.Serializer.encode(response, buffer);

        buffer.flip();

        WriteSingleCoilResponse decoded = WriteSingleCoilResponse.Serializer.decode(buffer);

        assertEquals(response, decoded);
      }
    }
  }
}
