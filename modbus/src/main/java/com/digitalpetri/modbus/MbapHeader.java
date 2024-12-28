package com.digitalpetri.modbus;

import java.nio.ByteBuffer;

/**
 * Modbus Application Protocol header for frames that encapsulates Modbus request and response PDUs
 * on TCP/IP.
 *
 * @param transactionId transaction identifier. 2 bytes, identifies a request/response transaction.
 * @param protocolId protocol identifier. 2 bytes, always 0 for Modbus protocol.
 * @param length number of bytes that follow, including 1 for the unit id. 2 bytes.
 * @param unitId identifier of a remote slave connected on a physical or logical other bus. 1 byte.
 */
public record MbapHeader(int transactionId, int protocolId, int length, int unitId) {

  /** Utility functions for encoding and decoding {@link MbapHeader}. */
  public static class Serializer {

    private Serializer() {}

    /**
     * Encode a {@link MbapHeader} into a {@link ByteBuffer}.
     *
     * @param header the header to encode.
     * @param buffer the buffer to encode into.
     */
    public static void encode(MbapHeader header, ByteBuffer buffer) {
      buffer.putShort((short) header.transactionId);
      buffer.putShort((short) header.protocolId);
      buffer.putShort((short) header.length);
      buffer.put((byte) header.unitId);
    }

    /**
     * Decode a {@link MbapHeader} from a {@link ByteBuffer}.
     *
     * @param buffer the buffer to decode from.
     * @return the decoded header.
     */
    public static MbapHeader decode(ByteBuffer buffer) {
      int transactionId = buffer.getShort() & 0xFFFF;
      int protocolId = buffer.getShort() & 0xFFFF;
      int length = buffer.getShort() & 0xFFFF;
      int unitId = buffer.get() & 0xFF;

      return new MbapHeader(transactionId, protocolId, length, unitId);
    }
  }
}
