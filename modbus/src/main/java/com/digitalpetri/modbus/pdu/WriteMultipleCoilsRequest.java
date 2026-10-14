package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A {@link FunctionCode#WRITE_MULTIPLE_COILS} request PDU.
 *
 * @param address the starting address. 2 bytes, range [0x0000, 0xFFFF].
 * @param quantity the quantity of coils to write. 2 bytes, range [0x0001, 0x7B0].
 * @param values a buffer of exactly N bytes, where N = (quantity + 7) / 8. Unused bits in the last
 *     byte must be zero.
 */
public record WriteMultipleCoilsRequest(int address, int quantity, byte[] values)
    implements ModbusRequestPdu {

  @Override
  public void validate() {
    RequestValidation.checkRange("quantity", quantity, 1, 1968);
    RequestValidation.checkAddressRange("address", address, quantity);
    RequestValidation.checkPayloadLength(values, (quantity + 7) / 8);

    int remainder = quantity % 8;
    if (remainder != 0 && (values[values.length - 1] & (0xFF << remainder)) != 0) {
      throw new IllegalArgumentException("unused bits in the last values byte must be zero");
    }
  }

  @Override
  public int getFunctionCode() {
    return FunctionCode.WRITE_MULTIPLE_COILS.getCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WriteMultipleCoilsRequest that = (WriteMultipleCoilsRequest) o;
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
    return new StringJoiner(", ", WriteMultipleCoilsRequest.class.getSimpleName() + "[", "]")
        .add("address=" + address)
        .add("quantity=" + quantity)
        .add("values=" + Hex.format(values))
        .toString();
  }

  /** Utility functions for encoding and decoding {@link WriteMultipleCoilsRequest}. */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link WriteMultipleCoilsRequest} into a {@link ByteBuffer}.
     *
     * @param request the request to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(WriteMultipleCoilsRequest request, ByteBuffer buffer) {
      request.validate();

      buffer.put((byte) request.getFunctionCode());
      buffer.putShort((short) request.address);
      buffer.putShort((short) request.quantity);

      int byteCount = (request.quantity + 7) / 8;
      buffer.put((byte) byteCount);
      buffer.put(request.values);
    }

    /**
     * Decode a {@link WriteMultipleCoilsRequest} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded request.
     */
    public static WriteMultipleCoilsRequest decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.WRITE_MULTIPLE_COILS.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int quantity = buffer.getShort() & 0xFFFF;

      int byteCount = buffer.get() & 0xFF;
      var values = new byte[byteCount];
      buffer.get(values);

      return new WriteMultipleCoilsRequest(address, quantity, values);
    }
  }
}
