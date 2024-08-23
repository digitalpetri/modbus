package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.StringJoiner;

/**
 * A {@link FunctionCode#READ_DISCRETE_INPUTS} response PDU.
 */
public record ReadDiscreteInputsResponse(byte[] inputs) implements ModbusResponsePdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.READ_DISCRETE_INPUTS.getCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReadDiscreteInputsResponse that = (ReadDiscreteInputsResponse) o;
    return Arrays.equals(inputs, that.inputs);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(inputs);
  }

  @Override
  public String toString() {
    // note: overridden to give preferred representation of `inputs` bytes
    return new StringJoiner(", ", ReadDiscreteInputsResponse.class.getSimpleName() + "[", "]")
        .add("inputs=" + Hex.format(inputs))
        .toString();
  }

  /**
   * Utility functions for encoding and decoding {@link ReadDiscreteInputsResponse}.
   */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link ReadDiscreteInputsResponse} into a {@link ByteBuffer}.
     *
     * @param response the response to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(ReadDiscreteInputsResponse response, ByteBuffer buffer) {
      buffer.put((byte) response.getFunctionCode());
      buffer.put((byte) response.inputs.length);
      buffer.put(response.inputs);
    }

    /**
     * Decode a {@link ReadDiscreteInputsResponse} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded response.
     */
    public static ReadDiscreteInputsResponse decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.READ_DISCRETE_INPUTS.getCode();

      int byteCount = buffer.get() & 0xFF;
      var inputs = new byte[byteCount];
      buffer.get(inputs);

      return new ReadDiscreteInputsResponse(inputs);
    }

  }

}
