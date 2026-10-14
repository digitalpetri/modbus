package com.digitalpetri.modbus.pdu;

/** Super-interface for Modbus request PDUs. */
public interface ModbusRequestPdu extends ModbusPdu {

  /**
   * Validate this request before outbound encoding.
   *
   * <p>Built-in requests enforce their standard protocol limits. The default implementation does
   * nothing, so custom requests can define their own validation rules independently of their
   * function code. Clients invoke this method before calling the configured request serializer.
   * Built-in serializers also invoke it before writing any bytes.
   *
   * <p>Validation is repeated for each encoding because request payloads may be mutable.
   * Constructors and decoders do not invoke validation, so servers can represent invalid requests
   * and return Modbus exception responses. Device-specific address availability remains a server
   * concern.
   *
   * @throws IllegalArgumentException if this request violates its protocol limits.
   */
  default void validate() {}
}
