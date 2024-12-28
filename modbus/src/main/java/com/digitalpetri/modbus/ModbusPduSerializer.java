package com.digitalpetri.modbus;

import com.digitalpetri.modbus.exceptions.ModbusException;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterResponse;
import com.digitalpetri.modbus.pdu.ModbusPdu;
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
import java.nio.ByteBuffer;

public interface ModbusPduSerializer {

  /**
   * Encode a Modbus PDU into a {@link ByteBuffer}.
   *
   * @param pdu the PDU object.
   * @param buffer the buffer to encode into.
   * @throws Exception if an error occurs during encoding.
   */
  void encode(ModbusPdu pdu, ByteBuffer buffer) throws Exception;

  /**
   * Decode a Modbus PDU from a {@link ByteBuffer}.
   *
   * @param functionCode the function code of the PDU to decode.
   * @param buffer the buffer to decode from.
   * @return the decoded PDU object.
   * @throws Exception if error occurs during decoding.
   */
  ModbusPdu decode(int functionCode, ByteBuffer buffer) throws Exception;

  class DefaultRequestSerializer implements ModbusPduSerializer {

    /**
     * A shared instance of {@link DefaultRequestSerializer}.
     *
     * <p>This instance is stateless and therefore safe for concurrent use by any number of threads.
     */
    public static final DefaultRequestSerializer INSTANCE = new DefaultRequestSerializer();

    @Override
    public void encode(ModbusPdu pdu, ByteBuffer buffer) throws ModbusException {
      switch (pdu.getFunctionCode()) {
        case 0x01 -> {
          if (pdu instanceof ReadCoilsRequest request) {
            ReadCoilsRequest.Serializer.encode(request, buffer);
          } else {
            throw new IllegalArgumentException("expected ReadCoilsRequest");
          }
        }
        case 0x02 -> {
          if (pdu instanceof ReadDiscreteInputsRequest request) {
            ReadDiscreteInputsRequest.Serializer.encode(request, buffer);
          } else {
            throw new IllegalArgumentException("expected ReadDiscreteInputsRequest");
          }
        }
        case 0x03 -> {
          if (pdu instanceof ReadHoldingRegistersRequest request) {
            ReadHoldingRegistersRequest.Serializer.encode(request, buffer);
          } else {
            throw new IllegalArgumentException("expected ReadHoldingRegistersRequest");
          }
        }
        case 0x04 -> {
          if (pdu instanceof ReadInputRegistersRequest request) {
            ReadInputRegistersRequest.Serializer.encode(request, buffer);
          } else {
            throw new IllegalArgumentException("expected ReadInputRegistersRequest");
          }
        }
        case 0x05 -> {
          if (pdu instanceof WriteSingleCoilRequest request) {
            WriteSingleCoilRequest.Serializer.encode(request, buffer);
          } else {
            throw new IllegalArgumentException("expected WriteSingleCoilRequest");
          }
        }
        case 0x06 -> {
          if (pdu instanceof WriteSingleRegisterRequest request) {
            WriteSingleRegisterRequest.Serializer.encode(request, buffer);
          } else {
            throw new IllegalArgumentException("expected WriteSingleRegisterRequest");
          }
        }
        case 0x0F -> {
          if (pdu instanceof WriteMultipleCoilsRequest request) {
            WriteMultipleCoilsRequest.Serializer.encode(request, buffer);
          } else {
            throw new IllegalArgumentException("expected WriteMultipleCoilsRequest");
          }
        }
        case 0x10 -> {
          if (pdu instanceof WriteMultipleRegistersRequest request) {
            WriteMultipleRegistersRequest.Serializer.encode(request, buffer);
          } else {
            throw new IllegalArgumentException("expected WriteMultipleRegistersRequest");
          }
        }
        case 0x16 -> {
          if (pdu instanceof MaskWriteRegisterRequest request) {
            MaskWriteRegisterRequest.Serializer.encode(request, buffer);
          } else {
            throw new IllegalArgumentException("expected MaskWriteRegisterRequest");
          }
        }
        case 0x17 -> {
          if (pdu instanceof ReadWriteMultipleRegistersRequest request) {
            ReadWriteMultipleRegistersRequest.Serializer.encode(request, buffer);
          } else {
            throw new IllegalArgumentException("expected ReadWriteMultipleRegistersRequest");
          }
        }
        default ->
            throw new ModbusException(
                "no serializer for functionCode=0x%02X".formatted(pdu.getFunctionCode()));
      }
    }

    @Override
    public ModbusPdu decode(int functionCode, ByteBuffer buffer) throws ModbusException {
      return switch (functionCode) {
        case 0x01 -> ReadCoilsRequest.Serializer.decode(buffer);
        case 0x02 -> ReadDiscreteInputsRequest.Serializer.decode(buffer);
        case 0x03 -> ReadHoldingRegistersRequest.Serializer.decode(buffer);
        case 0x04 -> ReadInputRegistersRequest.Serializer.decode(buffer);
        case 0x05 -> WriteSingleCoilRequest.Serializer.decode(buffer);
        case 0x06 -> WriteSingleRegisterRequest.Serializer.decode(buffer);
        case 0x0F -> WriteMultipleCoilsRequest.Serializer.decode(buffer);
        case 0x10 -> WriteMultipleRegistersRequest.Serializer.decode(buffer);
        case 0x16 -> MaskWriteRegisterRequest.Serializer.decode(buffer);
        case 0x17 -> ReadWriteMultipleRegistersRequest.Serializer.decode(buffer);
        default ->
            throw new ModbusException(
                "no serializer for functionCode=0x%02X".formatted(functionCode));
      };
    }
  }

