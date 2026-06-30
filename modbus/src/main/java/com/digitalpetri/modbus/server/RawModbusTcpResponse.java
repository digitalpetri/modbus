package com.digitalpetri.modbus.server;

/**
 * A raw Modbus/TCP response PDU to write after MBAP framing.
 *
 * <p>The {@code pdu} is the complete response PDU. The TCP server writes these bytes as-is and
 * computes only the MBAP metadata around them.
 *
 * <p>This record intentionally keeps ownership simple: it does not validate {@code pdu} and does
 * not clone it. Callers that construct or receive a raw response must treat the PDU array as
 * explicitly owned by the local request handling path.
 *
 * @param pdu the complete response PDU.
 */
public record RawModbusTcpResponse(byte[] pdu) {}
