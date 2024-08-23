package com.digitalpetri.modbus.pdu;


/**
 * Super-interface for objects that can be encoded as a Modbus PDU.
 */
public interface ModbusPdu {

  /**
   * Get the function code identifying this PDU.
   *
   * @return the function code identifying this PDU.
   */
  int getFunctionCode();

}
