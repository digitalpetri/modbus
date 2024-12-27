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
 * A simplified {@link AuthzHandler} that determines authorization based on whether the client has
 * read or write access to a given unit id.
 *
 * <p>Subclasses must implement {@link #authorizeRead(int, AuthzContext)} and
 * {@link #authorizeWrite(int, AuthzContext)}. The default implementations of the read and write
 * methods will call these methods to determine authorization.
 *
 * <p>Operations that require read authorization:
 * <ul>
 *   <li>ReadCoils
 *   <li>ReadDiscreteInputs
 *   <li>ReadHoldingRegisters
 *   <li>ReadInputRegisters
 * </ul>
 *
 * <p>Operations that require write authorization:
 * <ul>
 *   <li>WriteSingleCoil
 *   <li>WriteSingleRegister
 *   <li>WriteMultipleCoils
 *   <li>WriteMultipleRegisters
 *   <li>MaskWriteRegister
 * </ul>
 *
 * <p>Operations that require both read and write authorization:
 * <ul>
 *   <li>ReadWriteMultipleRegisters
 * </ul>
 */
public abstract class ReadWriteAuthzHandler implements AuthzHandler {

  @Override
  public AuthzResult authorizeReadCoils(
      AuthzContext authzContext,
      int unitId,
      ReadCoilsRequest request
  ) {

    return authorizeRead(unitId, authzContext);
  }

  @Override
  public AuthzResult authorizeReadDiscreteInputs(
      AuthzContext authzContext,
      int unitId,
      ReadDiscreteInputsRequest request
  ) {

    return authorizeRead(unitId, authzContext);
  }

  @Override
  public AuthzResult authorizeReadHoldingRegisters(
      AuthzContext authzContext,
      int unitId,
      ReadHoldingRegistersRequest request
  ) {

    return authorizeRead(unitId, authzContext);
  }

  @Override
  public AuthzResult authorizeReadInputRegisters(
      AuthzContext authzContext,
      int unitId,
      ReadInputRegistersRequest request
  ) {

    return authorizeRead(unitId, authzContext);
  }

  @Override
  public AuthzResult authorizeWriteSingleCoil(
      AuthzContext authzContext,
      int unitId,
      WriteSingleCoilRequest request
  ) {

    return authorizeWrite(unitId, authzContext);
  }

  @Override
  public AuthzResult authorizeWriteSingleRegister(
      AuthzContext authzContext,
      int unitId,
      WriteSingleRegisterRequest request
  ) {

    return authorizeWrite(unitId, authzContext);
  }

  @Override
  public AuthzResult authorizeWriteMultipleCoils(
      AuthzContext authzContext,
      int unitId,
      WriteMultipleCoilsRequest request
  ) {

    return authorizeWrite(unitId, authzContext);
  }

  @Override
  public AuthzResult authorizeWriteMultipleRegisters(
      AuthzContext authzContext,
      int unitId,
      WriteMultipleRegistersRequest request
  ) {

    return authorizeWrite(unitId, authzContext);
  }

  @Override
  public AuthzResult authorizeMaskWriteRegister(
      AuthzContext authzContext,
      int unitId,
      MaskWriteRegisterRequest request
  ) {

    return authorizeWrite(unitId, authzContext);
  }

  @Override
  public AuthzResult authorizeReadWriteMultipleRegisters(
      AuthzContext authzContext,
      int unitId,
      ReadWriteMultipleRegistersRequest request
  ) {

    AuthzResult readResult = authorizeRead(unitId, authzContext);
    AuthzResult writeResult = authorizeWrite(unitId, authzContext);

    if (readResult == AuthzResult.AUTHORIZED && writeResult == AuthzResult.AUTHORIZED) {
      return AuthzResult.AUTHORIZED;
    } else {
      return AuthzResult.NOT_AUTHORIZED;
    }
  }

  /**
   * Authorize a read operation against the given unit id.
   *
   * @param unitId the unit id to authorize against.
   * @param authzContext the {@link AuthzContext}.
   * @return the result of the authorization check.
   */
  protected abstract AuthzResult authorizeRead(int unitId, AuthzContext authzContext);

  /**
   * Authorize a write operation against the given unit id.
   *
   * @param unitId the unit id to authorize against.
   * @param authzContext the {@link AuthzContext}.
   * @return the result of the authorization check.
   */
  protected abstract AuthzResult authorizeWrite(int unitId, AuthzContext authzContext);

}
