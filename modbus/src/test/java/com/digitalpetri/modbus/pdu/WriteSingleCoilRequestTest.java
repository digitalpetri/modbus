package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class WriteSingleCoilRequestTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (boolean value : new boolean[] {true, false}) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var request = new WriteSingleCoilRequest(address, value);
        WriteSingleCoilRequest.Serializer.encode(request, buffer);

        buffer.flip();

        WriteSingleCoilRequest decoded = WriteSingleCoilRequest.Serializer.decode(buffer);

        assertEquals(request, decoded);
      }
    }
  }
}
