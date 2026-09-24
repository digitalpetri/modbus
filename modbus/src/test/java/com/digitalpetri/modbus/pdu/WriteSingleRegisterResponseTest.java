package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WriteSingleRegisterResponseTest {

  @Test
  void serializer() {
    for (int address = 0; address < 0xFFFF; address++) {
      for (int value : new int[] {0, 1, 0xFFFF}) {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        var response = new WriteSingleRegisterResponse(address, value);
        WriteSingleRegisterResponse.Serializer.encode(response, buffer);

        buffer.flip();

        WriteSingleRegisterResponse decoded = WriteSingleRegisterResponse.Serializer.decode(buffer);

        assertEquals(response, decoded);
      }
    }
  }

  @Test
  void encodeTwoByteValue() {
    var response = new WriteSingleRegisterResponse(0x0001, new byte[] {0x00, 0x03});

    assertEquals("060001" + "0003", encode(response));
  }

  @Test
  void encodeFourByteValue() {
    var response =
        new WriteSingleRegisterResponse(0x1B59, new byte[] {0x3F, (byte) 0x80, 0x00, 0x00});

    assertEquals("061b59" + "3f800000", encode(response));
  }

  @Test
  void decodeTwoByteValue() {
    WriteSingleRegisterResponse response = decode("060001" + "0003");

    assertEquals(0x0001, response.address());
    assertArrayEquals(new byte[] {0x00, 0x03}, response.value());
  }

  @Test
  void decodeFourByteValue() {
    WriteSingleRegisterResponse response = decode("061b59" + "3f800000");

    assertEquals(0x1B59, response.address());
    assertArrayEquals(new byte[] {0x3F, (byte) 0x80, 0x00, 0x00}, response.value());
  }

  @Test
  void intConstructorEncodesLow16BitsBigEndian() {
    assertArrayEquals(new byte[] {0x12, 0x34}, new WriteSingleRegisterResponse(0, 0x1234).value());
    assertArrayEquals(
        new byte[] {(byte) 0xFF, (byte) 0xFF}, new WriteSingleRegisterResponse(0, 0xFFFF).value());
    assertArrayEquals(
        new byte[] {0x56, 0x78}, new WriteSingleRegisterResponse(0, 0x12345678).value());
    assertArrayEquals(
        new byte[] {(byte) 0xFF, (byte) 0xFF}, new WriteSingleRegisterResponse(0, -1).value());
    assertArrayEquals(
        new byte[] {(byte) 0x80, 0x00}, new WriteSingleRegisterResponse(0, (short) 0x8000).value());
  }

  @Test
  void serializerPreservesAnyValueLength() {
    for (String hex :
        new String[] {"060001", "06000100", "060001aabbcc", "0600010102030405060708"}) {
      WriteSingleRegisterResponse decoded = decode(hex);

      assertEquals(hex, encode(decoded));
    }
  }

  @Test
  void equalsAndHashCodeCompareValueContent() {
    var a = new WriteSingleRegisterResponse(1, new byte[] {0x3F, (byte) 0x80, 0x00, 0x00});
    var b = new WriteSingleRegisterResponse(1, new byte[] {0x3F, (byte) 0x80, 0x00, 0x00});

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    assertNotEquals(a, new WriteSingleRegisterResponse(2, a.value()));
    assertNotEquals(a, new WriteSingleRegisterResponse(1, new byte[] {0x3F, (byte) 0x80}));
  }

  @Test
  void toStringFormatsValueAsHex() {
    var response =
        new WriteSingleRegisterResponse(0x1B59, new byte[] {0x3F, (byte) 0x80, 0x00, 0x00});

    assertEquals("WriteSingleRegisterResponse[address=7001, value=3f800000]", response.toString());
  }

  private static String encode(WriteSingleRegisterResponse response) {
    ByteBuffer buffer = ByteBuffer.allocate(256);
    WriteSingleRegisterResponse.Serializer.encode(response, buffer);
    buffer.flip();

    var bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static WriteSingleRegisterResponse decode(String hex) {
    return WriteSingleRegisterResponse.Serializer.decode(
        ByteBuffer.wrap(HexFormat.of().parseHex(hex)));
  }
}
