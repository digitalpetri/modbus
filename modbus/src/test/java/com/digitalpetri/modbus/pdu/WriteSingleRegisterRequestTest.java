package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WriteSingleRegisterRequestTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (int value : new int[] {0, 1, 0xFFFF}) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var request = new WriteSingleRegisterRequest(address, value);
        WriteSingleRegisterRequest.Serializer.encode(request, buffer);

        buffer.flip();

        WriteSingleRegisterRequest decoded = WriteSingleRegisterRequest.Serializer.decode(buffer);

        assertEquals(request, decoded);
      }
    }
  }

  @Test
  void encodeTwoByteValue() {
    var request = new WriteSingleRegisterRequest(0x0001, new byte[] {0x00, 0x03});

    assertEquals("060001" + "0003", encode(request));
  }

  @Test
  void encodeFourByteValue() {
    var request =
        new WriteSingleRegisterRequest(0x1B59, new byte[] {0x3F, (byte) 0x80, 0x00, 0x00});

    assertEquals("061b59" + "3f800000", encode(request));
  }

  @Test
  void decodeTwoByteValue() {
    WriteSingleRegisterRequest request = decode("060001" + "0003");

    assertEquals(0x0001, request.address());
    assertArrayEquals(new byte[] {0x00, 0x03}, request.value());
  }

  @Test
  void decodeFourByteValue() {
    WriteSingleRegisterRequest request = decode("061b59" + "3f800000");

    assertEquals(0x1B59, request.address());
    assertArrayEquals(new byte[] {0x3F, (byte) 0x80, 0x00, 0x00}, request.value());
  }

  @Test
  void intConstructorEncodesLow16BitsBigEndian() {
    assertArrayEquals(new byte[] {0x12, 0x34}, new WriteSingleRegisterRequest(0, 0x1234).value());
    assertArrayEquals(
        new byte[] {(byte) 0xFF, (byte) 0xFF}, new WriteSingleRegisterRequest(0, 0xFFFF).value());
    assertArrayEquals(
        new byte[] {0x56, 0x78}, new WriteSingleRegisterRequest(0, 0x12345678).value());
    assertArrayEquals(
        new byte[] {(byte) 0xFF, (byte) 0xFF}, new WriteSingleRegisterRequest(0, -1).value());
    assertArrayEquals(
        new byte[] {(byte) 0x80, 0x00}, new WriteSingleRegisterRequest(0, (short) 0x8000).value());
  }

  @Test
  void constructorRejectsInvalidValueLength() {
    for (int length : new int[] {0, 1, 3, 5, 8}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new WriteSingleRegisterRequest(0, new byte[length]));
    }
  }

  @Test
  void decodeRejectsInvalidValueLength() {
    for (String hex : new String[] {"060001", "06000100", "060001000000", "0600010000000000"}) {
      assertThrows(IllegalArgumentException.class, () -> decode(hex));
    }
  }

  @Test
  void equalsAndHashCodeCompareValueContent() {
    var a = new WriteSingleRegisterRequest(1, new byte[] {0x3F, (byte) 0x80, 0x00, 0x00});
    var b = new WriteSingleRegisterRequest(1, new byte[] {0x3F, (byte) 0x80, 0x00, 0x00});

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    assertNotEquals(a, new WriteSingleRegisterRequest(2, a.value()));
    assertNotEquals(a, new WriteSingleRegisterRequest(1, new byte[] {0x3F, (byte) 0x80}));
  }

  @Test
  void toStringFormatsValueAsHex() {
    var request =
        new WriteSingleRegisterRequest(0x1B59, new byte[] {0x3F, (byte) 0x80, 0x00, 0x00});

    assertEquals("WriteSingleRegisterRequest[address=7001, value=3f800000]", request.toString());
  }

  private static String encode(WriteSingleRegisterRequest request) {
    ByteBuffer buffer = ByteBuffer.allocate(256);
    WriteSingleRegisterRequest.Serializer.encode(request, buffer);
    buffer.flip();

    var bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static WriteSingleRegisterRequest decode(String hex) {
    return WriteSingleRegisterRequest.Serializer.decode(
        ByteBuffer.wrap(HexFormat.of().parseHex(hex)));
  }
}
