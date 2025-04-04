package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class MaskWriteRegisterResponseTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address += 256) {
      for (int andMask = 0; andMask <= 0xFFFF; andMask += 256) {
        for (int orMask = 0; orMask <= 0xFFFF; orMask += 256) {
          ByteBuffer buffer = ByteBuffer.allocate(256);

          var response = new MaskWriteRegisterResponse(address, andMask, orMask);
          MaskWriteRegisterResponse.Serializer.encode(response, buffer);

          buffer.flip();

          MaskWriteRegisterResponse decoded = MaskWriteRegisterResponse.Serializer.decode(buffer);

          assertEquals(response, decoded);
        }
      }
    }
  }
}
