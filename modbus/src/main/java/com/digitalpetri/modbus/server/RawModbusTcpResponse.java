package com.digitalpetri.modbus.server;

/**
 * A raw Modbus/TCP response PDU to write after MBAP framing.
 *
 * <p>The {@code pdu} is the complete response PDU, including the response function or status byte.
 * The TCP server writes these bytes as-is and computes only the MBAP metadata around them.
 *
 * <p>This record intentionally keeps ownership simple: it does not validate {@code pdu} and does
 * not clone it. Callers that construct or receive a raw response must treat the PDU array as
 * explicitly owned by the local request handling path.
 *
 * @param pdu the complete response PDU.
 */
public record RawModbusTcpResponse(byte[] pdu) {

  /**
   * Build a same-function raw response PDU from a function code and payload.
   *
   * <p>This is only a convenience for handlers that want response bytes shaped as {@code
   * [functionCode][payload...]}; the server does not interpret or rewrite the returned PDU.
   *
   * @param functionCode the response function code.
   * @param payload the response payload bytes following the function code.
   * @return a raw Modbus/TCP response containing {@code functionCode} followed by {@code payload}.
   */
  public static RawModbusTcpResponse response(int functionCode, byte[] payload) {
    byte[] pdu = new byte[1 + payload.length];
    pdu[0] = (byte) functionCode;
    System.arraycopy(payload, 0, pdu, 1, payload.length);
    return new RawModbusTcpResponse(pdu);
  }

  /**
   * Build a standard Modbus exception PDU explicitly.
   *
   * <p>This helper is for raw handlers that intentionally want standard Modbus exception encoding.
   * The raw TCP server never applies the {@code functionCode + 0x80} rule automatically.
   *
   * @param functionCode the request function code.
   * @param exceptionCode the Modbus exception code.
   * @return a raw Modbus/TCP response containing the standard exception PDU.
   */
  public static RawModbusTcpResponse modbusException(int functionCode, int exceptionCode) {
    return new RawModbusTcpResponse(
        new byte[] {(byte) (functionCode + 0x80), (byte) exceptionCode});
  }
}
