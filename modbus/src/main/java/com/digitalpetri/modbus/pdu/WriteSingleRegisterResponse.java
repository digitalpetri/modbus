package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A {@link FunctionCode#WRITE_SINGLE_REGISTER} response PDU.
 *
 * <p>A successful response echoes the address and every value byte of its {@link
 * WriteSingleRegisterRequest}. A standard register value is 2 bytes. The built-in Modbus RTU
 * framing only supports 2-byte values.
 *
 * <p>The {@code value} array is not copied. It is encoded as-is, and {@link #value()} returns the
 * same array. Do not modify the array after constructing the response.
 *
 * @param address the address of the register written to. 2 bytes, range [0x0000, 0xFFFF].
 * @param value the value bytes written, in wire order. 2 bytes for a standard register.
 */
public record WriteSingleRegisterResponse(int address, byte[] value) implements ModbusResponsePdu {

  /**
   * Create a standard {@link WriteSingleRegisterResponse} with a 2-byte value.
   *
   * <p>The low 16 bits of {@code value} are encoded in big-endian order. Higher bits are ignored.
   *
   * @param address the address of the register written to. 2 bytes, range [0x0000, 0xFFFF].
   * @param value the value written. The low 16 bits are used.
   */
  public WriteSingleRegisterResponse(int address, int value) {
    this(address, new byte[] {(byte) (value >> 8), (byte) value});
  }

  @Override
  public int getFunctionCode() {
    return FunctionCode.WRITE_SINGLE_REGISTER.getCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WriteSingleRegisterResponse that = (WriteSingleRegisterResponse) o;
    return Objects.equals(address, that.address) && Arrays.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(address);
    result = 31 * result + Arrays.hashCode(value);
    return result;
  }

  @Override
  public String toString() {
    // note: overridden to give preferred representation of `value` bytes
    return new StringJoiner(", ", WriteSingleRegisterResponse.class.getSimpleName() + "[", "]")
        .add("address=" + address)
        .add("value=" + Hex.format(value))
        .toString();
  }

  /** Utility functions for encoding and decoding {@link WriteSingleRegisterResponse}. */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link WriteSingleRegisterResponse} into a {@link ByteBuffer}.
     *
     * @param response the response to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(WriteSingleRegisterResponse response, ByteBuffer buffer) {
      buffer.put((byte) response.getFunctionCode());
      buffer.putShort((short) response.address);
      buffer.put(response.value);
    }

    /**
     * Decode a {@link WriteSingleRegisterResponse} from a {@link ByteBuffer}.
     *
     * <p>The buffer must contain exactly one framed PDU. All bytes remaining after the address are
     * decoded as the value.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded response.
     */
    public static WriteSingleRegisterResponse decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.WRITE_SINGLE_REGISTER.getCode();

      int address = buffer.getShort() & 0xFFFF;

      var value = new byte[buffer.remaining()];
      buffer.get(value);

      return new WriteSingleRegisterResponse(address, value);
    }
  }
}
