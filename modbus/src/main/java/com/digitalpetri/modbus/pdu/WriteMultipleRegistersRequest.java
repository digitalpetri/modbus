package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A {@link FunctionCode#WRITE_MULTIPLE_REGISTERS} request PDU.
 *
 * @param address the starting address to write to. 2 bytes, range [0x0000, 0xFFFF].
 * @param quantity the number of registers to write. 2 bytes, range [0x0001, 0x007B].
 * @param values the values to write. Must be at least {@code 2 * quantity} bytes.
 */
public record WriteMultipleRegistersRequest(int address, int quantity, byte[] values)
    implements ModbusRequestPdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.WRITE_MULTIPLE_REGISTERS.getCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WriteMultipleRegistersRequest that = (WriteMultipleRegistersRequest) o;
    return Objects.equals(address, that.address)
        && Objects.equals(quantity, that.quantity)
        && Arrays.equals(values, that.values);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(address, quantity);
    result = 31 * result + Arrays.hashCode(values);
    return result;
  }

  @Override
  public String toString() {
    // note: overridden to give preferred representation of `values` bytes
    return new StringJoiner(", ", WriteMultipleRegistersRequest.class.getSimpleName() + "[", "]")
        .add("address=" + address)
        .add("quantity=" + quantity)
        .add("values=" + Hex.format(values))
        .toString();
  }

  /** Utility functions for encoding and decoding {@link WriteMultipleRegistersRequest}. */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link WriteMultipleRegistersRequest} into a {@link ByteBuffer}.
     *
     * @param request the request to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(WriteMultipleRegistersRequest request, ByteBuffer buffer) {
      buffer.put((byte) request.getFunctionCode());
      buffer.putShort((short) request.address);
      buffer.putShort((short) request.quantity);

      buffer.put((byte) request.values.length);
      buffer.put(request.values);
    }

    /**
     * Decode a {@link WriteMultipleRegistersRequest} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded request.
     */
    public static WriteMultipleRegistersRequest decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.WRITE_MULTIPLE_REGISTERS.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int quantity = buffer.getShort() & 0xFFFF;
      int byteCount = buffer.get() & 0xFF;

      var values = new byte[byteCount];
      buffer.get(values);

      return new WriteMultipleRegistersRequest(address, quantity, values);
    }
  }
}
