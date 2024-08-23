package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WriteMultipleCoilsRequestTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (int quantity = 0; quantity < 0x07B0; quantity++) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        byte[] values = new byte[(quantity + 7) / 8];
        Arrays.fill(values, (byte) 0xFF);

        var request = new WriteMultipleCoilsRequest(address, quantity, values);
        WriteMultipleCoilsRequest.Serializer.encode(request, buffer);

        buffer.flip();

        WriteMultipleCoilsRequest decoded =
            WriteMultipleCoilsRequest.Serializer.decode(buffer);

        assertEquals(request, decoded);
      }
    }
  }

}
