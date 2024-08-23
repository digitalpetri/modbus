package com.digitalpetri.modbus;

import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.StringJoiner;

/**
 * Modbus/TCP frame data, an {@link MbapHeader} and encoded PDU.
 *
 * @param header the {@link MbapHeader} for this frame.
 * @param pdu the encoded Modbus PDU data.
 */
public record ModbusTcpFrame(MbapHeader header, ByteBuffer pdu) {

  @Override
  public String toString() {
    // note: overridden to give preferred representation of `pdu` bytes
    return new StringJoiner(", ", ModbusTcpFrame.class.getSimpleName() + "[", "]")
        .add("header=" + header)
        .add("pdu=" + Hex.format(pdu))
        .toString();
  }

}
