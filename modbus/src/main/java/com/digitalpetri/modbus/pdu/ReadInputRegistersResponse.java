package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.StringJoiner;

/**
 * A {@link FunctionCode#READ_INPUT_REGISTERS} response PDU.
 *
 * <p>The register data in the response is packed as 2 bytes per register.
 *
 * @param registers the register data, 2 bytes per register requested.
 */
public record ReadInputRegistersResponse(byte[] registers) implements ModbusResponsePdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.READ_INPUT_REGISTERS.getCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReadInputRegistersResponse that = (ReadInputRegistersResponse) o;
    return Arrays.equals(registers, that.registers);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(registers);
  }

  @Override
  public String toString() {
    // note: overridden to give preferred representation of `registers` bytes
    return new StringJoiner(", ", ReadInputRegistersResponse.class.getSimpleName() + "[", "]")
        .add("registers=" + Hex.format(registers))
        .toString();
  }

  /** Utility functions for encoding and decoding {@link ReadInputRegistersResponse}. */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link ReadInputRegistersResponse} into a {@link ByteBuffer}.
     *
     * @param response the response to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(ReadInputRegistersResponse response, ByteBuffer buffer) {
      buffer.put((byte) response.getFunctionCode());
      buffer.put((byte) response.registers.length);
      buffer.put(response.registers);
    }

    /**
     * Decode a {@link ReadInputRegistersResponse} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded response.
     */
    public static ReadInputRegistersResponse decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.READ_INPUT_REGISTERS.getCode();

      int byteCount = buffer.get() & 0xFF;
      var registers = new byte[byteCount];
      buffer.get(registers);

      return new ReadInputRegistersResponse(registers);
    }
  }
}
