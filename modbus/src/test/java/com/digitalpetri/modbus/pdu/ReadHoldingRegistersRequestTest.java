package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ReadHoldingRegistersRequestTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (short quantity = 1; quantity <= 125; quantity++) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var request = new ReadHoldingRegistersRequest(address, quantity);
        ReadHoldingRegistersRequest.Serializer.encode(request, buffer);

        buffer.flip();

        ReadHoldingRegistersRequest decoded =
            ReadHoldingRegistersRequest.Serializer.decode(buffer);

        assertEquals(request, decoded);
      }
    }
  }

}
