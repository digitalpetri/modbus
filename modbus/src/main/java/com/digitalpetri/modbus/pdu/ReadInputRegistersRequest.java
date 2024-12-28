package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#READ_INPUT_REGISTERS} request PDU.
 *
 * <p>Requests specify the starting register address and the number of register to read. In the PDU,
 * addresses are addressed starting at 0.
 *
 * @param address the starting address. 2 bytes, range [0x0000, 0xFFFF].
 * @param quantity the quantity of registers to read. 2 bytes, range [0x01, 0x7D].
 */
public record ReadInputRegistersRequest(int address, int quantity) implements ModbusRequestPdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.READ_INPUT_REGISTERS.getCode();
  }

  /** Utility functions for encoding and decoding {@link ReadInputRegistersRequest}. */
  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link ReadInputRegistersRequest} into a {@link ByteBuffer}.
     *
     * @param request the request to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(ReadInputRegistersRequest request, ByteBuffer buffer) {
      buffer.put((byte) request.getFunctionCode());
      buffer.putShort((short) request.address);
      buffer.putShort((short) request.quantity);
    }

    /**
     * Decode a {@link ReadInputRegistersRequest} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded request.
     */
    public static ReadInputRegistersRequest decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.READ_INPUT_REGISTERS.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int quantity = buffer.getShort() & 0xFFFF;

      return new ReadInputRegistersRequest(address, quantity);
    }
  }
}
