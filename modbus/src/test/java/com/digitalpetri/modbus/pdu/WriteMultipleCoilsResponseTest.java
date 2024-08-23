package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class WriteMultipleCoilsResponseTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (int quantity = 1; quantity < 0x07B0; quantity++) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var response = new WriteMultipleCoilsResponse(address, quantity);
        WriteMultipleCoilsResponse.Serializer.encode(response, buffer);

        buffer.flip();

        WriteMultipleCoilsResponse decoded = WriteMultipleCoilsResponse.Serializer.decode(buffer);

        assertEquals(response, decoded);
      }
    }
  }

}
