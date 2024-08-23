package com.digitalpetri.modbus.server;

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
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsResponse;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilResponse;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterResponse;

public interface ModbusServices {

  /**
   * Handle an incoming {@link ReadCoilsRequest} targeting {@code unitId}.
   *
   * @param context the {@link ModbusRequestContext} for the request.
   * @param unitId the unit id being targeted.
   * @param request the {@link ReadCoilsRequest} to handle.
   * @return a {@link ReadCoilsResponse}.
   * @throws ModbusResponseException if there is an error handling the request that can be
   *     reported by an {@link ExceptionCode}.
   * @throws UnknownUnitIdException if the unit id is not known to this server.
   */
  default ReadCoilsResponse readCoils(
      ModbusRequestContext context,
      int unitId,
      ReadCoilsRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    throw new ModbusResponseException(FunctionCode.READ_COILS, ExceptionCode.ILLEGAL_FUNCTION);
  }

  /**
   * Handle an incoming {@link ReadDiscreteInputsRequest} targeting {@code unitId}.
   *
   * @param context the {@link ModbusRequestContext} for the request.
   * @param unitId the unit id being targeted.
   * @param request the {@link ReadDiscreteInputsRequest} to handle.
   * @return a {@link ReadDiscreteInputsResponse}.
   * @throws ModbusResponseException if there is an error handling the request that can be
   *     reported by an {@link ExceptionCode}.
   * @throws UnknownUnitIdException if the unit id is not known to this server.
   */
  default ReadDiscreteInputsResponse readDiscreteInputs(
      ModbusRequestContext context,
      int unitId,
      ReadDiscreteInputsRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    throw new ModbusResponseException(FunctionCode.READ_DISCRETE_INPUTS,
        ExceptionCode.ILLEGAL_FUNCTION);
  }

  /**
   * Handle an incoming {@link ReadHoldingRegistersRequest} targeting {@code unitId}.
   *
   * @param context the {@link ModbusRequestContext} for the request.
   * @param unitId the unit id being targeted.
   * @param request the {@link ReadHoldingRegistersRequest} to handle.
   * @return a {@link ReadHoldingRegistersResponse}.
   * @throws ModbusResponseException if there is an error handling the request that can be
   *     reported by an {@link ExceptionCode}.
   * @throws UnknownUnitIdException if the unit id is not known to this server.
   */
  default ReadHoldingRegistersResponse readHoldingRegisters(
      ModbusRequestContext context,
      int unitId,
      ReadHoldingRegistersRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    throw new ModbusResponseException(FunctionCode.READ_HOLDING_REGISTERS,
        ExceptionCode.ILLEGAL_FUNCTION);
  }

  /**
   * Handle an incoming {@link ReadInputRegistersRequest} targeting {@code unitId}.
   *
   * @param context the {@link ModbusRequestContext} for the request.
   * @param unitId the unit id being targeted.
   * @param request the {@link ReadInputRegistersRequest} to handle.
   * @return a {@link ReadInputRegistersResponse}.
   * @throws ModbusResponseException if there is an error handling the request that can be
   *     reported by an {@link ExceptionCode}.
   * @throws UnknownUnitIdException if the unit id is not known to this server.
   */
  default ReadInputRegistersResponse readInputRegisters(
      ModbusRequestContext context,
      int unitId,
      ReadInputRegistersRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    throw new ModbusResponseException(FunctionCode.READ_INPUT_REGISTERS,
        ExceptionCode.ILLEGAL_FUNCTION);
  }

  /**
   * Handle an incoming {@link WriteSingleCoilRequest} targeting {@code unitId}.
   *
   * @param context the {@link ModbusRequestContext} for the request.
   * @param unitId the unit id being targeted.
   * @param request the {@link WriteSingleCoilRequest} to handle.
   * @return a {@link WriteSingleCoilResponse}.
   * @throws ModbusResponseException if there is an error handling the request that can be
   *     reported by an {@link ExceptionCode}.
   * @throws UnknownUnitIdException if the unit id is not known to this server.
   */
  default WriteSingleCoilResponse writeSingleCoil(
      ModbusRequestContext context,
      int unitId,
      WriteSingleCoilRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    throw new ModbusResponseException(FunctionCode.WRITE_SINGLE_COIL,
        ExceptionCode.ILLEGAL_FUNCTION);
  }

  /**
   * Handle an incoming {@link WriteSingleRegisterRequest} targeting {@code unitId}.
   *
   * @param context the {@link ModbusRequestContext} for the request.
   * @param unitId the unit id being targeted.
   * @param request the {@link WriteSingleRegisterRequest} to handle.
   * @return a {@link WriteSingleRegisterResponse}.
   * @throws ModbusResponseException if there is an error handling the request that can be
   *     reported by an {@link ExceptionCode}.
   * @throws UnknownUnitIdException if the unit id is not known to this server.
   */
  default WriteSingleRegisterResponse writeSingleRegister(
      ModbusRequestContext context,
      int unitId,
      WriteSingleRegisterRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    throw new ModbusResponseException(FunctionCode.WRITE_SINGLE_REGISTER,
        ExceptionCode.ILLEGAL_FUNCTION);
  }

