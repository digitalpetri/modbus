package com.digitalpetri.modbus.tcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBufUtil;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NettyRequestContextTest {

  @Test
  void shortFormRole() {
    assertEquals("Operator", decode("040a0c084f70657261746f72"));
  }

  @Test
  void utf8Role() {
    assertEquals(
        "Opérateur 操作员 😀", decode("041b0c194f70c3a972617465757220e6938de4bd9ce5919820f09f9880"));
  }

  @Test
  void longFormLengths() {
    assertRole("x".repeat(125), "047f0c7d");
    assertRole("x".repeat(126), "0481800c7e");
    assertRole("x".repeat(127), "0481810c7f");
    assertRole("x".repeat(128), "0481830c8180");
    assertRole("x".repeat(255), "048201020c81ff");
    assertRole("x".repeat(256), "048201040c820100");
    assertRole("操作员".repeat(15), "04818a0c8187");
  }

  @Test
  void absentAndEmptyRoles() {
    assertEquals("", NettyRequestContext.decodeClientRole(null));
    assertEquals("", decode("04020c00"));
  }

  @Test
  void malformedTagsAndLengths() {
    String[] malformed = {
      "",
      "04",
      "0400",
      "04010c",
      "05030c0141",
      "24030c0141",
      "0403040141",
      "04032c0141",
      "04040c0141",
      "04020c0141",
      "04030c0241",
      "04030c0041",
      "0480",
      "04020c80",
      "0481030c0141",
      "04040c810141",
      "048200030c0141",
      "04050c82000141",
      "0482ff",
      "04030c82ff",
      "0484ffffffff0c0141",
      "04060c84ffffffff",
      "048500000000030c0141",
      "04070c85000000000141"
    };

    for (String value : malformed) {
      assertEquals("", decode(value), value);
    }
  }

  @Test
  void trailingData() {
    assertEquals("", decode("04030c014100"));
    assertEquals("", decode("04040c014100"));
    assertEquals("", decode("04060c01410c0142"));
  }

  @Test
  void malformedUtf8() {
    String[] malformed = {
      "04030c0180",
      "04030c01c2",
      "04040c02c328",
      "04040c02c080",
      "04050c03eda080",
      "04060c04f4908080",
      "04060c04f5808080"
    };

    for (String value : malformed) {
      assertEquals("", decode(value), value);
    }
  }

  private static String decode(String hex) {
    return NettyRequestContext.decodeClientRole(ByteBufUtil.decodeHexDump(hex));
  }

  private static void assertRole(String role, String header) {
    assertEquals(role, decode(header + ByteBufUtil.hexDump(role.getBytes(StandardCharsets.UTF_8))));
  }
}
