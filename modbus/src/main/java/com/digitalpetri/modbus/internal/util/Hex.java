package com.digitalpetri.modbus.internal.util;

import java.nio.ByteBuffer;
import java.util.HexFormat;

public class Hex {

  private Hex() {}

  /**
   * Format a {@link ByteBuffer} as a hex string.
   *
   * <p>Only the bytes between {@link ByteBuffer#position()} and {@link ByteBuffer#limit()} are
   * considered.
   *
   * @param buffer the buffer to format.
   * @return the formatted hex string.
   */
  public static String format(ByteBuffer buffer) {
    StringBuilder sb = new StringBuilder();
    for (int i = buffer.position(); i < buffer.limit(); i++) {
      sb.append(String.format("%02x", buffer.get(i)));
    }
    return sb.toString();
  }

  /**
   * Format a byte array as a hex string.
   *
   * @param bytes the bytes to format.
   * @return the formatted hex string.
   */
  public static String format(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }
}