  /**
   * Handle an incoming {@link WriteMultipleCoilsRequest} targeting {@code unitId}.
   *
   * @param context the {@link ModbusRequestContext} for the request.
   * @param unitId the unit id being targeted.
   * @param request the {@link WriteMultipleCoilsRequest} to handle.
   * @return a {@link WriteMultipleCoilsResponse}.
   * @throws ModbusResponseException if there is an error handling the request that can be
   *     reported by an {@link ExceptionCode}.
   * @throws UnknownUnitIdException if the unit id is not known to this server.
   */
  default WriteMultipleCoilsResponse writeMultipleCoils(
      ModbusRequestContext context,
      int unitId,
      WriteMultipleCoilsRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    throw new ModbusResponseException(FunctionCode.WRITE_MULTIPLE_COILS,
        ExceptionCode.ILLEGAL_FUNCTION);
  }

  /**
   * Handle an incoming {@link WriteMultipleRegistersRequest} targeting {@code unitId}.
   *
   * @param context the {@link ModbusRequestContext} for the request.
   * @param unitId the unit id being targeted.
   * @param request the {@link WriteMultipleRegistersRequest} to handle.
   * @return a {@link WriteMultipleRegistersResponse}.
   * @throws ModbusResponseException if there is an error handling the request that can be
   *     reported by an {@link ExceptionCode}.
   * @throws UnknownUnitIdException if the unit id is not known to this server.
   */
  default WriteMultipleRegistersResponse writeMultipleRegisters(
      ModbusRequestContext context,
      int unitId,
      WriteMultipleRegistersRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    throw new ModbusResponseException(FunctionCode.WRITE_MULTIPLE_REGISTERS,
        ExceptionCode.ILLEGAL_FUNCTION);
  }

  /**
   * Handle an incoming {@link MaskWriteRegisterRequest} targeting {@code unitId}.
   *
   * @param context the {@link ModbusRequestContext} for the request.
   * @param unitId the unit id being targeted.
   * @param request the {@link MaskWriteRegisterRequest} to handle.
   * @return a {@link MaskWriteRegisterResponse}.
   * @throws ModbusResponseException if there is an error handling the request that can be
   *     reported by an {@link ExceptionCode}.
   * @throws UnknownUnitIdException if the unit id is not known to this server.
   */
  default MaskWriteRegisterResponse maskWriteRegister(
      ModbusRequestContext context,
      int unitId,
      MaskWriteRegisterRequest request
  ) throws ModbusResponseException, UnknownUnitIdException {

    throw new ModbusResponseException(FunctionCode.MASK_WRITE_REGISTER,
        ExceptionCode.ILLEGAL_FUNCTION);
  }

  /**
   * Check that the combination of address and quantity are valid.
   *
   * @param functionCode the function code of the request.
   * @param address the starting address.
   * @param quantity the quantity of registers.
   * @throws ModbusResponseException if the combination of address and quantity are invalid.
   */
  static void checkRegisterRange(int functionCode, int address, int quantity)
      throws ModbusResponseException {

    if (address < 0 || address > 0xFFFF) {
      throw new ModbusResponseException(functionCode, ExceptionCode.ILLEGAL_DATA_ADDRESS.getCode());
    }
    if (quantity < 1 || quantity > 0x7D) {
      throw new ModbusResponseException(functionCode, ExceptionCode.ILLEGAL_DATA_VALUE.getCode());
    }
    if (address + quantity > 65536) {
      throw new ModbusResponseException(functionCode, ExceptionCode.ILLEGAL_DATA_ADDRESS.getCode());
    }
  }

  /**
   * Check that the combination of address and quantity are valid.
   *
   * @param functionCode the function code of the request.
   * @param address the starting address.
   * @param quantity the quantity of bits.
   * @throws ModbusResponseException if the combination of address and quantity are invalid.
   */
  static void checkBitRange(int functionCode, int address, int quantity)
      throws ModbusResponseException {

    if (address < 0 || address > 0xFFFF) {
      throw new ModbusResponseException(functionCode, ExceptionCode.ILLEGAL_DATA_ADDRESS.getCode());
    }
    if (quantity < 1 || quantity > 0x7D0) {
      throw new ModbusResponseException(functionCode, ExceptionCode.ILLEGAL_DATA_VALUE.getCode());
    }
    if (address + quantity > 65536) {
      throw new ModbusResponseException(functionCode, ExceptionCode.ILLEGAL_DATA_ADDRESS.getCode());
    }
  }

}
