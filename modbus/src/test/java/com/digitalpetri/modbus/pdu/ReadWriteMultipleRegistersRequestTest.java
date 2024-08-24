package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ReadWriteMultipleRegistersRequestTest {

  @Test
  void serializer() {
    var random = new Random();
    int address = random.nextInt(0xFFFF + 1);
    int quantity = random.nextInt(125) + 1;
    int writeAddress = random.nextInt(0xFFFF + 1);
    int writeQuantity = random.nextInt(125) + 1;
    byte[] writeValues = new byte[writeQuantity * 2];
    random.nextBytes(writeValues);

    ByteBuffer buffer = ByteBuffer.allocate(256);

    var request = new ReadWriteMultipleRegistersRequest(
        address,
        quantity,
        writeAddress,
        writeQuantity,
        writeValues
    );

    ReadWriteMultipleRegistersRequest.Serializer.encode(request, buffer);

    buffer.flip();

    ReadWriteMultipleRegistersRequest decoded =
        ReadWriteMultipleRegistersRequest.Serializer.decode(buffer);

    assertEquals(request, decoded);
  }

}
