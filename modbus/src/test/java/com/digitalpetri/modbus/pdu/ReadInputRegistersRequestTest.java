package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ReadInputRegistersRequestTest {

  @Test
  public void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (short quantity = 1; quantity <= 125; quantity++) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var request = new ReadInputRegistersRequest(address, quantity);
        ReadInputRegistersRequest.Serializer.encode(request, buffer);

        buffer.flip();

        ReadInputRegistersRequest decoded = ReadInputRegistersRequest.Serializer.decode(buffer);

        assertEquals(request, decoded);
      }
    }
  }
}
