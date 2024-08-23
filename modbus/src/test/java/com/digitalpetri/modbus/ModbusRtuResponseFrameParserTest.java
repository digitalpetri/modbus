package com.digitalpetri.modbus;

import static com.digitalpetri.modbus.Util.partitions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.Accumulated;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.Accumulating;
import com.digitalpetri.modbus.ModbusRtuResponseFrameParser.ParserState;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ModbusRtuResponseFrameParserTest {

  private static final byte[] READ_COILS = new byte[]{
      0x01,
      0x01,
      0x02,
      0x01, 0x02,
      (byte) 0xCA, (byte) 0xFE
  };

  private static final byte[] READ_HOLDING_REGISTERS = new byte[]{
      0x01,
      0x03,
      0x04,
      0x01, 0x02, 0x03, 0x04,
      (byte) 0xCA, (byte) 0xFE
  };

  @Test
  void readCoils() {
    parseValidResponse(READ_COILS);
  }

  @Test
  void readHoldingRegisters() {
    parseValidResponse(READ_HOLDING_REGISTERS);
  }

  @Test
  void readCoils_InvalidLength() {
    byte[] invalidLengthResponse = Arrays.copyOf(
        READ_COILS,
        READ_COILS.length
    );

    invalidLengthResponse[2] = (byte) (invalidLengthResponse[2] * 2);

    parseInvalidLengthResponse(invalidLengthResponse);
  }

  @Test
  void readHoldingRegisters_InvalidLength() {
    byte[] invalidLengthResponse = Arrays.copyOf(
        READ_HOLDING_REGISTERS,
        READ_HOLDING_REGISTERS.length
    );

    invalidLengthResponse[2] = (byte) (invalidLengthResponse[2] * 2);

    parseInvalidLengthResponse(invalidLengthResponse);
  }

  private void parseValidResponse(byte[] validResponseData) {
    var parser = new ModbusRtuResponseFrameParser();

    for (int i = 1; i <= validResponseData.length; i++) {
      parser.reset();

      partitions(validResponseData, i).forEach(data -> {
        ParserState s = parser.parse(data);
        System.out.println(s);
      });
      System.out.println("--");

      ParserState state = parser.getState();
      if (state instanceof Accumulated a) {
        int expectedUnitId = validResponseData[0] & 0xFF;
        ByteBuffer expectedPdu = ByteBuffer.wrap(
            validResponseData, 1, validResponseData.length - 3);
        ByteBuffer expectedCrc = ByteBuffer.wrap(
            validResponseData, validResponseData.length - 2, 2);
        assertEquals(expectedUnitId, a.frame().unitId());
        assertEquals(expectedPdu, a.frame().pdu());
        assertEquals(expectedCrc, a.frame().crc());
      } else {
        fail("unexpected state: " + state);
      }
    }
  }

  private void parseInvalidLengthResponse(byte[] invalidLengthResponse) {
    var parser = new ModbusRtuResponseFrameParser();

    for (int i = 1; i <= invalidLengthResponse.length; i++) {
      parser.reset();

      Stream<byte[]> chunks = partitions(invalidLengthResponse, i);
      chunks.forEach(data -> {
        ParserState s = parser.parse(data);
        System.out.println(s);
      });
      System.out.println("--");

      ParserState state = parser.getState();

      assertInstanceOf(Accumulating.class, state);
    }
  }


}
