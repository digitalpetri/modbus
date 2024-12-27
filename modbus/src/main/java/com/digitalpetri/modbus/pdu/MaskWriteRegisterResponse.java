package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#MASK_WRITE_REGISTER} response PDU.
 *
 * @param address the address, 2 bytes, range [0x0000, 0xFFFF].
 * @param andMask the AND mask, 2 bytes, range [0x0000, 0xFFFF].
 * @param orMask the OR mask, 2 bytes, range [0x0000, 0xFFFF].
 */
public record MaskWriteRegisterResponse(int address, int andMask, int orMask)
    implements ModbusResponsePdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.MASK_WRITE_REGISTER.getCode();
  }

  /** Utility functions for encoding and decoding {@link MaskWriteRegisterResponse}. */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link MaskWriteRegisterResponse} into a {@link ByteBuffer}.
     *
     * @param response the response to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(MaskWriteRegisterResponse response, ByteBuffer buffer) {
      buffer.put((byte) response.getFunctionCode());
      buffer.putShort((short) response.address);
      buffer.putShort((short) response.andMask);
      buffer.putShort((short) response.orMask);
    }

    /**
     * Decode a {@link MaskWriteRegisterResponse} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded response.
     */
    public static MaskWriteRegisterResponse decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.MASK_WRITE_REGISTER.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int andMask = buffer.getShort() & 0xFFFF;
      int orMask = buffer.getShort() & 0xFFFF;

      return new MaskWriteRegisterResponse(address, andMask, orMask);
    }
  }
}
