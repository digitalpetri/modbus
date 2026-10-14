package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#WRITE_SINGLE_COIL} response PDU.
 *
 * <p>The normal response is an echo of the request PDU, returned after the coil state has been
 * written.
 */
public record WriteSingleCoilResponse(int address, int value) implements ModbusResponsePdu {

  /**
   * @see #WriteSingleCoilResponse(int, int)
   */
  public WriteSingleCoilResponse(int address, boolean value) {
    this(address, value ? 0xFF00 : 0x0000);
  }

  @Override
  public int getFunctionCode() {
    return FunctionCode.WRITE_SINGLE_COIL.getCode();
  }

  /** Utility functions for encoding and decoding {@link WriteSingleCoilResponse}. */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link WriteSingleCoilResponse} into a {@link ByteBuffer}.
     *
     * @param response the response to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(WriteSingleCoilResponse response, ByteBuffer buffer) {
      buffer.put((byte) response.getFunctionCode());
      buffer.putShort((short) response.address);
      buffer.putShort((short) response.value);
    }

    /**
     * Decode a {@link WriteSingleCoilResponse} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer containing the complete response PDU.
     * @return the decoded response.
     * @throws IllegalArgumentException if the response PDU format is invalid.
     */
    public static WriteSingleCoilResponse decode(ByteBuffer buffer) {
      ResponsePduChecks.checkLength(buffer, FunctionCode.WRITE_SINGLE_COIL, 5);
      buffer.get(); // function code

      int address = buffer.getShort() & 0xFFFF;
      int value = buffer.getShort() & 0xFFFF;

      if (value != 0x0000 && value != 0xFF00) {
        throw new IllegalArgumentException(
            "invalid WRITE_SINGLE_COIL response value: expected 0x0000 or 0xFF00, received 0x%04X"
                .formatted(value));
      }

      return new WriteSingleCoilResponse(address, value);
    }
  }
}
