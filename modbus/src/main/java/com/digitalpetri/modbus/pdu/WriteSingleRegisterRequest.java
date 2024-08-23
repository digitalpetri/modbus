package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#WRITE_SINGLE_REGISTER} request PDU.
 *
 * @param address the address of the register to write. 2 bytes, range [0x0000, 0xFFFF].
 * @param value the value to write. 2 bytes, range [0x0000, 0xFFFF].
 */
public record WriteSingleRegisterRequest(int address, int value) implements ModbusRequestPdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.WRITE_SINGLE_REGISTER.getCode();
  }

  /**
   * Utility functions for encoding and decoding {@link WriteSingleRegisterRequest}.
   */
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
      buffer.putShort((short) request.value);
    }

    /**
     * Decode a {@link WriteSingleRegisterRequest} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded request.
     */
    public static WriteSingleRegisterRequest decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.WRITE_SINGLE_REGISTER.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int value = buffer.getShort() & 0xFFFF;

      return new WriteSingleRegisterRequest(address, value);
    }

  }

}
