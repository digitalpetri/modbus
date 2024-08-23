package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#MASK_WRITE_REGISTER} request PDU.
 *
 * @param address the address, 2 bytes, range [0x0000, 0xFFFF].
 * @param andMask the AND mask, 2 bytes, range [0x0000, 0xFFFF].
 * @param orMask the OR mask, 2 bytes, range [0x0000, 0xFFFF].
 */
public record MaskWriteRegisterRequest(int address, int andMask, int orMask)
    implements ModbusRequestPdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.MASK_WRITE_REGISTER.getCode();
  }

  /**
   * Utility functions for encoding and decoding {@link MaskWriteRegisterRequest}.
   */
  public static class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link MaskWriteRegisterRequest} into a {@link ByteBuffer}.
     *
     * @param request the request to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(MaskWriteRegisterRequest request, ByteBuffer buffer) {
      buffer.put((byte) request.getFunctionCode());
      buffer.putShort((short) request.address);
      buffer.putShort((short) request.andMask);
      buffer.putShort((short) request.orMask);
    }

    /**
     * Decode a {@link MaskWriteRegisterRequest} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded request.
     */
    public static MaskWriteRegisterRequest decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.MASK_WRITE_REGISTER.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int andMask = buffer.getShort() & 0xFFFF;
      int orMask = buffer.getShort() & 0xFFFF;

      return new MaskWriteRegisterRequest(address, andMask, orMask);
    }

  }

}
