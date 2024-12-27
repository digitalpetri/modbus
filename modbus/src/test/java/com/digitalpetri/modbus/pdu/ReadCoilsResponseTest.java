package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ReadCoilsResponseTest {

  @Test
  void serializer() {
    var buffer = ByteBuffer.allocate(256);

    byte[] bs = new byte[10];
    new Random().nextBytes(bs);

    var response = new ReadCoilsResponse(bs);
    ReadCoilsResponse.Serializer.encode(response, buffer);

    buffer.flip();

    ReadCoilsResponse decoded = ReadCoilsResponse.Serializer.decode(buffer);

    assertEquals(response, decoded);
  }
}
