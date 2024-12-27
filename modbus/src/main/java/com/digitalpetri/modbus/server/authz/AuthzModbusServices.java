package com.digitalpetri.modbus.server.authz;

import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterResponse;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.pdu.ReadCoilsResponse;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsResponse;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersResponse;
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
import com.digitalpetri.modbus.server.ModbusRequestContext;
import com.digitalpetri.modbus.server.ModbusServices;
import com.digitalpetri.modbus.server.authz.AuthzHandler.AuthzResult;

/**
 * A {@link ModbusServices} implementation that delegates to another {@link ModbusServices} instance
 * after performing authorization checks.
 */
public class AuthzModbusServices implements ModbusServices {

  private final AuthzHandler authzHandler;
  private final ModbusServices modbusServices;

  /**
   * Create a new {@link AuthzModbusServices} instance.
   *
   * @param authzHandler the {@link AuthzHandler} to use for authorization checks.
   * @param modbusServices the {@link ModbusServices} to delegate to.
   */
  public AuthzModbusServices(AuthzHandler authzHandler, ModbusServices modbusServices) {
    this.authzHandler = authzHandler;
    this.modbusServices = modbusServices;
  }

  @Override
  public ReadCoilsResponse readCoils(
      ModbusRequestContext context,
      int unitId,
      ReadCoilsRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof AuthzContext ctx) {
      AuthzResult result = authzHandler.authorizeReadCoils(ctx, unitId, request);

      if (result != AuthzResult.AUTHORIZED) {
        throw new ModbusResponseException(FunctionCode.READ_COILS,
            ExceptionCode.ILLEGAL_FUNCTION);
      }
    }

    return modbusServices.readCoils(context, unitId, request);
  }

  @Override
  public ReadDiscreteInputsResponse readDiscreteInputs(
      ModbusRequestContext context,
      int unitId,
      ReadDiscreteInputsRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof AuthzContext ctx) {
      AuthzResult result = authzHandler.authorizeReadDiscreteInputs(ctx, unitId, request);

      if (result != AuthzResult.AUTHORIZED) {
        throw new ModbusResponseException(FunctionCode.READ_DISCRETE_INPUTS,
            ExceptionCode.ILLEGAL_FUNCTION);
      }
    }

    return modbusServices.readDiscreteInputs(context, unitId, request);
  }

  @Override
  public ReadHoldingRegistersResponse readHoldingRegisters(
      ModbusRequestContext context,
      int unitId,
      ReadHoldingRegistersRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof AuthzContext ctx) {
      AuthzResult result = authzHandler.authorizeReadHoldingRegisters(ctx, unitId, request);

      if (result != AuthzResult.AUTHORIZED) {
        throw new ModbusResponseException(FunctionCode.READ_HOLDING_REGISTERS,
            ExceptionCode.ILLEGAL_FUNCTION);
      }
    }

    return modbusServices.readHoldingRegisters(context, unitId, request);
  }

  @Override
  public ReadInputRegistersResponse readInputRegisters(
      ModbusRequestContext context,
      int unitId,
      ReadInputRegistersRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof AuthzContext ctx) {
      AuthzResult result = authzHandler.authorizeReadInputRegisters(ctx, unitId, request);

      if (result != AuthzResult.AUTHORIZED) {
        throw new ModbusResponseException(FunctionCode.READ_INPUT_REGISTERS,
            ExceptionCode.ILLEGAL_FUNCTION);
      }
    }

    return modbusServices.readInputRegisters(context, unitId, request);
  }

  @Override
  public WriteSingleCoilResponse writeSingleCoil(
      ModbusRequestContext context,
      int unitId,
      WriteSingleCoilRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof AuthzContext ctx) {
      AuthzResult result = authzHandler.authorizeWriteSingleCoil(ctx, unitId, request);

      if (result != AuthzResult.AUTHORIZED) {
        throw new ModbusResponseException(FunctionCode.WRITE_SINGLE_COIL,
            ExceptionCode.ILLEGAL_FUNCTION);
      }
    }

    return modbusServices.writeSingleCoil(context, unitId, request);
  }

  @Override
  public WriteSingleRegisterResponse writeSingleRegister(
      ModbusRequestContext context,
      int unitId,
      WriteSingleRegisterRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof AuthzContext ctx) {
      AuthzResult result = authzHandler.authorizeWriteSingleRegister(ctx, unitId, request);

      if (result != AuthzResult.AUTHORIZED) {
        throw new ModbusResponseException(FunctionCode.WRITE_SINGLE_REGISTER,
            ExceptionCode.ILLEGAL_FUNCTION);
      }
    }

    return modbusServices.writeSingleRegister(context, unitId, request);
  }

  @Override
  public WriteMultipleCoilsResponse writeMultipleCoils(
      ModbusRequestContext context,
      int unitId,
      WriteMultipleCoilsRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof AuthzContext ctx) {
      AuthzResult result = authzHandler.authorizeWriteMultipleCoils(ctx, unitId, request);

      if (result != AuthzResult.AUTHORIZED) {
        throw new ModbusResponseException(FunctionCode.WRITE_MULTIPLE_COILS,
            ExceptionCode.ILLEGAL_FUNCTION);
      }
    }

    return modbusServices.writeMultipleCoils(context, unitId, request);
  }

  @Override
  public WriteMultipleRegistersResponse writeMultipleRegisters(
      ModbusRequestContext context,
      int unitId,
      WriteMultipleRegistersRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof AuthzContext ctx) {
      AuthzResult result = authzHandler.authorizeWriteMultipleRegisters(ctx, unitId, request);

      if (result != AuthzResult.AUTHORIZED) {
        throw new ModbusResponseException(FunctionCode.WRITE_MULTIPLE_REGISTERS,
            ExceptionCode.ILLEGAL_FUNCTION);
      }
    }

    return modbusServices.writeMultipleRegisters(context, unitId, request);
  }

  @Override
  public MaskWriteRegisterResponse maskWriteRegister(
      ModbusRequestContext context,
      int unitId,
      MaskWriteRegisterRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof AuthzContext ctx) {
      AuthzResult result = authzHandler.authorizeMaskWriteRegister(ctx, unitId, request);

      if (result != AuthzResult.AUTHORIZED) {
        throw new ModbusResponseException(FunctionCode.MASK_WRITE_REGISTER,
            ExceptionCode.ILLEGAL_FUNCTION);
      }
    }

    return modbusServices.maskWriteRegister(context, unitId, request);
  }

  @Override
  public ReadWriteMultipleRegistersResponse readWriteMultipleRegisters(
      ModbusRequestContext context,
      int unitId,
      ReadWriteMultipleRegistersRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof AuthzContext ctx) {
      AuthzResult result = authzHandler.authorizeReadWriteMultipleRegisters(ctx, unitId, request);

      if (result != AuthzResult.AUTHORIZED) {
        throw new ModbusResponseException(FunctionCode.READ_WRITE_MULTIPLE_REGISTERS,
            ExceptionCode.ILLEGAL_FUNCTION);
      }
    }

    return modbusServices.readWriteMultipleRegisters(context, unitId, request);
  }

}
