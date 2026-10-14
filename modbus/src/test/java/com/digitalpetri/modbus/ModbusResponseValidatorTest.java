package com.digitalpetri.modbus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.ModbusResponseValidator.DefaultResponseValidator;
import com.digitalpetri.modbus.exceptions.ModbusException;
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
import java.util.List;
import org.junit.jupiter.api.Test;

class ModbusResponseValidatorTest {

  private final ModbusResponseValidator validator = DefaultResponseValidator.INSTANCE;

  @Test
  void readsRequireTheRequestedByteCount() throws Exception {
    for (int quantity : new int[] {1, 8, 9, 2000}) {
      int byteCount = (quantity + 7) / 8;
      checkRead(
          new ReadCoilsRequest(0, quantity),
          new ReadCoilsResponse(new byte[byteCount]),
          new ReadCoilsResponse(new byte[byteCount - 1]),
          new ReadCoilsResponse(new byte[byteCount + 1]));
      checkRead(
          new ReadDiscreteInputsRequest(0, quantity),
          new ReadDiscreteInputsResponse(new byte[byteCount]),
          new ReadDiscreteInputsResponse(new byte[byteCount - 1]),
          new ReadDiscreteInputsResponse(new byte[byteCount + 1]));
    }
    for (int quantity : new int[] {1, 2, 125}) {
      int byteCount = 2 * quantity;
      checkRead(
          new ReadHoldingRegistersRequest(0, quantity),
          new ReadHoldingRegistersResponse(new byte[byteCount]),
          new ReadHoldingRegistersResponse(new byte[byteCount - 2]),
          new ReadHoldingRegistersResponse(new byte[byteCount + 2]));
      checkRead(
          new ReadInputRegistersRequest(0, quantity),
          new ReadInputRegistersResponse(new byte[byteCount]),
          new ReadInputRegistersResponse(new byte[byteCount - 2]),
          new ReadInputRegistersResponse(new byte[byteCount + 2]));
      checkRead(
          new ReadWriteMultipleRegistersRequest(0, quantity, 10, 1, new byte[2]),
          new ReadWriteMultipleRegistersResponse(new byte[byteCount]),
          new ReadWriteMultipleRegistersResponse(new byte[byteCount - 2]),
          new ReadWriteMultipleRegistersResponse(new byte[byteCount + 2]));
    }
  }

  @Test
  void writesRequireEveryEchoedField() throws Exception {
    checkWrite(
        new WriteSingleCoilRequest(10, true),
        new WriteSingleCoilResponse(10, true),
        List.of(new WriteSingleCoilResponse(11, true), new WriteSingleCoilResponse(10, false)));
    checkWrite(
        new WriteSingleRegisterRequest(10, 0x1234),
        new WriteSingleRegisterResponse(10, 0x1234),
        List.of(
            new WriteSingleRegisterResponse(11, 0x1234),
            new WriteSingleRegisterResponse(10, 0x1235)));
    checkWrite(
        new WriteMultipleCoilsRequest(10, 9, new byte[2]),
        new WriteMultipleCoilsResponse(10, 9),
        List.of(new WriteMultipleCoilsResponse(11, 9), new WriteMultipleCoilsResponse(10, 8)));
    checkWrite(
        new WriteMultipleRegistersRequest(10, 2, new byte[4]),
        new WriteMultipleRegistersResponse(10, 2),
        List.of(
            new WriteMultipleRegistersResponse(11, 2), new WriteMultipleRegistersResponse(10, 1)));
    checkWrite(
        new MaskWriteRegisterRequest(10, 0x1234, 0x5678),
        new MaskWriteRegisterResponse(10, 0x1234, 0x5678),
        List.of(
            new MaskWriteRegisterResponse(11, 0x1234, 0x5678),
            new MaskWriteRegisterResponse(10, 0x1235, 0x5678),
            new MaskWriteRegisterResponse(10, 0x1234, 0x5679)));
  }

  @Test
  void standardFunctionCodeDoesNotSelectRulesForCustomPdus() {
    ModbusRequestPdu customRequest = () -> 0x03;
    ModbusResponsePdu customResponse = () -> 0x03;

    assertDoesNotThrow(() -> validator.validate(customRequest, customResponse));
    assertDoesNotThrow(
        () -> validator.validate(customRequest, new ReadHoldingRegistersResponse(new byte[3])));
    assertDoesNotThrow(
        () -> validator.validate(new ReadHoldingRegistersRequest(0, 2), customResponse));
  }

  private void checkRead(
      ModbusRequestPdu request,
      ModbusResponsePdu valid,
      ModbusResponsePdu shortResponse,
      ModbusResponsePdu longResponse)
      throws Exception {
    validator.validate(request, valid);
    for (ModbusResponsePdu invalid : List.of(shortResponse, longResponse)) {
      ModbusException e =
          assertThrows(ModbusException.class, () -> validator.validate(request, invalid));
      assertTrue(e.getMessage().contains("byte count"));
    }
  }

  private void checkWrite(
      ModbusRequestPdu request, ModbusResponsePdu valid, List<ModbusResponsePdu> invalidResponses)
      throws Exception {
    validator.validate(request, valid);
    for (ModbusResponsePdu invalid : invalidResponses) {
      ModbusException e =
          assertThrows(ModbusException.class, () -> validator.validate(request, invalid));
      assertTrue(e.getMessage().contains("expected"));
    }
  }
}
