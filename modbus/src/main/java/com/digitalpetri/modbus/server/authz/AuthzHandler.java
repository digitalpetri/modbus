package com.digitalpetri.modbus.server.authz;

import com.digitalpetri.modbus.pdu.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadWriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;

/**
 * Callback interface that handles authorization of Modbus operations.
 */
public interface AuthzHandler {

  /**
   * Authorizes the reading of coils.
   *
   * @param authzContext the {@link AuthzContext}.
   * @param unitId the unit identifier of the Modbus device.
   * @param request the {@link ReadCoilsRequest} being authorized.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeReadCoils(AuthzContext authzContext, int unitId, ReadCoilsRequest request);

  /**
   * Authorizes the reading of discrete inputs.
   *
   * @param authzContext the {@link AuthzContext}.
   * @param unitId the unit identifier of the Modbus device.
   * @param request the {@link ReadDiscreteInputsRequest} being authorized.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeReadDiscreteInputs(AuthzContext authzContext, int unitId,
      ReadDiscreteInputsRequest request);

  /**
   * Authorizes the reading of holding registers.
   *
   * @param authzContext the {@link AuthzContext}.
   * @param unitId the unit identifier of the Modbus device.
   * @param request the {@link ReadHoldingRegistersRequest} being authorized.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeReadHoldingRegisters(AuthzContext authzContext, int unitId,
      ReadHoldingRegistersRequest request);

  /**
   * Authorizes the reading of input registers.
   *
   * @param authzContext the {@link AuthzContext}.
   * @param unitId the unit identifier of the Modbus device.
   * @param request the {@link ReadInputRegistersRequest} being authorized.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeReadInputRegisters(AuthzContext authzContext, int unitId,
      ReadInputRegistersRequest request);

  /**
   * Authorizes the writing of a single coil.
   *
   * @param authzContext the {@link AuthzContext}.
   * @param unitId the unit identifier of the Modbus device.
   * @param request the {@link WriteSingleCoilRequest} being authorized.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeWriteSingleCoil(AuthzContext authzContext, int unitId,
      WriteSingleCoilRequest request);

  /**
   * Authorizes the writing of a single register.
   *
   * @param authzContext the {@link AuthzContext}.
   * @param unitId the unit identifier of the Modbus device.
   * @param request the {@link WriteSingleRegisterRequest} being authorized.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeWriteSingleRegister(AuthzContext authzContext, int unitId,
      WriteSingleRegisterRequest request);

  /**
   * Authorizes the writing of multiple coils.
   *
   * @param authzContext the {@link AuthzContext}.
   * @param unitId the unit identifier of the Modbus device.
   * @param request the {@link WriteMultipleCoilsRequest} being authorized.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeWriteMultipleCoils(AuthzContext authzContext, int unitId,
      WriteMultipleCoilsRequest request);

  /**
   * Authorizes the writing of multiple registers.
   *
   * @param authzContext the {@link AuthzContext}.
   * @param unitId the unit identifier of the Modbus device.
   * @param request the {@link WriteMultipleRegistersRequest} being authorized.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeWriteMultipleRegisters(AuthzContext authzContext, int unitId,
      WriteMultipleRegistersRequest request);

  /**
   * Authorizes the mask write register operation.
   *
   * @param authzContext the {@link AuthzContext}.
   * @param unitId the unit identifier of the Modbus device.
   * @param request the {@link MaskWriteRegisterRequest} being authorized.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeMaskWriteRegister(AuthzContext authzContext, int unitId,
      MaskWriteRegisterRequest request);

  /**
   * Authorizes the read-write-multiple registers operation.
   *
   * @param authzContext the {@link AuthzContext}.
   * @param unitId the unit identifier of the Modbus device.
   * @param request the {@link ReadWriteMultipleRegistersRequest} being authorized.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeReadWriteMultipleRegisters(AuthzContext authzContext, int unitId,
      ReadWriteMultipleRegistersRequest request);

  /**
   * Enumeration representing the result of an authorization check.
   */
  enum AuthzResult {

    /**
     * Indicates that the operation is allowed.
     */
    ALLOWED,

    /**
     * Indicates that the operation is denied.
     */
    DENIED

  }

}
