package com.digitalpetri.modbus.pdu;

/** Shared checks for standard request fields. */
final class RequestValidation {

  private RequestValidation() {}

  static void checkRange(String field, int value, int min, int max) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(
          "%s must be in %s..%s: %s".formatted(field, min, max, value));
    }
  }

  static void checkAddressRange(String field, int address, int quantity) {
    checkRange(field, address, 0, 65535);
    // Quantities have already been checked, so this addition cannot overflow.
    if (address + quantity > 65536) {
      throw new IllegalArgumentException(
          "%s range extends past 65535: address=%s, quantity=%s"
              .formatted(field, address, quantity));
    }
  }

  static void checkPayloadLength(byte[] values, int expected) {
    if (values == null || values.length != expected) {
      throw new IllegalArgumentException(
          "values must contain exactly %s bytes: %s"
              .formatted(expected, values == null ? "null" : values.length));
    }
  }
}
