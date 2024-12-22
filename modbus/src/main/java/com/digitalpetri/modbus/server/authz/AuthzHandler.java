package com.digitalpetri.modbus.server.authz;

import java.util.Optional;

/**
 * Callback interface that handles authorization of Modbus operations.
 */
public interface AuthzHandler {

  /**
   * Authorizes the reading of coils.
   *
   * @param clientRole the role of the client attempting the operation, if available.
   * @param unitId the unit identifier of the Modbus device.
   * @param address the starting address of the coils to read.
   * @param quantity the number of coils to read.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeReadCoils(Optional<String> clientRole, int unitId, int address, int quantity);

  /**
   * Authorizes the reading of discrete inputs.
   *
   * @param clientRole the role of the client attempting the operation, if available.
   * @param unitId the unit identifier of the Modbus device.
   * @param address the starting address of the discrete inputs to read.
   * @param quantity the number of discrete inputs to read.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeReadDiscreteInputs(Optional<String> clientRole, int unitId, int address, int quantity);

  /**
   * Authorizes the reading of holding registers.
   *
   * @param clientRole the role of the client attempting the operation, if available.
   * @param unitId the unit identifier of the Modbus device.
   * @param address the starting address of the holding registers to read.
   * @param quantity the number of holding registers to read.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeReadHoldingRegisters(Optional<String> clientRole, int unitId, int address, int quantity);

  /**
   * Authorizes the reading of input registers.
   *
   * @param clientRole the role of the client attempting the operation, if available.
   * @param unitId the unit identifier of the Modbus device.
   * @param address the starting address of the input registers to read.
   * @param quantity the number of input registers to read.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeReadInputRegisters(Optional<String> clientRole, int unitId, int address, int quantity);

  /**
   * Authorizes the writing of single coil.
   *
   * @param clientRole the role of the client attempting the operation, if available.
   * @param unitId the unit identifier of the Modbus device.
   * @param address the address of the coil to write.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeWriteSingleCoil(Optional<String> clientRole, int unitId, int address);

  /**
   * Authorizes the writing of single register.
   *
   * @param clientRole the role of the client attempting the operation, if available.
   * @param unitId the unit identifier of the Modbus device.
   * @param address the address of the register to write.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeWriteSingleRegister(Optional<String> clientRole, int unitId, int address);

  /**
   * Authorizes the writing of multiple coils.
   *
   * @param clientRole the role of the client attempting the operation, if available.
   * @param unitId the unit identifier of the Modbus device.
   * @param address the starting address of the coils to write.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeWriteMultipleCoils(Optional<String> clientRole, int unitId, int address);

  /**
   * Authorizes the writing of multiple registers.
   *
   * @param clientRole the role of the client attempting the operation, if available.
   * @param unitId the unit identifier of the Modbus device.
   * @param address the starting address of the registers to write.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeWriteMultipleRegisters(Optional<String> clientRole, int unitId, int address);

  /**
   * Authorizes the mask writing of a register.
   *
   * @param clientRole the role of the client attempting the operation, if available.
   * @param unitId the unit identifier of the Modbus device.
   * @param address the address of the register to mask write.
   * @param andMask the AND mask to apply to the register.
   * @param orMask the OR mask to apply to the register.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeMaskWriteRegister(Optional<String> clientRole, int unitId, int address, int andMask, int orMask);

  /**
   * Authorizes the reading and writing of multiple registers.
   *
   * @param clientRole the role of the client attempting the operation, if available.
   * @param unitId the unit identifier of the Modbus device.
   * @param readAddress the starting address of the registers to read.
   * @param readQuantity the number of registers to read.
   * @param writeAddress the starting address of the registers to write.
   * @param writeQuantity the number of registers to write.
   * @return the result of the authorization check.
   */
  AuthzResult authorizeReadWriteMultipleRegisters(Optional<String> clientRole, int unitId, int readAddress,
      int readQuantity, int writeAddress, int writeQuantity);
  
  /**
   * Enumeration representing the result of an authorization check.
   */
  public enum AuthzResult {

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