package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#WRITE_MULTIPLE_COILS} response PDU.
 *
 * <p>The normal response returns the function code, starting address, and quantity of coils forced.
 *
 * @param address the starting address. 2 bytes, range [0x0000, 0xFFFF].
 * @param quantity the quantity of coils to write. 2 bytes, range [0x0001, 0x7B0].
 */
public record WriteMultipleCoilsResponse(int address, int quantity) implements ModbusResponsePdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.WRITE_MULTIPLE_COILS.getCode();
  }

  /** Utility functions for encoding and decoding {@link WriteMultipleCoilsResponse}. */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link WriteMultipleCoilsResponse} into a {@link ByteBuffer}.
     *
     * @param response the response to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(WriteMultipleCoilsResponse response, ByteBuffer buffer) {
      buffer.put((byte) response.getFunctionCode());
      buffer.putShort((short) response.address);
      buffer.putShort((short) response.quantity);
    }

    /**
     * Decode a {@link WriteMultipleCoilsResponse} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded response.
     */
    public static WriteMultipleCoilsResponse decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.WRITE_MULTIPLE_COILS.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int quantity = buffer.getShort() & 0xFFFF;

      return new WriteMultipleCoilsResponse(address, quantity);
    }
  }
}
