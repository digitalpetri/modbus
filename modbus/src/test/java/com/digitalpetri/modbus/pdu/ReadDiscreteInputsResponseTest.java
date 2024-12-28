package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class ReadDiscreteInputsResponseTest {

  @Test
  public void serializer() {
    ByteBuffer buffer = ByteBuffer.allocate(256);

    byte[] bs = new byte[10];
    new Random().nextBytes(bs);

    var response = new ReadDiscreteInputsResponse(bs);
    ReadDiscreteInputsResponse.Serializer.encode(response, buffer);

    buffer.flip();

    ReadDiscreteInputsResponse decoded = ReadDiscreteInputsResponse.Serializer.decode(buffer);

    assertEquals(response, decoded);
  }
}
