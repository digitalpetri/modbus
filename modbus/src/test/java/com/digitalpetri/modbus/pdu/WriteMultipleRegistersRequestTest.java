package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WriteMultipleRegistersRequestTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (int quantity = 1; quantity < 0x007B; quantity++) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        byte[] values = new byte[quantity * 2];
        Arrays.fill(values, (byte) 0xFF);

        var request = new WriteMultipleRegistersRequest(address, quantity, values);

        WriteMultipleRegistersRequest.Serializer.encode(request, buffer);

        buffer.flip();

        WriteMultipleRegistersRequest decoded =
            WriteMultipleRegistersRequest.Serializer.decode(buffer);

        assertEquals(request, decoded);
      }
    }
  }
}
