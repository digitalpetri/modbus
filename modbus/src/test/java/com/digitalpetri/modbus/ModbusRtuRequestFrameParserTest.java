package com.digitalpetri.modbus;

import static com.digitalpetri.modbus.Util.partitions;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.Accumulated;
import com.digitalpetri.modbus.ModbusRtuRequestFrameParser.ParserState;
import java.nio.ByteBuffer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ModbusRtuRequestFrameParserTest {

  private static final byte[] READ_COILS = new byte[]{
      0x01,
      0x01,
      0x00, 0x00,
      0x00, 0x08,
      (byte) 0xCA, (byte) 0xFE
  };

  private static final byte[] WRITE_MULTIPLE_REGISTERS = new byte[]{
      0x01,
      0x10,
      0x00, 0x00,
      0x00, 0x02,
      0x04,
      0x00, 0x01,
      0x00, 0x02,
      (byte) 0x8B, (byte) 0x3A
  };

  @Test
  void readCoils() {
    parseValidRequest(READ_COILS);
  }


  @Test
  void writeMultipleRegisters() {
    parseValidRequest(WRITE_MULTIPLE_REGISTERS);
  }

  private void parseValidRequest(byte[] validRequestData) {
    var parser = new ModbusRtuRequestFrameParser();


    for (int i = 1; i <= validRequestData.length; i++) {
      parser.reset();

      Stream<byte[]> chunks = partitions(validRequestData, i);

      chunks.forEach(chunk -> {
        ParserState state = parser.parse(chunk);
        System.out.println(state);
      });
      System.out.println("--");

      ParserState state = parser.getState();
      if (state instanceof Accumulated a) {
        int expectedUnitId = validRequestData[0] & 0xFF;
        ByteBuffer expectedPdu = ByteBuffer.wrap(
            validRequestData, 1, validRequestData.length - 3);
        ByteBuffer expectedCrc = ByteBuffer.wrap(
            validRequestData, validRequestData.length - 2, 2);
        assertEquals(expectedUnitId, a.frame().unitId());
        assertEquals(expectedPdu, a.frame().pdu());
        assertEquals(expectedCrc, a.frame().crc());
      }
    }
  }

}
