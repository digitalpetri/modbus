package com.digitalpetri.modbus.pdu;

import com.digitalpetri.modbus.FunctionCode;
import java.nio.ByteBuffer;

/**
 * A {@link FunctionCode#READ_DISCRETE_INPUTS} request PDU.
 *
 * <p>Requests specify the starting address, i.e. the address of the first input specified, and the
 * number of inputs. In the PDU inputs are addressed starting at 0.
 *
 * @param address the starting address. 2 bytes, range [0x0000, 0xFFFF].
 * @param quantity the quantity of inputs to read. 2 bytes, range [0x0001, 0x07D0].
 */
public record ReadDiscreteInputsRequest(int address, int quantity) implements ModbusRequestPdu {

  @Override
  public int getFunctionCode() {
    return FunctionCode.READ_DISCRETE_INPUTS.getCode();
  }

  public static final class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link ReadDiscreteInputsRequest} into a {@link ByteBuffer}.
     *
     * @param request the request to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(ReadDiscreteInputsRequest request, ByteBuffer buffer) {
      buffer.put((byte) request.getFunctionCode());
      buffer.putShort((short) request.address);
      buffer.putShort((short) request.quantity);
    }

    /**
     * Decode a {@link ReadDiscreteInputsRequest} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded request.
     */
    public static ReadDiscreteInputsRequest decode(ByteBuffer buffer) {
      int functionCode = buffer.get() & 0xFF;
      assert functionCode == FunctionCode.READ_DISCRETE_INPUTS.getCode();

      int address = buffer.getShort() & 0xFFFF;
      int quantity = buffer.getShort() & 0xFFFF;

      return new ReadDiscreteInputsRequest(address, quantity);
    }

  }

}
