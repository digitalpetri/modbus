package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#WRITE_SINGLE_COIL} request PDU.
 *
 * <p>Requests specify the address of the coil to be forced. In the PDU, coils are addressed
 * starting at 0.
 *
 * @param address the address of the coil to force. 2 bytes, range [0x0000, 0xFFFF].
 * @param value the value to force.
 */
public record WriteSingleCoilRequest(int address, int value) implements ModbusRequestPdu {

  /**
   * @see #WriteSingleCoilRequest(int, int)
   */
  public WriteSingleCoilRequest(int address, boolean value) {
    this(address, value ? 0xFF00 : 0x0000);
  }

  @Override
  public int getFunctionCode() {
    return FunctionCode.WRITE_SINGLE_COIL.getCode();
  }

  /**
   * Utility functions for encoding and decoding {@link WriteSingleCoilRequest}.
   */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link WriteSingleCoilRequest} into a {@link ByteBuffer}.
     *
     * @param request the request to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(WriteSingleCoilRequest request, ByteBuffer buffer) {
      buffer.put((byte) request.getFunctionCode());
      buffer.putShort((short) request.address);
      buffer.putShort((short) request.value);
    }

    /**
     * Decode a {@link WriteSingleCoilRequest} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded request.
     */
    public static WriteSingleCoilRequest decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.WRITE_SINGLE_COIL.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int value = buffer.getShort() & 0xFFFF;

      return new WriteSingleCoilRequest(address, value);
    }

  }

}
