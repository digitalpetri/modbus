package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#WRITE_MULTIPLE_REGISTERS} response PDU.
 *
 * @param address the starting address. 2 bytes, range [0x0000, 0xFFFF].
 * @param quantity the quantity of registers written to. 2 bytes, range [0x0001, 0x007B].
 */
public record WriteMultipleRegistersResponse(int address, int quantity)
    implements ModbusResponsePdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.WRITE_MULTIPLE_REGISTERS.getCode();
  }

  /**
   * Utility functions for encoding and decoding {@link WriteMultipleRegistersResponse}.
   */
  public static class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link WriteMultipleRegistersResponse} into a {@link ByteBuffer}.
     *
     * @param response the response to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(WriteMultipleRegistersResponse response, ByteBuffer buffer) {
      buffer.put((byte) response.getFunctionCode());
      buffer.putShort((short) response.address);
      buffer.putShort((short) response.quantity);
    }

    /**
     * Decode a {@link WriteMultipleRegistersResponse} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded response.
     */
    public static WriteMultipleRegistersResponse decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.WRITE_MULTIPLE_REGISTERS.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int quantity = buffer.getShort() & 0xFFFF;

      return new WriteMultipleRegistersResponse(address, quantity);
    }

  }

}
