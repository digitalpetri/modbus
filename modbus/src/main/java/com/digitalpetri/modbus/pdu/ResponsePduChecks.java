package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/** Format checks shared by the built-in response decoders. */
final class ResponsePduChecks {

  private ResponsePduChecks() {}

  static void checkLength(ByteBuffer buffer, FunctionCode functionCode, int expected) {
    if (buffer.remaining() != expected) {
      throw new IllegalArgumentException(
          "invalid %s response PDU length: expected %d bytes, received %d"
              .formatted(functionCode, expected, buffer.remaining()));
    }
    checkFunctionCode(buffer, functionCode);
  }

  static int readByteCount(ByteBuffer buffer, FunctionCode functionCode) {
    if (buffer.remaining() < 2) {
      throw new IllegalArgumentException(
          "truncated %s response PDU: expected function code and byte count"
              .formatted(functionCode));
    }

    int byteCount = buffer.get(buffer.position() + 1) & 0xFF;
    checkLength(buffer, functionCode, 2 + byteCount);
    checkRange(functionCode, "byte count", byteCount, 1, 250);

    buffer.get(); // function code
    buffer.get(); // byte count
    return byteCount;
  }

  static int readRegisterByteCount(ByteBuffer buffer, FunctionCode functionCode) {
    int byteCount = readByteCount(buffer, functionCode);
    if (byteCount % 2 != 0) {
      throw new IllegalArgumentException(
          "invalid %s response register byte count: expected an even count, received %d"
              .formatted(functionCode, byteCount));
    }
    return byteCount;
  }

  static void checkRange(FunctionCode functionCode, String field, int value, int min, int max) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(
          "invalid %s response %s: expected %d to %d, received %d"
              .formatted(functionCode, field, min, max, value));
    }
  }

  static void checkFunctionCode(ByteBuffer buffer, FunctionCode expected) {
    int actual = buffer.get(buffer.position()) & 0xFF;
    if (actual != expected.getCode()) {
      throw new IllegalArgumentException(
          "invalid %s response function code: expected 0x%02X, received 0x%02X"
              .formatted(expected, expected.getCode(), actual));
    }
  }
}
