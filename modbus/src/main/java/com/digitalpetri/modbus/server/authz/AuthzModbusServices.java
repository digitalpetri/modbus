package com.digitalpetri.modbus.server.authz;

import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.pdu.ReadCoilsResponse;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsResponse;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersResponse;
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
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpTlsRequestContext;

/**
 * A {@link ModbusServices} implementation that delegates to another
 * {@link ModbusServices} instance after performing authorization checks.
 */
public class AuthzModbusServices implements ModbusServices {

  private final AuthzHandler authzHandler;
  private final ModbusServices modbusServices;

  /**
   * Create a new {@link AuthzModbusServices} instance.
   * 
   * @param authzHandler   the {@link AuthzHandler} to use for authorization
   *                       checks.
   * @param modbusServices the {@link ModbusServices} to delegate to.
   */
  public AuthzModbusServices(AuthzHandler authzHandler, ModbusServices modbusServices) {
    this.authzHandler = authzHandler;
    this.modbusServices = modbusServices;
  }

  @Override
  public ReadCoilsResponse readCoils(ModbusRequestContext context, int unitId, ReadCoilsRequest request)
      throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof ModbusTcpTlsRequestContext ctx) {
      AuthzHandler.AuthzResult authResult = authzHandler.authorizeReadCoils(
          ctx.clientRole(),
          unitId,
          request.address(),
          request.quantity());

      if (authResult == AuthzHandler.AuthzResult.DENIED) {
        throw new ModbusResponseException(FunctionCode.READ_COILS, ExceptionCode.ILLEGAL_DATA_ADDRESS);
      }
    }

    return modbusServices.readCoils(context, unitId, request);
  }

  @Override
  public ReadDiscreteInputsResponse readDiscreteInputs(ModbusRequestContext context, int unitId,
      ReadDiscreteInputsRequest request)
      throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof ModbusTcpTlsRequestContext ctx) {
      AuthzHandler.AuthzResult authResult = authzHandler.authorizeReadDiscreteInputs(
          ctx.clientRole(),
          unitId,
          request.address(),
          request.quantity());

      if (authResult == AuthzHandler.AuthzResult.DENIED) {
        throw new ModbusResponseException(FunctionCode.READ_DISCRETE_INPUTS, ExceptionCode.ILLEGAL_DATA_ADDRESS);
      }
    }

    return modbusServices.readDiscreteInputs(context, unitId, request);
  }

  @Override
  public ReadHoldingRegistersResponse readHoldingRegisters(ModbusRequestContext context, int unitId,
      ReadHoldingRegistersRequest request) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof ModbusTcpTlsRequestContext ctx) {
      AuthzHandler.AuthzResult authResult = authzHandler.authorizeReadHoldingRegisters(
          ctx.clientRole(),
          unitId,
          request.address(),
          request.quantity());

      if (authResult == AuthzHandler.AuthzResult.DENIED) {
        throw new ModbusResponseException(FunctionCode.READ_HOLDING_REGISTERS, ExceptionCode.ILLEGAL_DATA_ADDRESS);
      }
    }

    return modbusServices.readHoldingRegisters(context, unitId, request);
  }

  @Override
  public ReadInputRegistersResponse readInputRegisters(ModbusRequestContext context, int unitId,
      ReadInputRegistersRequest request) throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof ModbusTcpTlsRequestContext ctx) {
      AuthzHandler.AuthzResult authResult = authzHandler.authorizeReadInputRegisters(
          ctx.clientRole(),
          unitId,
          request.address(),
          request.quantity());

      if (authResult == AuthzHandler.AuthzResult.DENIED) {
        throw new ModbusResponseException(FunctionCode.READ_INPUT_REGISTERS, ExceptionCode.ILLEGAL_DATA_ADDRESS);
      }
    }

    return modbusServices.readInputRegisters(context, unitId, request);
  }

  @Override
  public WriteSingleCoilResponse writeSingleCoil(ModbusRequestContext context, int unitId,
      WriteSingleCoilRequest request)
      throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof ModbusTcpTlsRequestContext ctx) {
      AuthzHandler.AuthzResult authResult = authzHandler.authorizeWriteSingleCoil(
          ctx.clientRole(),
          unitId,
          request.address());

      if (authResult == AuthzHandler.AuthzResult.DENIED) {
        throw new ModbusResponseException(FunctionCode.WRITE_SINGLE_COIL, ExceptionCode.ILLEGAL_DATA_ADDRESS);
      }
    }

    return modbusServices.writeSingleCoil(context, unitId, request);
  }

  @Override
  public WriteSingleRegisterResponse writeSingleRegister(ModbusRequestContext context, int unitId,
      WriteSingleRegisterRequest request)
      throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof ModbusTcpTlsRequestContext ctx) {
      AuthzHandler.AuthzResult authResult = authzHandler.authorizeWriteSingleRegister(
          ctx.clientRole(),
          unitId,
          request.address());

      if (authResult == AuthzHandler.AuthzResult.DENIED) {
        throw new ModbusResponseException(FunctionCode.WRITE_SINGLE_REGISTER, ExceptionCode.ILLEGAL_DATA_ADDRESS);
      }
    }

    return modbusServices.writeSingleRegister(context, unitId, request);
  }

  @Override
  public WriteMultipleCoilsResponse writeMultipleCoils(ModbusRequestContext context, int unitId,
      WriteMultipleCoilsRequest request)
      throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof ModbusTcpTlsRequestContext ctx) {
      AuthzHandler.AuthzResult authResult = authzHandler.authorizeWriteMultipleCoils(
          ctx.clientRole(),
          unitId,
          request.address());

      if (authResult == AuthzHandler.AuthzResult.DENIED) {
        throw new ModbusResponseException(FunctionCode.WRITE_MULTIPLE_COILS, ExceptionCode.ILLEGAL_DATA_ADDRESS);
      }
    }

    return modbusServices.writeMultipleCoils(context, unitId, request);
  }

  @Override
  public WriteMultipleRegistersResponse writeMultipleRegisters(ModbusRequestContext context, int unitId,
      WriteMultipleRegistersRequest request)
      throws ModbusResponseException, UnknownUnitIdException {

    if (context instanceof ModbusTcpTlsRequestContext ctx) {
      AuthzHandler.AuthzResult authResult = authzHandler.authorizeWriteMultipleRegisters(
          ctx.clientRole(),
          unitId,
          request.address());

      if (authResult == AuthzHandler.AuthzResult.DENIED) {
        throw new ModbusResponseException(FunctionCode.WRITE_MULTIPLE_REGISTERS, ExceptionCode.ILLEGAL_DATA_ADDRESS);
      }
    }

    return modbusServices.writeMultipleRegisters(context, unitId, request);
  }

}
