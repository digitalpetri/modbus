package com.digitalpetri.modbus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class MbapHeaderTest {

  @Test
  void serializer() {
    for (int i = 0; i < 65536; i++) {
      ByteBuffer buffer = ByteBuffer.allocate(7);

      var header = new MbapHeader(i, i, i, i % 256);
      MbapHeader.Serializer.encode(header, buffer);

      buffer.flip();
      MbapHeader decoded = MbapHeader.Serializer.decode(buffer);

      assertEquals(header, decoded);
    }
  }

}
