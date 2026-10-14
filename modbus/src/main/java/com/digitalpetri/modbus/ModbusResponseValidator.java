package com.digitalpetri.modbus;

import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.internal.util.Hex;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterResponse;
import com.digitalpetri.modbus.pdu.ModbusRequestPdu;
import com.digitalpetri.modbus.pdu.ModbusResponsePdu;
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
import java.util.Arrays;

/**
 * Validates a decoded response against its request.
 *
 * <p>Clients invoke the configured validator after decoding a normal response and before completing
 * the request successfully. Codecs own response format checks, including complete-PDU consumption.
 * Raw requests and exception responses do not invoke this validator.
 *
 * <p>Implementations must be safe for concurrent use by the clients that share them.
 */
@FunctionalInterface
public interface ModbusResponseValidator {

  /**
   * Validate a decoded response against its request.
   *
   * @param request the original request.
   * @param response the decoded response.
   * @throws ModbusException if the response does not satisfy the request.
   */
  void validate(ModbusRequestPdu request, ModbusResponsePdu response) throws ModbusException;

  /**
   * Standard request-relative checks for matching built-in request and response record pairs.
   *
   * <p>Other PDU types are ignored, even when they use a standard function code. Applications using
   * built-in records with different register widths or echo rules must replace this validator along
   * with their codecs. Write Single Register values may have any length, but must be echoed
   * exactly.
   */
  class DefaultResponseValidator implements ModbusResponseValidator {

    /** A shared, stateless instance safe for concurrent use. */
    public static final DefaultResponseValidator INSTANCE = new DefaultResponseValidator();

    @Override
    public void validate(ModbusRequestPdu request, ModbusResponsePdu response)
        throws ModbusException {
      if (request instanceof ReadCoilsRequest q && response instanceof ReadCoilsResponse r) {
        check(request, "coil byte count", (q.quantity() + 7L) / 8, r.coils().length);
      } else if (request instanceof ReadDiscreteInputsRequest q
          && response instanceof ReadDiscreteInputsResponse r) {
        check(request, "input byte count", (q.quantity() + 7L) / 8, r.inputs().length);
      } else if (request instanceof ReadHoldingRegistersRequest q
          && response instanceof ReadHoldingRegistersResponse r) {
        check(request, "register byte count", 2L * q.quantity(), r.registers().length);
      } else if (request instanceof ReadInputRegistersRequest q
          && response instanceof ReadInputRegistersResponse r) {
        check(request, "register byte count", 2L * q.quantity(), r.registers().length);
      } else if (request instanceof ReadWriteMultipleRegistersRequest q
          && response instanceof ReadWriteMultipleRegistersResponse r) {
        check(request, "register byte count", 2L * q.readQuantity(), r.registers().length);
      } else if (request instanceof WriteSingleCoilRequest q
          && response instanceof WriteSingleCoilResponse r) {
        check(request, "address", q.address(), r.address());
        check(request, "value", q.value(), r.value());
      } else if (request instanceof WriteSingleRegisterRequest q
          && response instanceof WriteSingleRegisterResponse r) {
        check(request, "address", q.address(), r.address());
        if (!Arrays.equals(q.value(), r.value())) {
          throw new ModbusException(
              "response for FC 0x%02X has unexpected value: expected %s, received %s"
                  .formatted(
                      request.getFunctionCode(), Hex.format(q.value()), Hex.format(r.value())));
        }
      } else if (request instanceof WriteMultipleCoilsRequest q
          && response instanceof WriteMultipleCoilsResponse r) {
        check(request, "address", q.address(), r.address());
        check(request, "quantity", q.quantity(), r.quantity());
      } else if (request instanceof WriteMultipleRegistersRequest q
          && response instanceof WriteMultipleRegistersResponse r) {
        check(request, "address", q.address(), r.address());
        check(request, "quantity", q.quantity(), r.quantity());
      } else if (request instanceof MaskWriteRegisterRequest q
          && response instanceof MaskWriteRegisterResponse r) {
        check(request, "address", q.address(), r.address());
        check(request, "AND mask", q.andMask(), r.andMask());
        check(request, "OR mask", q.orMask(), r.orMask());
      }
    }

    private static void check(ModbusRequestPdu request, String field, long expected, long actual)
        throws ModbusException {
      if (actual != expected) {
        throw new ModbusException(
            "response for FC 0x%02X has unexpected %s: expected %d, received %d"
                .formatted(request.getFunctionCode(), field, expected, actual));
      }
    }
  }
}
