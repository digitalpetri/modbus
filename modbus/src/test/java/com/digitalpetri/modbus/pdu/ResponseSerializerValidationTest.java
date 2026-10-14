package com.digitalpetri.modbus.pdu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.ModbusPduSerializer.DefaultResponseSerializer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ResponseSerializerValidationTest {

  private static final List<Decoder> DECODERS =
      List.of(
          new Decoder(ReadCoilsResponse.Serializer::decode, new byte[] {1, 1, 0x55}),
          new Decoder(ReadDiscreteInputsResponse.Serializer::decode, new byte[] {2, 1, 0x55}),
          new Decoder(
              ReadHoldingRegistersResponse.Serializer::decode, new byte[] {3, 2, 0x12, 0x34}),
          new Decoder(ReadInputRegistersResponse.Serializer::decode, new byte[] {4, 2, 0x12, 0x34}),
          new Decoder(
              WriteSingleCoilResponse.Serializer::decode, new byte[] {5, 0, 10, (byte) 0xFF, 0}),
          new Decoder(
              WriteSingleRegisterResponse.Serializer::decode, new byte[] {6, 0, 10, 0x12, 0x34}),
          new Decoder(WriteMultipleCoilsResponse.Serializer::decode, new byte[] {15, 0, 10, 0, 9}),
          new Decoder(
              WriteMultipleRegistersResponse.Serializer::decode, new byte[] {16, 0, 10, 0, 2}),
          new Decoder(
              MaskWriteRegisterResponse.Serializer::decode,
              new byte[] {22, 0, 10, 0x12, 0x34, 0x56, 0x78}),
          new Decoder(
              ReadWriteMultipleRegistersResponse.Serializer::decode,
              new byte[] {23, 2, 0x12, 0x34}));

  @Test
  void decodersConsumeCompletePdusWithinTheBufferWindow() throws Exception {
    for (Decoder decoder : DECODERS) {
      byte[] pdu = decoder.pdu();
      ByteBuffer buffer = ByteBuffer.allocate(pdu.length + 2);
      buffer.put((byte) 0x7F).put(pdu).put((byte) 0x7F);
      buffer.position(1).limit(1 + pdu.length);
      ModbusResponsePdu response = decoder.decode().apply(buffer);
      assertEquals(pdu[0], response.getFunctionCode());
      assertEquals(buffer.limit(), buffer.position());
      assertEquals(
          response, DefaultResponseSerializer.INSTANCE.decode(pdu[0], ByteBuffer.wrap(pdu)));
    }
  }

  @Test
  void decodersRejectEveryTruncationAndTrailingBytes() {
    for (Decoder decoder : DECODERS) {
      if (decoder.pdu()[0] == 6) {
        // Every byte after the FC 06 address belongs to its variable-length value.
        continue;
      }
      for (int length = 0; length < decoder.pdu().length; length++) {
        byte[] truncated = Arrays.copyOf(decoder.pdu(), length);
        IllegalArgumentException e =
            assertThrows(
                IllegalArgumentException.class,
                () -> decoder.decode().apply(ByteBuffer.wrap(truncated)));
        assertTrue(e.getMessage().contains("response PDU"));
      }
      byte[] trailing = Arrays.copyOf(decoder.pdu(), decoder.pdu().length + 1);
      IllegalArgumentException e =
          assertThrows(
              IllegalArgumentException.class,
              () -> decoder.decode().apply(ByteBuffer.wrap(trailing)));
      assertTrue(e.getMessage().contains("length"));
    }
  }

  @Test
  void decodersCheckFunctionCodesWithoutAssertions() {
    for (Decoder decoder : DECODERS) {
      byte[] wrongFunction = decoder.pdu().clone();
      wrongFunction[0] = 0x7F;
      IllegalArgumentException e =
          assertThrows(
              IllegalArgumentException.class,
              () -> decoder.decode().apply(ByteBuffer.wrap(wrongFunction)));
      assertTrue(e.getMessage().contains("function code"));
    }
  }

  @Test
  void readDecodersRejectWrongDeclaredCountsAndAcceptMaximumData() {
    for (Decoder decoder : DECODERS) {
      int functionCode = decoder.pdu()[0];
      if (functionCode == 1
          || functionCode == 2
          || functionCode == 3
          || functionCode == 4
          || functionCode == 23) {
        byte[] wrongCount = decoder.pdu().clone();
        wrongCount[1]++;
        assertThrows(
            IllegalArgumentException.class,
            () -> decoder.decode().apply(ByteBuffer.wrap(wrongCount)));
        for (int count : new int[] {0, 251, 252, 255}) {
          byte[] invalid = readPdu(functionCode, count);
          assertThrows(
              IllegalArgumentException.class,
              () -> decoder.decode().apply(ByteBuffer.wrap(invalid)));
        }
        ByteBuffer maximum = ByteBuffer.wrap(readPdu(functionCode, 250));
        decoder.decode().apply(maximum);
        assertEquals(0, maximum.remaining());
      }
      if (functionCode == 3 || functionCode == 4 || functionCode == 23) {
        for (int count : new int[] {1, 3, 249}) {
          byte[] oddCount = readPdu(functionCode, count);
          IllegalArgumentException e =
              assertThrows(
                  IllegalArgumentException.class,
                  () -> decoder.decode().apply(ByteBuffer.wrap(oddCount)));
          assertTrue(e.getMessage().contains("even count"));
        }
      }
    }
  }

  @Test
  void writeDecodersRejectInvalidCoilValuesAndQuantities() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            WriteSingleCoilResponse.Serializer.decode(ByteBuffer.wrap(new byte[] {5, 0, 0, 0, 1})));
    for (int quantity : new int[] {0, 1969, 65535}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> WriteMultipleCoilsResponse.Serializer.decode(writePdu(15, quantity)));
    }
    for (int quantity : new int[] {0, 124, 65535}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> WriteMultipleRegistersResponse.Serializer.decode(writePdu(16, quantity)));
    }
    assertEquals(1968, WriteMultipleCoilsResponse.Serializer.decode(writePdu(15, 1968)).quantity());
    assertEquals(
        123, WriteMultipleRegistersResponse.Serializer.decode(writePdu(16, 123)).quantity());
  }

  private static byte[] readPdu(int functionCode, int count) {
    byte[] pdu = new byte[2 + count];
    pdu[0] = (byte) functionCode;
    pdu[1] = (byte) count;
    return pdu;
  }

  private static ByteBuffer writePdu(int functionCode, int quantity) {
    return ByteBuffer.wrap(
        new byte[] {(byte) functionCode, 0, 0, (byte) (quantity >> 8), (byte) quantity});
  }

  private record Decoder(Function<ByteBuffer, ModbusResponsePdu> decode, byte[] pdu) {}
}
