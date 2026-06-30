package com.digitalpetri.modbus.server;

/**
 * A raw Modbus/TCP request PDU.
 *
 * <p>This record intentionally keeps ownership simple: it does not validate field ranges and does
 * not clone {@code pdu}. Callers that construct or receive a raw request must treat the PDU array
 * as explicitly owned by the local request handling path.
 *
 * @param unitId the unit id copied from the MBAP header.
 * @param pdu the complete request PDU bytes.
 */
public record RawModbusTcpRequest(int unitId, byte[] pdu) {}
