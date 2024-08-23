package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class ReadInputRegistersResponseTest {

  @Test
  public void serializer() {
    byte[] registers = new byte[]{0x01, 0x02, 0x03, 0x04};
    var response = new ReadInputRegistersResponse(registers);

    ByteBuffer buffer = ByteBuffer.allocate(256);
    ReadInputRegistersResponse.Serializer.encode(response, buffer);

    buffer.flip();

    ReadInputRegistersResponse decoded =
        ReadInputRegistersResponse.Serializer.decode(buffer);

    assertEquals(response, decoded);
  }

}
