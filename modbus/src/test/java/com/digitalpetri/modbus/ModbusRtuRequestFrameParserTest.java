package com.digitalpetri.modbus;

import static com.digitalpetri.modbus.Util.partitions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.Accumulated;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.ParserState;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ModbusRtuRequestFrameParserTest {

  private static final byte[] READ_COILS = frame(0x01, new byte[] {0x01, 0x00, 0x00, 0x00, 0x08});

  private static final byte[] WRITE_MULTIPLE_REGISTERS =
      frame(0x01, new byte[] {0x10, 0x00, 0x00, 0x00, 0x02, 0x04, 0x00, 0x01, 0x00, 0x02});

  @Test
  void readCoils() {
    parseValidRequest(READ_COILS);
  }

  @Test
  void writeMultipleRegisters() {
    parseValidRequest(WRITE_MULTIPLE_REGISTERS);
  }

  @Test
  void writeMultipleCoils_ByteCounts() {
    // 246 bytes is the maximum: 1968 coils.
    for (int byteCount : new int[] {1, 127, 128, 246}) {
      byte[] pdu = writeRequestPdu(0x0F, byteCount * 8, byteCount);

      parseValidRequest(frame(0x01, pdu));
    }
  }

  @Test
  void writeMultipleRegisters_ByteCounts() {
    // 246 bytes is the maximum: 123 registers.
    for (int byteCount : new int[] {2, 126, 128, 246}) {
      byte[] pdu = writeRequestPdu(0x10, byteCount / 2, byteCount);

      parseValidRequest(frame(0x01, pdu));
    }
  }

  @Test
  void readWriteMultipleRegisters_ByteCounts() {
    // 242 bytes is the maximum: 121 registers.
    for (int byteCount : new int[] {2, 126, 128, 242}) {
      ByteBuffer pdu = ByteBuffer.allocate(10 + byteCount);
      pdu.put((byte) 0x17);
      pdu.putShort((short) 0x0000);
      pdu.putShort((short) 125);
      pdu.putShort((short) 0x0100);
      pdu.putShort((short) (byteCount / 2));
      pdu.put((byte) byteCount);
      pdu.put(values(byteCount));

      parseValidRequest(frame(0x01, pdu.array()));
    }
  }

  /**
   * Parse {@code validRequestData} split into chunks of every size from 1 byte to the whole frame,
   * and verify the unit id, PDU, and CRC of the resulting frame each time. The CRC must also match
   * one calculated over the unit id and PDU the parser located.
   */
  private void parseValidRequest(byte[] validRequestData) {
    var parser = new ModbusRtuRequestFrameParser();

    for (int i = 1; i <= validRequestData.length; i++) {
      parser.reset();

      partitions(validRequestData, i).forEach(parser::parse);

      ParserState state = parser.getState();
      String message = "frame length %d, chunk size %d".formatted(validRequestData.length, i);
      Accumulated a = assertInstanceOf(Accumulated.class, state, message);
      ModbusRtuFrame frame = a.frame();

      int expectedUnitId = validRequestData[0] & 0xFF;
      ByteBuffer expectedPdu = ByteBuffer.wrap(validRequestData, 1, validRequestData.length - 3);
      ByteBuffer expectedCrc = ByteBuffer.wrap(validRequestData, validRequestData.length - 2, 2);
      assertEquals(expectedUnitId, frame.unitId(), message);
      assertEquals(expectedPdu, frame.pdu(), message);
      assertEquals(expectedCrc, frame.crc(), message);
      assertEquals(ByteBuffer.wrap(crc(frame.unitId(), frame.pdu())), frame.crc(), message);
    }
  }

  private static byte[] writeRequestPdu(int functionCode, int quantity, int byteCount) {
    ByteBuffer pdu = ByteBuffer.allocate(6 + byteCount);
    pdu.put((byte) functionCode);
    pdu.putShort((short) 0x0000);
    pdu.putShort((short) quantity);
    pdu.put((byte) byteCount);
    pdu.put(values(byteCount));
    return pdu.array();
  }

  /** Values that include bytes with the high bit set. */
  private static byte[] values(int length) {
    var values = new byte[length];
    for (int i = 0; i < length; i++) {
      values[i] = (byte) (0x80 + i);
    }
    return values;
  }

  /** Build an RTU frame: unit id, PDU, and a valid CRC. */
  private static byte[] frame(int unitId, byte[] pdu) {
    byte[] crc = crc(unitId, ByteBuffer.wrap(pdu));

    ByteBuffer frame = ByteBuffer.allocate(1 + pdu.length + 2);
    frame.put((byte) unitId);
    frame.put(pdu);
    frame.put(crc);

    return frame.array();
  }

  /** Calculate the CRC over the unit id and PDU, in wire (little-endian) order. */
  private static byte[] crc(int unitId, ByteBuffer pdu) {
    var crc16 = new Crc16();
    crc16.update(unitId);
    crc16.update(pdu);

    int value = crc16.getValue();
    return new byte[] {(byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF)};
  }
}
