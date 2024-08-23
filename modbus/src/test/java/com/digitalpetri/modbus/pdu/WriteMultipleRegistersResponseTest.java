package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class WriteMultipleRegistersResponseTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (int quantity = 1; quantity < 0x007B; quantity++) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var response = new WriteMultipleRegistersResponse(address, quantity);
        WriteMultipleRegistersResponse.Serializer.encode(response, buffer);

        buffer.flip();

        WriteMultipleRegistersResponse decoded =
            WriteMultipleRegistersResponse.Serializer.decode(buffer);

        assertEquals(response, decoded);
      }
    }
  }

}
