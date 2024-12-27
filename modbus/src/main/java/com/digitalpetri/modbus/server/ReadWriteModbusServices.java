package com.digitalpetri.modbus.server;

import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterResponse;
import com.digitalpetri.modbus.pdu.ReadWriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadWriteMultipleRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsResponse;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilResponse;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterResponse;

public abstract class ReadWriteModbusServices extends ReadOnlyModbusServices
    implements ModbusServices {

  @Override
  public WriteSingleCoilResponse writeSingleCoil(
      ModbusRequestContext context, int unitId, WriteSingleCoilRequest request)
      throws UnknownUnitIdException {

    ProcessImage processImage =
        getProcessImage(unitId).orElseThrow(() -> new UnknownUnitIdException(unitId));

    final int address = request.address();
    final int value = request.value();

    processImage.with(
        tx ->
            tx.writeCoils(
                coilMap -> {
                  if (value == 0) {
                    coilMap.remove(address);
                  } else {
                    coilMap.put(address, true);
                  }
                }));

    return new WriteSingleCoilResponse(address, value);
  }

  @Override
  public WriteMultipleCoilsResponse writeMultipleCoils(
      ModbusRequestContext context, int unitId, WriteMultipleCoilsRequest request)
      throws UnknownUnitIdException {

    ProcessImage processImage =
        getProcessImage(unitId).orElseThrow(() -> new UnknownUnitIdException(unitId));

    final int address = request.address();
    final int quantity = request.quantity();
    final byte[] values = request.values();

    processImage.with(
        tx ->
            tx.writeCoils(
                coilMap -> {
                  for (int i = 0; i < quantity; i++) {
                    int vi = i / 8;
                    int bi = i % 8;
                    boolean value = (values[vi] & (1 << bi)) != 0;
                    if (!value) {
                      coilMap.remove(address + i);
                    } else {
                      coilMap.put(address + i, true);
                    }
                  }
                }));

    return new WriteMultipleCoilsResponse(address, quantity);
  }

  @Override
  public WriteSingleRegisterResponse writeSingleRegister(
      ModbusRequestContext context, int unitId, WriteSingleRegisterRequest request)
      throws UnknownUnitIdException {

    ProcessImage processImage =
        getProcessImage(unitId).orElseThrow(() -> new UnknownUnitIdException(unitId));

    final int address = request.address();
    final int value = request.value();

    processImage.with(
        tx ->
            tx.writeHoldingRegisters(
                registerMap -> {
                  if (value == 0) {
                    registerMap.remove(address);
                  } else {
                    byte high = (byte) ((value >> 8) & 0xFF);
                    byte low = (byte) (value & 0xFF);
                    byte[] bs = new byte[] {high, low};
                    registerMap.put(address, bs);
                  }
                }));

    return new WriteSingleRegisterResponse(address, value);
  }

  @Override
  public WriteMultipleRegistersResponse writeMultipleRegisters(
      ModbusRequestContext context, int unitId, WriteMultipleRegistersRequest request)
      throws UnknownUnitIdException {

    ProcessImage processImage =
        getProcessImage(unitId).orElseThrow(() -> new UnknownUnitIdException(unitId));

    final int address = request.address();
    final int quantity = request.quantity();
    final byte[] values = request.values();

    processImage.with(
        tx ->
            tx.writeHoldingRegisters(
                registerMap -> {
                  for (int i = 0; i < quantity; i++) {
                    byte high = values[i * 2];
                    byte low = values[i * 2 + 1];

                    if (high == 0 && low == 0) {
                      registerMap.remove(address + i);
                    } else {
                      byte[] value = new byte[] {high, low};
                      registerMap.put(address + i, value);
                    }
                  }
                }));

    return new WriteMultipleRegistersResponse(address, quantity);
  }

  @Override
  public MaskWriteRegisterResponse maskWriteRegister(
      ModbusRequestContext context, int unitId, MaskWriteRegisterRequest request)
      throws UnknownUnitIdException {

    ProcessImage processImage =
        getProcessImage(unitId).orElseThrow(() -> new UnknownUnitIdException(unitId));

    // Result = (Current Contents AND And_Mask) OR (Or_Mask AND (NOT And_Mask))
    final int address = request.address();
    final int andMask = request.andMask();
    final int orMask = request.orMask();

    processImage.with(
        tx ->
            tx.writeHoldingRegisters(
                registerMap -> {
                  byte[] value = registerMap.getOrDefault(address, new byte[2]);
                  int currentValue = (value[0] << 8) | (value[1] & 0xFF);
                  int result = (currentValue & andMask) | (orMask & ~andMask);

                  if (result == 0) {
                    registerMap.remove(address);
                  } else {
                    byte high = (byte) ((result >> 8) & 0xFF);
                    byte low = (byte) (result & 0xFF);
                    byte[] bs = new byte[] {high, low};
                    registerMap.put(address, bs);
                  }
                }));

    return new MaskWriteRegisterResponse(address, andMask, orMask);
  }

  @Override
  public ReadWriteMultipleRegistersResponse readWriteMultipleRegisters(
      ModbusRequestContext context, int unitId, ReadWriteMultipleRegistersRequest request)
      throws UnknownUnitIdException {

    ProcessImage processImage =
        getProcessImage(unitId).orElseThrow(() -> new UnknownUnitIdException(unitId));

    final int readAddress = request.readAddress();
    final int readQuantity = request.readQuantity();
    final int writeAddress = request.writeAddress();
    final int writeQuantity = request.writeQuantity();
    final byte[] values = request.values();

    byte[] registers =
        processImage.get(
            tx -> {
              tx.writeHoldingRegisters(
                  registerMap -> {
                    for (int i = 0; i < writeQuantity; i++) {
                      byte high = values[i * 2];
                      byte low = values[i * 2 + 1];

                      if (high == 0 && low == 0) {
                        registerMap.remove(writeAddress + i);
                      } else {
                        byte[] value = new byte[] {high, low};
                        registerMap.put(writeAddress + i, value);
                      }
                    }
                  });

              return tx.readHoldingRegisters(readRegisters(readAddress, readQuantity));
            });

    return new ReadWriteMultipleRegistersResponse(registers);
  }
}
