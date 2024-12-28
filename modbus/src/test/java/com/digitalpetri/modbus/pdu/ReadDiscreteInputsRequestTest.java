package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ReadDiscreteInputsRequestTest {

  @Test
  public void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (short quantity = 1; quantity <= 2000; quantity++) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var request = new ReadDiscreteInputsRequest(address, quantity);
        ReadDiscreteInputsRequest.Serializer.encode(request, buffer);

        buffer.flip();

        ReadDiscreteInputsRequest decoded = ReadDiscreteInputsRequest.Serializer.decode(buffer);

        assertEquals(request, decoded);
      }
    }
  }
}
