package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A {@link FunctionCode#READ_WRITE_MULTIPLE_REGISTERS} request PDU.
 *
 * @param readAddress the starting address to read from. 2 bytes, range [0x0000, 0xFFFF].
 * @param readQuantity the quantity of registers to read. 2 bytes, range [0x01, 0x7D].
 * @param writeAddress the starting address to write to. 2 bytes, range [0x0000, 0xFFFF].
 * @param writeQuantity the quantity of registers to write. 2 bytes, range [0x01, 0x79].
 * @param values the register values to write. 2 bytes per register.
 */
public record ReadWriteMultipleRegistersRequest(
    int readAddress,
    int readQuantity,
    int writeAddress,
    int writeQuantity,
    byte[] values
) implements ModbusRequestPdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.READ_WRITE_MULTIPLE_REGISTERS.getCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReadWriteMultipleRegistersRequest that = (ReadWriteMultipleRegistersRequest) o;
    return readAddress == that.readAddress && readQuantity == that.readQuantity
        && writeAddress == that.writeAddress && writeQuantity == that.writeQuantity
        && Objects.deepEquals(values, that.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(readAddress, readQuantity, writeAddress, writeQuantity,
        Arrays.hashCode(values));
  }

  @Override
  public String toString() {
    // note: overridden to give preferred representation of `values` bytes
    return new StringJoiner(
        ", ", ReadWriteMultipleRegistersRequest.class.getSimpleName() + "[", "]")
        .add("readAddress=" + readAddress)
        .add("readQuantity=" + readQuantity)
        .add("writeAddress=" + writeAddress)
        .add("writeQuantity=" + writeQuantity)
        .add("values=" + Hex.format(values))
        .toString();
  }

  /**
   * Utility functions for encoding and decoding {@link ReadWriteMultipleRegistersRequest}.
   */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link ReadWriteMultipleRegistersRequest} into a {@link ByteBuffer}.
     *
     * @param request the request to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(ReadWriteMultipleRegistersRequest request, ByteBuffer buffer) {
      buffer.put((byte) request.getFunctionCode());
      buffer.putShort((short) request.readAddress);
      buffer.putShort((short) request.readQuantity);
      buffer.putShort((short) request.writeAddress);
      buffer.putShort((short) request.writeQuantity);
      buffer.put((byte) (2 * request.writeQuantity));
      buffer.put(request.values);
    }

    /**
     * Decode a {@link ReadWriteMultipleRegistersRequest} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded request.
     */
    public static ReadWriteMultipleRegistersRequest decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.READ_WRITE_MULTIPLE_REGISTERS.getCode();

      int readAddress = buffer.getShort() & 0xFFFF;
      int readQuantity = buffer.getShort() & 0xFFFF;
      int writeAddress = buffer.getShort() & 0xFFFF;
      int writeQuantity = buffer.getShort() & 0xFFFF;
      int byteCount = buffer.get() & 0xFF;
      byte[] values = new byte[byteCount];
      buffer.get(values);

      return new ReadWriteMultipleRegistersRequest(
          readAddress,
          readQuantity,
          writeAddress,
          writeQuantity,
          values
      );
    }

  }

}
