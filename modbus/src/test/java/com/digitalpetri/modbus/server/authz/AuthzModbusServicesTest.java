package com.digitalpetri.modbus.server.authz;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.digitalpetri.modbus.exceptions.ModbusException;
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
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpTlsRequestContext;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import java.net.SocketAddress;
import java.security.cert.X509Certificate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthzModbusServicesTest {

  private final TestModbusContext readOnlyContext = new TestModbusContext("ReadOnly");
  private final TestModbusContext writeOnlyContext = new TestModbusContext("WriteOnly");
  private final TestModbusContext readWriteContext = new TestModbusContext("ReadWrite");

  private final AuthzHandler readAuthzHandler =
      new ReadWriteAuthzHandler() {
        @Override
        protected AuthzResult authorizeRead(int unitId, AuthzContext authzContext) {
          String clientRole = authzContext.clientRole().orElseThrow();
          return clientRole.equals("ReadOnly") || clientRole.equals("ReadWrite")
              ? AuthzResult.AUTHORIZED
              : AuthzResult.NOT_AUTHORIZED;
        }

        @Override
        protected AuthzResult authorizeWrite(int unitId, AuthzContext authzContext) {
          String clientRole = authzContext.clientRole().orElseThrow();
          return clientRole.equals("WriteOnly") || clientRole.equals("ReadWrite")
              ? AuthzResult.AUTHORIZED
              : AuthzResult.NOT_AUTHORIZED;
        }
      };

  private final AuthzModbusServices services =
      new AuthzModbusServices(
          readAuthzHandler,
          new ReadWriteModbusServices() {
            private final ProcessImage processImage = new ProcessImage();

            @Override
            protected Optional<ProcessImage> getProcessImage(int unitId) {
              return Optional.of(processImage);
            }
          });

  @Test
  void testReadCoils() {
    assertDoesNotThrow(
        () -> {
          services.readCoils(readOnlyContext, 1, new ReadCoilsRequest(0, 1));
        });
    assertThrows(
        ModbusException.class,
        () -> {
          services.readCoils(writeOnlyContext, 1, new ReadCoilsRequest(0, 1));
        });
    assertDoesNotThrow(
        () -> {
          services.readCoils(readWriteContext, 1, new ReadCoilsRequest(0, 1));
        });
  }

  @Test
  void testReadDiscreteInputs() {
    assertDoesNotThrow(
        () -> {
          services.readDiscreteInputs(readOnlyContext, 1, new ReadDiscreteInputsRequest(0, 1));
        });
    assertThrows(
        ModbusException.class,
        () -> {
          services.readDiscreteInputs(writeOnlyContext, 1, new ReadDiscreteInputsRequest(0, 1));
        });
    assertDoesNotThrow(
        () -> {
          services.readDiscreteInputs(readWriteContext, 1, new ReadDiscreteInputsRequest(0, 1));
        });
  }

  @Test
  void testReadHoldingRegisters() {
    assertDoesNotThrow(
        () -> {
          services.readHoldingRegisters(readOnlyContext, 1, new ReadHoldingRegistersRequest(0, 1));
        });
    assertThrows(
        ModbusException.class,
        () -> {
          services.readHoldingRegisters(writeOnlyContext, 1, new ReadHoldingRegistersRequest(0, 1));
        });
    assertDoesNotThrow(
        () -> {
          services.readHoldingRegisters(readWriteContext, 1, new ReadHoldingRegistersRequest(0, 1));
        });
  }

  @Test
  void testReadInputRegisters() {
    assertDoesNotThrow(
        () -> {
          services.readInputRegisters(readOnlyContext, 1, new ReadInputRegistersRequest(0, 1));
        });
    assertThrows(
        ModbusException.class,
        () -> {
          services.readInputRegisters(writeOnlyContext, 1, new ReadInputRegistersRequest(0, 1));
        });
    assertDoesNotThrow(
        () -> {
          services.readInputRegisters(readWriteContext, 1, new ReadInputRegistersRequest(0, 1));
        });
  }

  @Test
  void testWriteSingleCoil() {
    assertThrows(
        ModbusException.class,
        () -> {
          services.writeSingleCoil(readOnlyContext, 1, new WriteSingleCoilRequest(0, true));
        });
    assertDoesNotThrow(
        () -> {
          services.writeSingleCoil(writeOnlyContext, 1, new WriteSingleCoilRequest(0, true));
        });
    assertDoesNotThrow(
        () -> {
          services.writeSingleCoil(readWriteContext, 1, new WriteSingleCoilRequest(0, true));
        });
  }

  @Test
  void testWriteSingleRegister() {
    assertThrows(
        ModbusException.class,
        () -> {
          services.writeSingleRegister(readOnlyContext, 1, new WriteSingleRegisterRequest(0, 1234));
        });
    assertDoesNotThrow(
        () -> {
          services.writeSingleRegister(
              writeOnlyContext, 1, new WriteSingleRegisterRequest(0, 1234));
        });
    assertDoesNotThrow(
        () -> {
          services.writeSingleRegister(
              readWriteContext, 1, new WriteSingleRegisterRequest(0, 1234));
        });
  }

  @Test
  void testWriteMultipleCoils() {
    assertThrows(
        ModbusException.class,
        () -> {
          services.writeMultipleCoils(
              readOnlyContext, 1, new WriteMultipleCoilsRequest(0, 1, new byte[] {0}));
        });
    assertDoesNotThrow(
        () -> {
          services.writeMultipleCoils(
              writeOnlyContext, 1, new WriteMultipleCoilsRequest(0, 1, new byte[] {0}));
        });
    assertDoesNotThrow(
        () -> {
          services.writeMultipleCoils(
              readWriteContext, 1, new WriteMultipleCoilsRequest(0, 1, new byte[] {0}));
        });
  }

  @Test
  void testWriteMultipleRegisters() {
    assertThrows(
        ModbusException.class,
        () -> {
          services.writeMultipleRegisters(
              readOnlyContext, 1, new WriteMultipleRegistersRequest(0, 1, new byte[] {0, 0}));
        });
    assertDoesNotThrow(
        () -> {
          services.writeMultipleRegisters(
              writeOnlyContext, 1, new WriteMultipleRegistersRequest(0, 1, new byte[] {0, 0}));
        });
    assertDoesNotThrow(
        () -> {
          services.writeMultipleRegisters(
              readWriteContext, 1, new WriteMultipleRegistersRequest(0, 1, new byte[] {0, 0}));
        });
  }

  @Test
  void testMaskWriteRegister() {
    assertThrows(
        ModbusException.class,
        () -> {
          services.maskWriteRegister(
              readOnlyContext, 1, new MaskWriteRegisterRequest(0, 0xFFFF, 0x0000));
        });
    assertDoesNotThrow(
        () -> {
          services.maskWriteRegister(
              writeOnlyContext, 1, new MaskWriteRegisterRequest(0, 0xFFFF, 0x0000));
        });
    assertDoesNotThrow(
        () -> {
          services.maskWriteRegister(
              readWriteContext, 1, new MaskWriteRegisterRequest(0, 0xFFFF, 0x0000));
        });
  }

  @Test
  void testReadWriteMultipleRegisters() {
    assertThrows(
        ModbusException.class,
        () -> {
          services.readWriteMultipleRegisters(
              readOnlyContext,
              1,
              new ReadWriteMultipleRegistersRequest(0, 1, 0, 1, new byte[] {0, 0}));
        });
    assertThrows(
        ModbusException.class,
        () -> {
          services.readWriteMultipleRegisters(
              writeOnlyContext,
              1,
              new ReadWriteMultipleRegistersRequest(0, 1, 0, 1, new byte[] {0, 0}));
        });
    assertDoesNotThrow(
        () -> {
          services.readWriteMultipleRegisters(
              readWriteContext,
              1,
              new ReadWriteMultipleRegistersRequest(0, 1, 0, 1, new byte[] {0, 0}));
        });
  }

  private static class TestModbusContext implements AuthzContext, ModbusTcpTlsRequestContext {

    private final String clientRole;

    private TestModbusContext(String clientRole) {
      this.clientRole = clientRole;
    }

    @Override
    public Optional<String> clientRole() {
      return Optional.of(clientRole);
    }

    @Override
    public X509Certificate[] clientCertificateChain() {
      return new X509Certificate[0];
    }

    @Override
    public SocketAddress localAddress() {
      return null;
    }

    @Override
    public SocketAddress remoteAddress() {
      return null;
    }
  }
}
