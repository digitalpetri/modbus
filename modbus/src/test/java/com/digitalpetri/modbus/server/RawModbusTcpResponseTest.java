package com.digitalpetri.modbus.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class RawModbusTcpResponseTest {

  @Test
  void responseBuildsFunctionCodeAndPayload() {
    RawModbusTcpResponse response = RawModbusTcpResponse.response(0x5A, new byte[] {0x01, 0x02});

    assertArrayEquals(new byte[] {0x5A, 0x01, 0x02}, response.pdu());
  }

  @Test
  void modbusExceptionBuildsStandardExceptionPdu() {
    RawModbusTcpResponse response = RawModbusTcpResponse.modbusException(0x5A, 0x01);

    assertArrayEquals(new byte[] {(byte) 0xDA, 0x01}, response.pdu());
  }
}
