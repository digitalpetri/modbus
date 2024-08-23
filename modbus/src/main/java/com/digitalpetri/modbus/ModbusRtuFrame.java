package com.digitalpetri.modbus;

import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.StringJoiner;

/**
 * Modbus/RTU frame data, a unit id and encoded PDU.
 *
 * @param unitId the identifier of a remote slave connected on a physical or logical other bus.
 * @param pdu the encoded Modbus PDU data.
 * @param crc the CRC bytes.
 */
public record ModbusRtuFrame(int unitId, ByteBuffer pdu, ByteBuffer crc) {

  @Override
  public String toString() {
    // note: overridden to give preferred representation of `pdu` and `crc` bytes
    return new StringJoiner(", ", ModbusRtuFrame.class.getSimpleName() + "[", "]")
        .add("unitId=" + unitId)
        .add("pdu=" + Hex.format(pdu))
        .add("crc=" + Hex.format(crc))
        .toString();
  }

}
