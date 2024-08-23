package com.digitalpetri.modbus.server;

import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.pdu.ReadCoilsResponse;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsResponse;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersResponse;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public abstract class ReadOnlyModbusServices implements ModbusServices {

  protected abstract Optional<ProcessImage> getProcessImage(int unitId);

  @Override
  public ReadCoilsResponse readCoils(
      ModbusRequestContext context,
      int unitId,
      ReadCoilsRequest request
  ) throws UnknownUnitIdException {

    ProcessImage processImage = getProcessImage(unitId)
        .orElseThrow(() -> new UnknownUnitIdException(unitId));

    final int address = request.address();
    final int quantity = request.quantity();

    byte[] coils = processImage.get(
        tx ->
            tx.readCoils(readBits(address, quantity))
    );

    return new ReadCoilsResponse(coils);
  }

  @Override
  public ReadDiscreteInputsResponse readDiscreteInputs(
      ModbusRequestContext context,
      int unitId,
      ReadDiscreteInputsRequest request
  ) throws UnknownUnitIdException {

    ProcessImage processImage = getProcessImage(unitId)
        .orElseThrow(() -> new UnknownUnitIdException(unitId));

    final int address = request.address();
    final int quantity = request.quantity();

    byte[] inputs = processImage.get(
        tx ->
            tx.readDiscreteInputs(readBits(address, quantity))
    );

    return new ReadDiscreteInputsResponse(inputs);
  }

  @Override
  public ReadHoldingRegistersResponse readHoldingRegisters(
      ModbusRequestContext context,
      int unitId,
      ReadHoldingRegistersRequest request
  ) throws UnknownUnitIdException {

    ProcessImage processImage = getProcessImage(unitId)
        .orElseThrow(() -> new UnknownUnitIdException(unitId));

    final int address = request.address();
    final int quantity = request.quantity();

    byte[] registers = processImage.get(
        tx ->
            tx.readHoldingRegisters(readRegisters(address, quantity))
    );

    return new ReadHoldingRegistersResponse(registers);
  }

  @Override
  public ReadInputRegistersResponse readInputRegisters(
      ModbusRequestContext context,
      int unitId,
      ReadInputRegistersRequest request
  ) throws UnknownUnitIdException {

    ProcessImage processImage = getProcessImage(unitId)
        .orElseThrow(() -> new UnknownUnitIdException(unitId));

    final int address = request.address();
    final int quantity = request.quantity();

    byte[] registers = processImage.get(
        tx ->
            tx.readInputRegisters(readRegisters(address, quantity))
    );

    return new ReadInputRegistersResponse(registers);
  }

  private static Function<Map<Integer, Boolean>, byte[]> readBits(int address, int quantity) {
    final var bytes = new byte[(quantity + 7) / 8];

    return bitMap -> {
      for (int i = 0; i < quantity; i++) {
        int bitIndex = i % 8;
        int byteIndex = i / 8;

        boolean value = bitMap.getOrDefault(address + i, false);

        int b = bytes[byteIndex];
        if (value) {
          b |= (1 << bitIndex);
        } else {
          b &= ~(1 << bitIndex);
        }
        bytes[byteIndex] = (byte) (b & 0xFF);
      }

      return bytes;
    };
  }

  private static Function<Map<Integer, byte[]>, byte[]> readRegisters(int address, int quantity) {
    final var registers = new byte[quantity * 2];

    return registerMap -> {
      for (int i = 0; i < quantity; i++) {
        byte[] value = registerMap.getOrDefault(address + i, new byte[2]);

        registers[i * 2] = value[0];
        registers[i * 2 + 1] = value[1];
      }

      return registers;
    };
  }

}
