package com.digitalpetri.modbus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.pdu.ReadCoilsResponse;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsResponse;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersResponse;
import com.digitalpetri.modbus.server.ModbusTcpServerTransport.ModbusTcpRequestContext;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class ReadOnlyModbusServicesTest {

  private final Random random = new Random();

  private final ProcessImage processImage = new ProcessImage();

  private final ReadOnlyModbusServices services = new ReadOnlyModbusServices() {
    @Override
    protected Optional<ProcessImage> getProcessImage(int unitId) {
      return Optional.of(processImage);
    }
  };

  @Test
  void readCoils() throws Exception {
    var randomBooleans = new boolean[65536];
    for (int i = 0; i < 65536; i++) {
      randomBooleans[i] = random.nextBoolean();
    }

    processImage.with(tx -> tx.writeCoils(coilMap -> {
      for (int i = 0; i < 65536; i++) {
        coilMap.put(i, randomBooleans[i]);
      }
    }));

    int address = 0;
    int remaining = 65536;
    int quantity = Math.min(remaining - address, random.nextInt(2000) + 1);

    while (remaining > 0) {
      ReadCoilsResponse response = services.readCoils(
          new TestModbusRequestContext(),
          0,
          new ReadCoilsRequest(address, quantity)
      );

      byte[] inputs = response.coils();

      for (int i = 0; i < quantity; i++) {
        byte b = inputs[i / 8];
        int bit = i % 8;
        int value = (b >> bit) & 0x01;
        assertEquals(randomBooleans[address + i] ? 1 : 0, value);
      }

      address += quantity;
      remaining -= quantity;
      quantity = Math.min(remaining, random.nextInt(2000) + 1);
    }
  }

  @Test
  void readDiscreteInputs() throws Exception {
    var randomBooleans = new boolean[65536];
    for (int i = 0; i < 65536; i++) {
      randomBooleans[i] = random.nextBoolean();
    }

    processImage.with(tx -> tx.writeDiscreteInputs(discreteInputMap -> {
      for (int i = 0; i < 65536; i++) {
        discreteInputMap.put(i, randomBooleans[i]);
      }
    }));

    int address = 0;
    int remaining = 65536;
    int quantity = Math.min(remaining - address, random.nextInt(2000) + 1);

    while (remaining > 0) {
      ReadDiscreteInputsResponse response = services.readDiscreteInputs(
          new TestModbusRequestContext(),
          0,
          new ReadDiscreteInputsRequest(address, quantity)
      );

      byte[] inputs = response.inputs();

      for (int i = 0; i < quantity; i++) {
        byte b = inputs[i / 8];
        int bit = i % 8;
        int value = (b >> bit) & 0x01;
        assertEquals(randomBooleans[address + i] ? 1 : 0, value);
      }

      address += quantity;
      remaining -= quantity;
      quantity = Math.min(remaining, random.nextInt(2000) + 1);
    }
  }

  @Test
  void readHoldingRegisters() throws Exception {
    var random = new Random();
    var randomBytes = new byte[65536 * 2];
    random.nextBytes(randomBytes);

    // Fill the Holding Registers with random data
    processImage.with(tx -> tx.writeHoldingRegisters(holdingRegisterMap -> {
      for (int i = 0; i < 65536; i++) {
        var bs = new byte[2];
        bs[0] = randomBytes[i * 2];
        bs[1] = randomBytes[i * 2 + 1];
        holdingRegisterMap.put(i, bs);
      }
    }));

    // Read random lengths until all registers are read and verified
    int address = 0;
    int remaining = 65536;
    int quantity = Math.min(remaining - address, random.nextInt(125) + 1);

    while (remaining > 0) {
      ReadHoldingRegistersResponse response = services.readHoldingRegisters(
          new TestModbusRequestContext(),
          0,
          new ReadHoldingRegistersRequest(address, quantity)
      );

      byte[] registers = response.registers();

      int baseOffsetIntoRandom = address * 2;
      for (int i = 0; i < quantity; i++) {
        byte b0 = registers[i * 2];
        byte b1 = registers[i * 2 + 1];
        byte r0 = randomBytes[baseOffsetIntoRandom + i * 2];
        byte r1 = randomBytes[baseOffsetIntoRandom + i * 2 + 1];
        assertEquals(r0, b0);
        assertEquals(r1, b1);
      }

      address += quantity;
      remaining -= quantity;
      quantity = Math.min(remaining, random.nextInt(125) + 1);
    }
  }

  @Test
  void readInputRegisters() throws Exception {
    var random = new Random();
    var randomBytes = new byte[65536 * 2];
    random.nextBytes(randomBytes);

    // Fill the Holding Registers with random data
    processImage.with(tx -> tx.writeInputRegisters(inputRegisterMap -> {
      for (int i = 0; i < 65536; i++) {
        var bs = new byte[2];
        bs[0] = randomBytes[i * 2];
        bs[1] = randomBytes[i * 2 + 1];
        inputRegisterMap.put(i, bs);
      }
    }));

    // Read random lengths until all registers are read and verified
    int address = 0;
    int remaining = 65536;
    int quantity = Math.min(remaining - address, random.nextInt(125) + 1);

    while (remaining > 0) {
      ReadInputRegistersResponse response = services.readInputRegisters(
          new TestModbusRequestContext(),
          0,
          new ReadInputRegistersRequest(address, quantity)
      );

      byte[] registers = response.registers();

      int baseOffsetIntoRandom = address * 2;
      for (int i = 0; i < quantity; i++) {
        byte b0 = registers[i * 2];
        byte b1 = registers[i * 2 + 1];
        byte r0 = randomBytes[baseOffsetIntoRandom + i * 2];
        byte r1 = randomBytes[baseOffsetIntoRandom + i * 2 + 1];
        assertEquals(r0, b0);
        assertEquals(r1, b1);
      }

      address += quantity;
      remaining -= quantity;
      quantity = Math.min(remaining, random.nextInt(125) + 1);
    }
  }

  static class TestModbusRequestContext implements ModbusTcpRequestContext {

    @Override
    public SocketAddress localAddress() {
      return null;
    }

    @Override
    public SocketAddress remoteAddress() {
      return null;
    }
  }

}
