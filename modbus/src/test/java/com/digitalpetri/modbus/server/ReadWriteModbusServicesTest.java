package com.digitalpetri.modbus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.digitalpetri.modbus.pdu.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.pdu.ReadWriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadWriteMultipleRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.server.ReadOnlyModbusServicesTest.TestModbusRequestContext;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class ReadWriteModbusServicesTest {

  private final Random random = new Random();

  private final ProcessImage processImage = new ProcessImage();

  private final ReadWriteModbusServices services = new ReadWriteModbusServices() {
    @Override
    protected Optional<ProcessImage> getProcessImage(int unitId) {
      return Optional.of(processImage);
    }
  };

  @Test
  void writeSingleCoil() throws Exception {
    boolean[] values = new boolean[65536];
    for (int i = 0; i < 65536; i++) {
      values[i] = random.nextBoolean();
      services.writeSingleCoil(
          new TestModbusRequestContext(),
          0,
          new WriteSingleCoilRequest(i, values[i])
      );
    }
    processImage.with(tx -> tx.readCoils(coilMap -> {
      for (int i = 0; i < 65536; i++) {
        assertEquals(values[i], coilMap.getOrDefault(i, false));
      }
      return null;
    }));
  }

  @Test
  void writeMultipleCoils() throws Exception {
    boolean[] values = new boolean[65536];
    for (int i = 0; i < 65536; i++) {
      values[i] = random.nextBoolean();
    }

    int address = 0;
    int remaining = 65536;
    int quantity = Math.min(remaining - address, random.nextInt(0x7B0) + 1);

    while (remaining > 0) {
      var coils = new byte[(quantity + 7) / 8];
      for (int i = 0; i < quantity; i++) {
        int ci = i / 8;
        int bi = i % 8;
        if (values[address + i]) {
          coils[ci] |= (byte) (1 << bi);
        }
      }

      services.writeMultipleCoils(
          new TestModbusRequestContext(),
          0,
          new WriteMultipleCoilsRequest(address, quantity, coils)
      );

      address += quantity;
      remaining -= quantity;
      quantity = Math.min(remaining, random.nextInt(0x7B0) + 1);
    }

    processImage.with(tx -> tx.readCoils(coilMap -> {
      for (int i = 0; i < 65536; i++) {
        assertEquals(values[i], coilMap.getOrDefault(i, false));
      }
      return null;
    }));
  }

  @Test
  void writeSingleRegister() throws Exception {
    var randomBytes = new byte[65536 * 2];
    random.nextBytes(randomBytes);

    for (int i = 0; i < 65536; i++) {
      int value = (randomBytes[i * 2] & 0xFF) << 8 | (randomBytes[i * 2 + 1] & 0xFF);
      services.writeSingleRegister(
          new TestModbusRequestContext(),
          0,
          new WriteSingleRegisterRequest(i, value)
      );
    }

    processImage.with(tx -> tx.readHoldingRegisters(holdingRegisterMap -> {
      for (int i = 0; i < 65536; i++) {
        byte[] registerBytes = holdingRegisterMap.getOrDefault(i, new byte[2]);
        int registerValue = (registerBytes[0] & 0xFF) << 8 | (registerBytes[1] & 0xFF);
        int expectedValue = (randomBytes[i * 2] & 0xFF) << 8 | (randomBytes[i * 2 + 1] & 0xFF);
        assertEquals(expectedValue, registerValue);
      }
      return null;
    }));
  }

  @Test
  void writeMultipleRegisters() throws Exception {
    var randomBytes = new byte[65536 * 2];
    random.nextBytes(randomBytes);

    int address = 0;
    int remaining = 65536;
    int quantity = Math.min(remaining - address, random.nextInt(123) + 1);

    while (remaining > 0) {
      var registers = new byte[quantity * 2];

      int baseOffsetIntoRandom = address * 2;
      for (int i = 0; i < quantity; i++) {
        registers[i * 2] = randomBytes[baseOffsetIntoRandom + i * 2];
        registers[i * 2 + 1] = randomBytes[baseOffsetIntoRandom + i * 2 + 1];
      }

      services.writeMultipleRegisters(
          new TestModbusRequestContext(),
          0,
          new WriteMultipleRegistersRequest(address, quantity, registers)
      );

      address += quantity;
      remaining -= quantity;
      quantity = Math.min(remaining, random.nextInt(123) + 1);
    }

    processImage.with(tx -> tx.readHoldingRegisters(holdingRegisterMap -> {
      for (int i = 0; i < 65536; i++) {
        byte[] registerBytes = holdingRegisterMap.getOrDefault(i, new byte[2]);
        byte b0 = registerBytes[0];
        byte b1 = registerBytes[1];
        byte r0 = randomBytes[i * 2];
        byte r1 = randomBytes[i * 2 + 1];
        assertEquals(r0, b0);
        assertEquals(r1, b1);
      }
      return null;
    }));
  }

  @Test
  void maskWriteRegister() throws Exception {
    var randomBytes = new byte[65536 * 2];
    random.nextBytes(randomBytes);

    processImage.with(tx -> tx.writeHoldingRegisters(registerMap -> {
      for (int i = 0; i < 65536; i++) {
        var bs = new byte[2];
        bs[0] = randomBytes[i * 2];
        bs[1] = randomBytes[i * 2 + 1];
        registerMap.put(i, bs);
      }
    }));

    short[] expectedValues = new short[65536];

    for (int i = 0; i < 65536; i++) {
      int andMask = random.nextInt(0xFFFF + 1);
      int orMask = random.nextInt(0xFFFF + 1);
      services.maskWriteRegister(
          new TestModbusRequestContext(),
          0,
          new MaskWriteRegisterRequest(i, andMask, orMask)
      );
      int current = (randomBytes[i * 2] & 0xFF) << 8 | randomBytes[i * 2 + 1] & 0xFF;
      int expected = (current & andMask) | (orMask & ~andMask);
      expectedValues[i] = (short) expected;
    }

    processImage.with(tx -> tx.readHoldingRegisters(registerMap -> {
      for (int i = 0; i < 65536; i++) {
        byte[] registerBytes = registerMap.getOrDefault(i, new byte[2]);
        short registerValue = (short) ((registerBytes[0] & 0xFF) << 8 | (registerBytes[1] & 0xFF));
        assertEquals(expectedValues[i], registerValue);
      }
      return null;
    }));
  }

  @Test
  void readWriteMultipleRegisters() throws Exception {
    var randomBytes = new byte[65536 * 2];
    random.nextBytes(randomBytes);

    int address = 0;
    int remaining = 65536;
    int quantity = Math.min(remaining - address, random.nextInt(0x79) + 1);

    while (remaining > 0) {
      var values = new byte[quantity * 2];

      ReadWriteMultipleRegistersResponse response = services.readWriteMultipleRegisters(
          new TestModbusRequestContext(),
          0,
          new ReadWriteMultipleRegistersRequest(address, quantity, address, quantity, values)
      );

      byte[] registers = response.registers();
      for (byte register : registers) {
        assertEquals(0, register);
      }

      address += quantity;
      remaining -= quantity;
      quantity = Math.min(remaining, random.nextInt(123) + 1);
    }

    // the above wrote zero to all the randomly initialized registers
    processImage.with(tx -> tx.readHoldingRegisters(holdingRegisterMap -> {
      for (int i = 0; i < 65536; i++) {
        byte[] registerBytes = holdingRegisterMap.getOrDefault(i, new byte[2]);
        byte b0 = registerBytes[0];
        byte b1 = registerBytes[1];

        assertEquals(0, b0);
        assertEquals(0, b1);
      }
      return null;
    }));
  }

}
