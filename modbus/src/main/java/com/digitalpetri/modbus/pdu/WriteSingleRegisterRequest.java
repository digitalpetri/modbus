package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A {@link FunctionCode#WRITE_SINGLE_REGISTER} request PDU.
 *
 * <p>A standard Modbus request carries a 2-byte value. A 4-byte value supports devices that store
 * 32-bit values at a single register address, such as Enron/Daniels Modbus devices. Only send a
 * 4-byte value to a device or service that expects it.
 *
 * <p>The built-in Modbus RTU framing assumes a 2-byte value, so 4-byte values are currently only
 * usable over Modbus TCP. The built-in RTU client still sends a 4-byte request, and the device may
 * apply the write, but the longer response then fails the CRC check. Do not send 4-byte values
 * through the built-in RTU transports.
 *
 * <p>The {@code value} array is not copied. It is encoded as-is, and {@link #value()} returns the
 * same array. Do not modify the array after constructing the request.
 *
 * @param address the address of the register to write. 2 bytes, range [0x0000, 0xFFFF].
 * @param value the value bytes to write, in wire order. Must be exactly 2 or 4 bytes.
 */
public record WriteSingleRegisterRequest(int address, byte[] value) implements ModbusRequestPdu {

  /**
   * Create a {@link WriteSingleRegisterRequest}.
   *
   * @param address the address of the register to write. 2 bytes, range [0x0000, 0xFFFF].
   * @param value the value bytes to write, in wire order. Must be exactly 2 or 4 bytes.
   * @throws IllegalArgumentException if {@code value} is not 2 or 4 bytes long.
   */
  public WriteSingleRegisterRequest {
    if (value.length != 2 && value.length != 4) {
      throw new IllegalArgumentException(
          "value must be 2 or 4 bytes, got %d".formatted(value.length));
    }
  }

  /**
   * Create a standard {@link WriteSingleRegisterRequest} with a 2-byte value.
   *
   * <p>The low 16 bits of {@code value} are encoded in big-endian order. Higher bits are ignored.
   *
   * @param address the address of the register to write. 2 bytes, range [0x0000, 0xFFFF].
   * @param value the value to write. The low 16 bits are used.
   */
  public WriteSingleRegisterRequest(int address, int value) {
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
    WriteSingleRegisterRequest that = (WriteSingleRegisterRequest) o;
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
    return new StringJoiner(", ", WriteSingleRegisterRequest.class.getSimpleName() + "[", "]")
        .add("address=" + address)
        .add("value=" + Hex.format(value))
        .toString();
  }

  /** Utility functions for encoding and decoding {@link WriteSingleRegisterRequest}. */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link WriteSingleRegisterRequest} into a {@link ByteBuffer}.
     *
     * @param request the request to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(WriteSingleRegisterRequest request, ByteBuffer buffer) {
      buffer.put((byte) request.getFunctionCode());
      buffer.putShort((short) request.address);
      buffer.put(request.value);
    }

    /**
     * Decode a {@link WriteSingleRegisterRequest} from a {@link ByteBuffer}.
     *
     * <p>The buffer must contain exactly one framed PDU. All bytes remaining after the address are
     * decoded as the value.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded request.
     * @throws IllegalArgumentException if the remaining value bytes are not 2 or 4 bytes long.
     */
    public static WriteSingleRegisterRequest decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.WRITE_SINGLE_REGISTER.getCode();

      int address = buffer.getShort() & 0xFFFF;

      var value = new byte[buffer.remaining()];
      buffer.get(value);

      return new WriteSingleRegisterRequest(address, value);
    }
  }
}
