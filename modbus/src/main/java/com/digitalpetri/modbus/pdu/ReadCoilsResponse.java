package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.StringJoiner;

/**
 * A {@link FunctionCode#READ_COILS} response PDU.
 *
 * <p>The coils in the response message are packed as one coil per-bit. Status is indicated as 1=ON
 * and 0=OFF.
 *
 * <p>The LSB of the first data byte contains the output addressed in the query. The other coils
 * follow toward the high order end of this byte, and from low order to high order in subsequent
 * bytes.
 *
 * <p>If the returned output quantity is not a multiple of eight, the remaining bits in the last
 * byte will be padded with zeros (toward the high order end of the byte).
 *
 * @param coils the {@code byte[]} containing coil status, 8 coils per-byte.
 */
public record ReadCoilsResponse(byte[] coils) implements ModbusResponsePdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.READ_COILS.getCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReadCoilsResponse that = (ReadCoilsResponse) o;
    return Arrays.equals(coils, that.coils);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(coils);
  }

  @Override
  public String toString() {
    // note: overridden to give preferred representation of `coils` bytes
    return new StringJoiner(", ", ReadCoilsResponse.class.getSimpleName() + "[", "]")
        .add("coils=" + Hex.format(coils))
        .toString();
  }

  /** Utility functions for encoding and decoding {@link ReadCoilsResponse}. */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link ReadCoilsResponse} into a {@link ByteBuffer}.
     *
     * @param response the response to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(ReadCoilsResponse response, ByteBuffer buffer) {
      buffer.put((byte) response.getFunctionCode());
      buffer.put((byte) response.coils.length);
      buffer.put(response.coils);
    }

    /**
     * Decode a {@link ReadCoilsResponse} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded response.
     */
    public static ReadCoilsResponse decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.READ_COILS.getCode();

      int byteCount = buffer.get() & 0xFF;
      var coils = new byte[byteCount];
      buffer.get(coils);

      return new ReadCoilsResponse(coils);
    }
  }
}
