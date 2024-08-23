package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#READ_COILS} request PDU.
 *
 * <p>Requests specify the starting address, i.e. the address of the first coil specified, and the
 * number of coils. In the PDU Coils are addressed starting at 0.
 *
 * @param address the starting address. 2 bytes, range [0x0000, 0xFFFF].
 * @param quantity the quantity of coils to read. 2 bytes, range [0x0001, 0x07D0].
 */
public record ReadCoilsRequest(int address, int quantity) implements ModbusRequestPdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.READ_COILS.getCode();
  }

  /**
   * Utility functions for encoding and decoding {@link ReadCoilsRequest}.
   */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link ReadCoilsRequest} into a {@link ByteBuffer}.
     *
     * @param request the request to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(ReadCoilsRequest request, ByteBuffer buffer) {
      buffer.put((byte) request.getFunctionCode());
      buffer.putShort((short) request.address);
      buffer.putShort((short) request.quantity);
    }

    /**
     * Decode a {@link ReadCoilsRequest} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded request.
     */
    public static ReadCoilsRequest decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.READ_COILS.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int quantity = buffer.getShort() & 0xFFFF;

      return new ReadCoilsRequest(address, quantity);
    }

  }

}
