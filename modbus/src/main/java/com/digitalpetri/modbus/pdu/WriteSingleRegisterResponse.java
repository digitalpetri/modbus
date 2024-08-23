package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#WRITE_SINGLE_REGISTER} response PDU.
 *
 * @param address the address of the register written to. 2 bytes, range [0x0000, 0xFFFF].
 * @param value the value written. 2 bytes, range [0x0000, 0xFFFF].
 */
public record WriteSingleRegisterResponse(int address, int value) implements ModbusResponsePdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.WRITE_SINGLE_REGISTER.getCode();
  }

  /**
   * Utility functions for encoding and decoding {@link WriteSingleRegisterResponse}.
   */
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
      buffer.putShort((short) response.value);
    }

    /**
     * Decode a {@link WriteSingleRegisterResponse} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded response.
     */
    public static WriteSingleRegisterResponse decode(ByteBuffer buffer) {
      int functionCode = buffer.get();
      assert functionCode == FunctionCode.WRITE_SINGLE_REGISTER.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int value = buffer.getShort() & 0xFFFF;

      return new WriteSingleRegisterResponse(address, value);
    }

  }

}
