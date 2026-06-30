package com.digitalpetri.modbus.server;

/**
 * A raw Modbus/TCP request PDU after the function code has been separated from the payload.
 *
 * <p>This record intentionally keeps ownership simple: it does not validate field ranges and does
 * not clone {@code payload}. Callers that construct or receive a raw request must treat the payload
 * array as explicitly owned by the local request handling path.
 *
 * @param unitId the unit id copied from the MBAP header.
 * @param functionCode the first byte of the request PDU, represented as an unsigned integer.
 * @param payload the request PDU bytes following the function code.
 */
public record RawModbusTcpRequest(int unitId, int functionCode, byte[] payload) {}