  class DefaultResponseSerializer implements ModbusPduSerializer {

    /**
     * A shared instance of {@link DefaultResponseSerializer}.
     *
     * <p>This instance is stateless and therefore safe for concurrent use by any number of threads.
     */
    public static final DefaultResponseSerializer INSTANCE = new DefaultResponseSerializer();

    @Override
    public void encode(ModbusPdu pdu, ByteBuffer buffer) throws ModbusException {
      switch (pdu.getFunctionCode()) {
        case 0x01 -> {
          if (pdu instanceof ReadCoilsResponse response) {
            ReadCoilsResponse.Serializer.encode(response, buffer);
          } else {
            throw new IllegalArgumentException("expected ReadCoilsResponse");
          }
        }
        case 0x02 -> {
          if (pdu instanceof ReadDiscreteInputsResponse response) {
            ReadDiscreteInputsResponse.Serializer.encode(response, buffer);
          } else {
            throw new IllegalArgumentException("expected ReadDiscreteInputsResponse");
          }
        }
        case 0x03 -> {
          if (pdu instanceof ReadHoldingRegistersResponse response) {
            ReadHoldingRegistersResponse.Serializer.encode(response, buffer);
          } else {
            throw new IllegalArgumentException("expected ReadHoldingRegistersResponse");
          }
        }
        case 0x04 -> {
          if (pdu instanceof ReadInputRegistersResponse response) {
            ReadInputRegistersResponse.Serializer.encode(response, buffer);
          } else {
            throw new IllegalArgumentException("expected ReadInputRegistersResponse");
          }
        }
        case 0x05 -> {
          if (pdu instanceof WriteSingleCoilResponse response) {
            WriteSingleCoilResponse.Serializer.encode(response, buffer);
          } else {
            throw new IllegalArgumentException("expected WriteSingleCoilResponse");
          }
        }
        case 0x06 -> {
          if (pdu instanceof WriteSingleRegisterResponse response) {
            WriteSingleRegisterResponse.Serializer.encode(response, buffer);
          } else {
            throw new IllegalArgumentException("expected WriteSingleRegisterResponse");
          }
        }
        case 0x0F -> {
          if (pdu instanceof WriteMultipleCoilsResponse response) {
            WriteMultipleCoilsResponse.Serializer.encode(response, buffer);
          } else {
            throw new IllegalArgumentException("expected WriteMultipleCoilsResponse");
          }
        }
        case 0x10 -> {
          if (pdu instanceof WriteMultipleRegistersResponse response) {
            WriteMultipleRegistersResponse.Serializer.encode(response, buffer);
          } else {
            throw new IllegalArgumentException("expected WriteMultipleRegistersResponse");
          }
        }
        case 0x16 -> {
          if (pdu instanceof MaskWriteRegisterResponse response) {
            MaskWriteRegisterResponse.Serializer.encode(response, buffer);
          } else {
            throw new IllegalArgumentException("expected MaskWriteRegisterResponse");
          }
        }
        case 0x17 -> {
          if (pdu instanceof ReadWriteMultipleRegistersResponse response) {
            ReadWriteMultipleRegistersResponse.Serializer.encode(response, buffer);
          } else {
            throw new IllegalArgumentException("expected ReadWriteMultipleRegistersResponse");
          }
        }
        default ->
            throw new ModbusException(
                "no serializer for functionCode=0x%02X".formatted(pdu.getFunctionCode()));
      }
    }

    @Override
    public ModbusPdu decode(int functionCode, ByteBuffer buffer) throws ModbusException {
      return switch (functionCode) {
        case 0x01 -> ReadCoilsResponse.Serializer.decode(buffer);
        case 0x02 -> ReadDiscreteInputsResponse.Serializer.decode(buffer);
        case 0x03 -> ReadHoldingRegistersResponse.Serializer.decode(buffer);
        case 0x04 -> ReadInputRegistersResponse.Serializer.decode(buffer);
        case 0x05 -> WriteSingleCoilResponse.Serializer.decode(buffer);
        case 0x06 -> WriteSingleRegisterResponse.Serializer.decode(buffer);
        case 0x0F -> WriteMultipleCoilsResponse.Serializer.decode(buffer);
        case 0x10 -> WriteMultipleRegistersResponse.Serializer.decode(buffer);
        case 0x16 -> MaskWriteRegisterResponse.Serializer.decode(buffer);
        case 0x17 -> ReadWriteMultipleRegistersResponse.Serializer.decode(buffer);
        default ->
            throw new ModbusException(
                "no serializer for functionCode=0x%02X".formatted(functionCode));
      };
    }
  }
}
