package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ReadWriteMultipleRegistersRequestTest {

  @Test
  void serializer() {
    var random = new Random();
    int quantity = random.nextInt(125) + 1;
    int address = random.nextInt(65537 - quantity);
    int writeQuantity = random.nextInt(121) + 1;
    int writeAddress = random.nextInt(65537 - writeQuantity);
    byte[] writeValues = new byte[writeQuantity * 2];
    random.nextBytes(writeValues);

    ByteBuffer buffer = ByteBuffer.allocate(256);

    var request =
        new ReadWriteMultipleRegistersRequest(
            address, quantity, writeAddress, writeQuantity, writeValues);

    ReadWriteMultipleRegistersRequest.Serializer.encode(request, buffer);

    buffer.flip();

    ReadWriteMultipleRegistersRequest decoded =
        ReadWriteMultipleRegistersRequest.Serializer.decode(buffer);

    assertEquals(request, decoded);
  }

  @Test
  void serializerAtQuantityLimits() {
    for (int readQuantity : new int[] {1, 125}) {
      for (int writeQuantity : new int[] {1, 121}) {
        byte[] writeValues = new byte[writeQuantity * 2];
        for (int i = 0; i < writeValues.length; i++) {
          writeValues[i] = (byte) i;
        }

        var request =
            new ReadWriteMultipleRegistersRequest(
                65536 - readQuantity,
                readQuantity,
                65536 - writeQuantity,
                writeQuantity,
                writeValues);
        ByteBuffer buffer = ByteBuffer.allocate(253);

        ReadWriteMultipleRegistersRequest.Serializer.encode(request, buffer);
        assertEquals(10 + writeValues.length, buffer.position());

        buffer.flip();

        ReadWriteMultipleRegistersRequest decoded =
            ReadWriteMultipleRegistersRequest.Serializer.decode(buffer);

        assertEquals(request, decoded);
        assertEquals(0, buffer.remaining());
      }
    }
  }
}
