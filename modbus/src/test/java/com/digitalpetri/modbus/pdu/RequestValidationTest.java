package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.ModbusPduSerializer.DefaultRequestSerializer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class RequestValidationTest {

  @Test
  void addressAndQuantityBoundaries() throws Exception {
    checkRange(ReadCoilsRequest::new, 2000);
    checkRange(ReadDiscreteInputsRequest::new, 2000);
    checkRange(ReadHoldingRegistersRequest::new, 125);
    checkRange(ReadInputRegistersRequest::new, 125);
    checkRange((a, q) -> new WriteMultipleCoilsRequest(a, q, new byte[(q + 7) / 8]), 1968);
    checkRange((a, q) -> new WriteMultipleRegistersRequest(a, q, new byte[q * 2]), 123);
    checkRange((a, q) -> new ReadWriteMultipleRegistersRequest(a, q, 0, 1, new byte[2]), 125);
    checkRange((a, q) -> new ReadWriteMultipleRegistersRequest(0, 1, a, q, new byte[q * 2]), 121);
  }

  @Test
  void rejectsIntegerNarrowingAndInvalidSingleValues() {
    for (int value : new int[] {-1, 65536, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
      assertInvalid(new WriteSingleCoilRequest(value, true));
      assertInvalid(new WriteSingleRegisterRequest(value, 0));
      assertInvalid(new MaskWriteRegisterRequest(value, 0, 0));
      assertInvalid(new MaskWriteRegisterRequest(0, value, 0));
      assertInvalid(new MaskWriteRegisterRequest(0, 0, value));
      assertInvalid(new ReadHoldingRegistersRequest(value, 1));
      assertInvalid(new ReadHoldingRegistersRequest(0, value));
    }
    for (int value : new int[] {-1, 1, 0x00FF, 0xFFFF, 65536, 0x1FF00}) {
      assertInvalid(new WriteSingleCoilRequest(0, value));
    }
  }

  @Test
  void singleValueBoundaries() throws Exception {
    for (int address : new int[] {0, 65535}) {
      for (int value : new int[] {0, 65535}) {
        assertRoundTrip(new WriteSingleRegisterRequest(address, value));
        assertRoundTrip(new MaskWriteRegisterRequest(address, value, value));
      }
      assertRoundTrip(new WriteSingleCoilRequest(address, false));
      assertRoundTrip(new WriteSingleCoilRequest(address, true));
    }
  }

  @Test
  void singleRegisterValuesPreserveAnyLengthAndLow16Bits() throws Exception {
    for (int value : new int[] {-1, 65536, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
      assertRoundTrip(new WriteSingleRegisterRequest(0, value));
    }
    for (int length : new int[] {0, 1, 2, 3, 4, 8}) {
      assertRoundTrip(new WriteSingleRegisterRequest(0, new byte[length]));
    }
    assertInvalid(new WriteSingleRegisterRequest(0, null));
  }

  @Test
  void rejectsInconsistentPayloadLengths() {
    for (int length : new int[] {0, 1, 3, 4, 256}) {
      assertInvalid(new WriteMultipleRegistersRequest(0, 1, new byte[length]));
      assertInvalid(new ReadWriteMultipleRegistersRequest(0, 1, 0, 1, new byte[length]));
    }
    for (int quantity : new int[] {1, 7, 8, 9, 1968}) {
      int length = (quantity + 7) / 8;
      assertInvalid(new WriteMultipleCoilsRequest(0, quantity, new byte[length - 1]));
      assertInvalid(new WriteMultipleCoilsRequest(0, quantity, new byte[length + 1]));
    }
    assertInvalid(new WriteMultipleCoilsRequest(0, 1, null));
    assertInvalid(new WriteMultipleRegistersRequest(0, 1, null));
    assertInvalid(new ReadWriteMultipleRegistersRequest(0, 1, 0, 1, null));
  }

  @Test
  void validatesMutableCoilPayloadAtEveryEncoding() throws Exception {
    byte[] values = {1};
    var request = new WriteMultipleCoilsRequest(0, 1, values);
    ByteBuffer buffer = ByteBuffer.allocate(253);
    WriteMultipleCoilsRequest.Serializer.encode(request, buffer);
    assertArrayEquals(new byte[] {0x0F, 0, 0, 0, 1, 1, 1}, encodedBytes(buffer));

    values[0] = (byte) 0x81;
    buffer.clear();
    assertThrows(
        IllegalArgumentException.class,
        () -> WriteMultipleCoilsRequest.Serializer.encode(request, buffer));
    assertEquals(0, buffer.position());
    assertInvalid(request);

    values[0] = 0;
    buffer.clear();
    WriteMultipleCoilsRequest.Serializer.encode(request, buffer);
    assertArrayEquals(new byte[] {0x0F, 0, 0, 0, 1, 1, 0}, encodedBytes(buffer));
  }

  @Test
  void directSerializerRejectsNarrowingBeforeWriting() {
    ByteBuffer buffer = ByteBuffer.allocate(253);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ReadHoldingRegistersRequest.Serializer.encode(
                new ReadHoldingRegistersRequest(65536, 1), buffer));
    assertEquals(0, buffer.position());
  }

  @Test
  void decodingPreservesInvalidRequests() throws Exception {
    for (byte[] bytes :
        List.of(
            new byte[] {0x03, 0, 0, 0, 0},
            new byte[] {0x03, (byte) 0xFF, (byte) 0xFF, 0, 2},
            new byte[] {0x05, 0, 0, 0, 1},
            new byte[] {0x10, 0, 0, 0, 2, 2, 0, 1})) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      ModbusRequestPdu request =
          (ModbusRequestPdu) DefaultRequestSerializer.INSTANCE.decode(bytes[0], buffer);
      assertEquals(0, buffer.remaining());
      assertInvalid(request);
    }
  }

  private static void checkRange(BiFunction<Integer, Integer, ModbusRequestPdu> factory, int max)
      throws Exception {
    for (int quantity : new int[] {1, max}) {
      assertRoundTrip(factory.apply(0, quantity));
      assertRoundTrip(factory.apply(65536 - quantity, quantity));
      assertInvalid(factory.apply(65537 - quantity, quantity));
    }
    assertInvalid(factory.apply(-1, 1));
    assertInvalid(factory.apply(65536, 1));
    assertInvalid(factory.apply(0, 0));
    assertInvalid(factory.apply(0, max + 1));
  }

  private static void assertInvalid(ModbusRequestPdu request) {
    ByteBuffer buffer = ByteBuffer.allocate(253);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> DefaultRequestSerializer.INSTANCE.encode(request, buffer),
            request.getClass().getSimpleName());
    assertTrue(e.getMessage().length() > 0);
    assertEquals(0, buffer.position());
  }

  private static void assertRoundTrip(ModbusRequestPdu request) throws Exception {
    ByteBuffer buffer = ByteBuffer.allocate(253);
    DefaultRequestSerializer.INSTANCE.encode(request, buffer);
    buffer.flip();
    assertEquals(
        request, DefaultRequestSerializer.INSTANCE.decode(request.getFunctionCode(), buffer));
    assertEquals(0, buffer.remaining());
  }

  private static byte[] encodedBytes(ByteBuffer buffer) {
    buffer.flip();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return bytes;
  }
}
