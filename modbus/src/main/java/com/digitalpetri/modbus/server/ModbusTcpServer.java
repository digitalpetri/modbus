package com.digitalpetri.modbus.server;

import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusTcpFrame;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.pdu.ModbusRequestPdu;
import com.digitalpetri.modbus.pdu.ModbusResponsePdu;
import com.digitalpetri.modbus.pdu.ReadCoilsRequest;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.server.ModbusTcpServerTransport.ModbusTcpRequestContext;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ModbusTcpServer implements ModbusServer {

  private final ModbusServerConfig config;
  private final ModbusTcpServerTransport transport;
  private final AtomicReference<ModbusServices> services =
      new AtomicReference<>(new ModbusServices() {});

  public ModbusTcpServer(
      ModbusServerConfig config,
      ModbusTcpServerTransport transport,
      ModbusServices services
  ) {

    this.config = config;
    this.transport = transport;

    this.services.set(services);
  }

  @Override
  public void start() throws ExecutionException, InterruptedException {
    transport.receive((context, frame) -> handleModbusTcpFrame(frame, context));

    transport.bind().toCompletableFuture().get();
  }

  @Override
  public void stop() throws ExecutionException, InterruptedException {
    transport.unbind().toCompletableFuture().get();
  }

  @Override
  public void setModbusServices(ModbusServices services) {
    if (services == null) {
      throw new NullPointerException("services");
    }
    this.services.set(services);
  }

  protected ModbusTcpFrame handleModbusTcpFrame(
      ModbusTcpFrame frame,
      ModbusTcpRequestContext context
  ) throws Exception {

    MbapHeader header = frame.header();
    ByteBuffer pdu = frame.pdu();
    int functionCode = pdu.get(pdu.position()) & 0xFF;
    ModbusRequestPdu requestPdu =
        (ModbusRequestPdu) config.requestSerializer().decode(functionCode, pdu);

    return handleModbusRequestPdu(context, header.transactionId(), header.unitId(), requestPdu);
  }

  protected ModbusTcpFrame handleModbusRequestPdu(
      ModbusTcpRequestContext context,
      int transactionId,
      int unitId,
      ModbusRequestPdu requestPdu
  ) throws Exception {

    try {
      int fcb = requestPdu.getFunctionCode();
      FunctionCode functionCode = FunctionCode.from(fcb).orElse(null);

      if (functionCode == null) {
        throw new ModbusResponseException(
            requestPdu.getFunctionCode(),
            ExceptionCode.ILLEGAL_FUNCTION.getCode()
        );
      }

      ModbusServices services = this.services.get();
      assert services != null;

      ModbusResponsePdu response = switch (functionCode) {
        case READ_COILS -> {
          if (requestPdu instanceof ReadCoilsRequest request) {
            yield services.readCoils(context, unitId, request);
          } else {
            throw new IllegalArgumentException("expected ReadCoilsRequest");
          }
        }
        case READ_DISCRETE_INPUTS -> {
          if (requestPdu instanceof ReadDiscreteInputsRequest request) {
            yield services.readDiscreteInputs(context, unitId, request);
          } else {
            throw new IllegalArgumentException("expected ReadDiscreteInputsRequest");
          }
        }
        case READ_HOLDING_REGISTERS -> {
          if (requestPdu instanceof ReadHoldingRegistersRequest request) {
            yield services.readHoldingRegisters(context, unitId, request);
          } else {
            throw new IllegalArgumentException("expected ReadHoldingRegistersRequest");
          }
        }
        case READ_INPUT_REGISTERS -> {
          if (requestPdu instanceof ReadInputRegistersRequest request) {
            yield services.readInputRegisters(context, unitId, request);
          } else {
            throw new IllegalArgumentException("expected ReadInputRegistersRequest");
          }
        }
        case WRITE_SINGLE_COIL -> {
          if (requestPdu instanceof WriteSingleCoilRequest request) {
            yield services.writeSingleCoil(context, unitId, request);
          } else {
            throw new IllegalArgumentException("expected WriteSingleCoilRequest");
          }
        }
        case WRITE_SINGLE_REGISTER -> {
          if (requestPdu instanceof WriteSingleRegisterRequest request) {
            yield services.writeSingleRegister(context, unitId, request);
          } else {
            throw new IllegalArgumentException("expected WriteSingleRegisterRequest");
          }
        }
        case WRITE_MULTIPLE_COILS -> {
          if (requestPdu instanceof WriteMultipleCoilsRequest request) {
            yield services.writeMultipleCoils(context, unitId, request);
          } else {
            throw new IllegalArgumentException("expected WriteMultipleCoilsRequest");
          }
        }
        case WRITE_MULTIPLE_REGISTERS -> {
          if (requestPdu instanceof WriteMultipleRegistersRequest request) {
            yield services.writeMultipleRegisters(context, unitId, request);
          } else {
            throw new IllegalArgumentException("expected WriteMultipleRegistersRequest");
          }
        }
        case MASK_WRITE_REGISTER -> {
          if (requestPdu instanceof MaskWriteRegisterRequest request) {
            yield services.maskWriteRegister(context, unitId, request);
          } else {
            throw new IllegalArgumentException("expected MaskWriteRegisterRequest");
          }
        }
        default -> throw new ModbusResponseException(requestPdu.getFunctionCode(),
            ExceptionCode.ILLEGAL_FUNCTION.getCode());
      };

      ByteBuffer pdu = ByteBuffer.allocate(256);

      config.responseSerializer().encode(response, pdu);

      var header = new MbapHeader(transactionId, 0, pdu.position() + 1, unitId);

      return new ModbusTcpFrame(header, pdu.flip());
    } catch (ModbusResponseException e) {
      var header = new MbapHeader(transactionId, 0, 3, unitId);
      int fc = e.getFunctionCode() + 0x80;
      int ec = e.getExceptionCode();
      ByteBuffer pdu = ByteBuffer.allocate(2)
          .put((byte) fc)
          .put((byte) ec)
          .flip();

      return new ModbusTcpFrame(header, pdu);
    }
  }

  /**
   * Create a new {@link ModbusTcpServer} with the given {@link ModbusTcpServerTransport} and
   * {@link ModbusServices}.
   *
   * @param transport the {@link ModbusTcpServerTransport} to use.
   * @param modbusServices the {@link ModbusServices} to use.
   * @return a new {@link ModbusTcpServer}.
   */
  public static ModbusTcpServer create(
      ModbusTcpServerTransport transport,
      ModbusServices modbusServices
  ) {

    return create(transport, modbusServices, b -> {});
  }

  /**
   * Create a new {@link ModbusTcpServer} with the given {@link ModbusTcpServerTransport},
   * {@link ModbusServices}, and a callback that can be used to configure a
   * {@link ModbusServerConfig.Builder}.
   *
   * @param transport the {@link ModbusTcpServerTransport} to use.
   * @param modbusServices the {@link ModbusServices} to use.
   * @param configure a callback that can be used to configure a
   *     {@link ModbusServerConfig.Builder}.
   * @return a new {@link ModbusTcpServer}.
   */
  public static ModbusTcpServer create(
      ModbusTcpServerTransport transport,
      ModbusServices modbusServices,
      Consumer<ModbusServerConfig.Builder> configure
  ) {

    ModbusServerConfig.Builder builder = new ModbusServerConfig.Builder();
    configure.accept(builder);
    return new ModbusTcpServer(builder.build(), transport, modbusServices);
  }

}
