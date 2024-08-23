package com.digitalpetri.modbus.server;

import com.digitalpetri.modbus.Crc16;
import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
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
import com.digitalpetri.modbus.server.ModbusRtuServerTransport.ModbusRtuRequestContext;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ModbusRtuServer implements ModbusServer {

  private final ModbusServerConfig config;
  private final ModbusRtuServerTransport transport;
  private final AtomicReference<ModbusServices> services =
      new AtomicReference<>(new ModbusServices() {});

  public ModbusRtuServer(
      ModbusServerConfig config,
      ModbusRtuServerTransport transport,
      ModbusServices services
  ) {

    this.config = config;
    this.transport = transport;

    this.services.set(services);
  }

  @Override
  public void start() throws ExecutionException, InterruptedException {
    transport.receive((context, frame) -> {
      int unitId = frame.unitId();
      ByteBuffer pdu = frame.pdu();
      int fcb = pdu.get(pdu.position()) & 0xFF;
      ModbusRequestPdu requestPdu = (ModbusRequestPdu) config.requestSerializer().decode(fcb, pdu);

      return handleModbusRtuFrame(context, unitId, fcb, requestPdu);
    });

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

  protected ModbusRtuFrame handleModbusRtuFrame(
      ModbusRtuRequestContext context,
      int unitId,
      int fcb,
      ModbusRequestPdu requestPdu
  ) throws Exception {

    try {
      FunctionCode functionCode = FunctionCode.from(fcb).orElse(null);

      if (functionCode == null) {
        throw new ModbusResponseException(
            fcb,
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
      pdu.flip();

      ByteBuffer crc = calculateCrc16(unitId, pdu);
      crc.flip();

      return new ModbusRtuFrame(unitId, pdu, crc);
    } catch (ModbusResponseException e) {
      int fc = fcb + 0x80;
      int ec = e.getExceptionCode();

      ByteBuffer pdu = ByteBuffer.allocate(2)
          .put((byte) fc)
          .put((byte) ec);

      ByteBuffer crc = calculateCrc16(unitId, pdu);

      return new ModbusRtuFrame(unitId, pdu.flip(), crc.flip());
    }
  }

  private ByteBuffer calculateCrc16(int unitId, ByteBuffer pdu) {
    var crc16 = new Crc16();
    crc16.update(unitId);
    crc16.update(pdu);

    ByteBuffer crc = ByteBuffer.allocate(2);
    // write crc in little-endian order
    crc.put((byte) (crc16.getValue() & 0xFF));
    crc.put((byte) ((crc16.getValue() >> 8) & 0xFF));

    return crc;
  }

  /**
   * Create a new {@link ModbusRtuServer} with the given {@link ModbusRtuServerTransport} and
   * {@link ModbusServices}.
   *
   * @param transport the {@link ModbusRtuServerTransport} to use.
   * @param modbusServices the {@link ModbusServices} to use.
   * @return a new {@link ModbusRtuServer}.
   */
  public static ModbusRtuServer create(
      ModbusRtuServerTransport transport,
      ModbusServices modbusServices
  ) {

    return create(transport, modbusServices, b -> {});
  }

  /**
   * Create a new {@link ModbusRtuServer} with the given {@link ModbusRtuServerTransport},
   * {@link ModbusServices}, and a callback that can be used to configure a
   * {@link ModbusServerConfig.Builder}.
   *
   * @param transport the {@link ModbusRtuServerTransport} to use.
   * @param modbusServices the {@link ModbusServices} to use.
   * @param configure a callback that can be used to configure a
   *     {@link ModbusServerConfig.Builder}.
   * @return a new {@link ModbusRtuServer}.
   */
  public static ModbusRtuServer create(
      ModbusRtuServerTransport transport,
      ModbusServices modbusServices,
      Consumer<ModbusServerConfig.Builder> configure
  ) {

    ModbusServerConfig.Builder builder = new ModbusServerConfig.Builder();
    configure.accept(builder);
    return new ModbusRtuServer(builder.build(), transport, modbusServices);
  }

}

