package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ReadCoilsRequestTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (short quantity = 1; quantity <= 2000; quantity++) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var request = new ReadCoilsRequest(address, quantity);
        ReadCoilsRequest.Serializer.encode(request, buffer);

        buffer.flip();

        ReadCoilsRequest decoded =
            ReadCoilsRequest.Serializer.decode(buffer);

        assertEquals(request, decoded);
      }
    }
  }

}
