package com.digitalpetri.modbus.server.authz;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.digitalpetri.modbus.server.authz.AuthzHandler.AuthzResult;
import java.security.cert.X509Certificate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadWriteAuthzHandlerTest {

  private ReadWriteAuthzHandler authzHandler;
  private AuthzContext readOnlyAuthzContext;
  private AuthzContext writeOnlyAuthzContext;
  private AuthzContext readWriteAuthzContext;

  @BeforeEach
  void setUp() {
    authzHandler =
        new ReadWriteAuthzHandler() {
          @Override
          protected AuthzResult authorizeRead(int unitId, AuthzContext authzContext) {
            String role = authzContext.clientRole().orElseThrow();
            return role.equals("ReadOnly") || role.equals("ReadWrite")
                ? AuthzResult.AUTHORIZED
                : AuthzResult.NOT_AUTHORIZED;
          }

          @Override
          protected AuthzResult authorizeWrite(int unitId, AuthzContext authzContext) {
            String role = authzContext.clientRole().orElseThrow();
            return role.equals("WriteOnly") || role.equals("ReadWrite")
                ? AuthzResult.AUTHORIZED
                : AuthzResult.NOT_AUTHORIZED;
          }
        };

    readOnlyAuthzContext = new TestAuthzContext("ReadOnly");
    writeOnlyAuthzContext = new TestAuthzContext("WriteOnly");
    readWriteAuthzContext = new TestAuthzContext("ReadWrite");
  }

  @Test
  void testAuthorizeReadCoils() {
    AuthzResult result =
        authzHandler.authorizeReadCoils(readOnlyAuthzContext, 1, new ReadCoilsRequest(0, 1));
    assertEquals(AuthzResult.AUTHORIZED, result);

    result = authzHandler.authorizeReadCoils(writeOnlyAuthzContext, 1, new ReadCoilsRequest(0, 1));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result = authzHandler.authorizeReadCoils(readWriteAuthzContext, 1, new ReadCoilsRequest(0, 1));
    assertEquals(AuthzResult.AUTHORIZED, result);
  }

  @Test
  void testAuthorizeReadDiscreteInputs() {
    AuthzResult result =
        authzHandler.authorizeReadDiscreteInputs(
            readOnlyAuthzContext, 1, new ReadDiscreteInputsRequest(0, 1));
    assertEquals(AuthzResult.AUTHORIZED, result);

    result =
        authzHandler.authorizeReadDiscreteInputs(
            writeOnlyAuthzContext, 1, new ReadDiscreteInputsRequest(0, 1));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result =
        authzHandler.authorizeReadDiscreteInputs(
            readWriteAuthzContext, 1, new ReadDiscreteInputsRequest(0, 1));
    assertEquals(AuthzResult.AUTHORIZED, result);
  }

  @Test
  void testAuthorizeReadHoldingRegisters() {
    AuthzResult result =
        authzHandler.authorizeReadHoldingRegisters(
            readOnlyAuthzContext, 1, new ReadHoldingRegistersRequest(0, 1));
    assertEquals(AuthzResult.AUTHORIZED, result);

    result =
        authzHandler.authorizeReadHoldingRegisters(
            writeOnlyAuthzContext, 1, new ReadHoldingRegistersRequest(0, 1));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result =
        authzHandler.authorizeReadHoldingRegisters(
            readWriteAuthzContext, 1, new ReadHoldingRegistersRequest(0, 1));
    assertEquals(AuthzResult.AUTHORIZED, result);
  }

  @Test
  void testAuthorizeReadInputRegisters() {
    AuthzResult result =
        authzHandler.authorizeReadInputRegisters(
            readOnlyAuthzContext, 1, new ReadInputRegistersRequest(0, 1));
    assertEquals(AuthzResult.AUTHORIZED, result);

    result =
        authzHandler.authorizeReadInputRegisters(
            writeOnlyAuthzContext, 1, new ReadInputRegistersRequest(0, 1));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result =
        authzHandler.authorizeReadInputRegisters(
            readWriteAuthzContext, 1, new ReadInputRegistersRequest(0, 1));
    assertEquals(AuthzResult.AUTHORIZED, result);
  }

  @Test
  void testAuthorizeWriteSingleCoil() {
    AuthzResult result =
        authzHandler.authorizeWriteSingleCoil(
            readOnlyAuthzContext, 1, new WriteSingleCoilRequest(0, true));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result =
        authzHandler.authorizeWriteSingleCoil(
            writeOnlyAuthzContext, 1, new WriteSingleCoilRequest(0, true));
    assertEquals(AuthzResult.AUTHORIZED, result);

    result =
        authzHandler.authorizeWriteSingleCoil(
            readWriteAuthzContext, 1, new WriteSingleCoilRequest(0, true));
    assertEquals(AuthzResult.AUTHORIZED, result);
  }

  @Test
  void testAuthorizeWriteSingleRegister() {
    AuthzResult result =
        authzHandler.authorizeWriteSingleRegister(
            readOnlyAuthzContext, 1, new WriteSingleRegisterRequest(0, (short) 1));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result =
        authzHandler.authorizeWriteSingleRegister(
            writeOnlyAuthzContext, 1, new WriteSingleRegisterRequest(0, (short) 1));
    assertEquals(AuthzResult.AUTHORIZED, result);

    result =
        authzHandler.authorizeWriteSingleRegister(
            readWriteAuthzContext, 1, new WriteSingleRegisterRequest(0, (short) 1));
    assertEquals(AuthzResult.AUTHORIZED, result);
  }

  @Test
  void testAuthorizeWriteMultipleCoils() {
    AuthzResult result =
        authzHandler.authorizeWriteMultipleCoils(
            readOnlyAuthzContext, 1, new WriteMultipleCoilsRequest(0, 1, new byte[] {1}));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result =
        authzHandler.authorizeWriteMultipleCoils(
            writeOnlyAuthzContext, 1, new WriteMultipleCoilsRequest(0, 1, new byte[] {1}));
    assertEquals(AuthzResult.AUTHORIZED, result);

    result =
        authzHandler.authorizeWriteMultipleCoils(
            readWriteAuthzContext, 1, new WriteMultipleCoilsRequest(0, 1, new byte[] {1}));
    assertEquals(AuthzResult.AUTHORIZED, result);
  }

  @Test
  void testAuthorizeWriteMultipleRegisters() {
    AuthzResult result =
        authzHandler.authorizeWriteMultipleRegisters(
            readOnlyAuthzContext, 1, new WriteMultipleRegistersRequest(0, 1, new byte[] {1, 2}));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result =
        authzHandler.authorizeWriteMultipleRegisters(
            writeOnlyAuthzContext, 1, new WriteMultipleRegistersRequest(0, 1, new byte[] {1, 2}));
    assertEquals(AuthzResult.AUTHORIZED, result);

    result =
        authzHandler.authorizeWriteMultipleRegisters(
            readWriteAuthzContext, 1, new WriteMultipleRegistersRequest(0, 1, new byte[] {1, 2}));
    assertEquals(AuthzResult.AUTHORIZED, result);
  }

  @Test
  void testAuthorizeMaskWriteRegister() {
    AuthzResult result =
        authzHandler.authorizeMaskWriteRegister(
            readOnlyAuthzContext, 1, new MaskWriteRegisterRequest(0, (short) 1, (short) 1));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result =
        authzHandler.authorizeMaskWriteRegister(
            writeOnlyAuthzContext, 1, new MaskWriteRegisterRequest(0, (short) 1, (short) 1));
    assertEquals(AuthzResult.AUTHORIZED, result);

    result =
        authzHandler.authorizeMaskWriteRegister(
            readWriteAuthzContext, 1, new MaskWriteRegisterRequest(0, (short) 1, (short) 1));
    assertEquals(AuthzResult.AUTHORIZED, result);
  }

  @Test
  void testAuthorizeReadWriteMultipleRegisters() {
    AuthzResult result =
        authzHandler.authorizeReadWriteMultipleRegisters(
            readOnlyAuthzContext,
            1,
            new ReadWriteMultipleRegistersRequest(0, 1, 0, 1, new byte[] {1, 2}));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result =
        authzHandler.authorizeReadWriteMultipleRegisters(
            writeOnlyAuthzContext,
            1,
            new ReadWriteMultipleRegistersRequest(0, 1, 0, 1, new byte[] {1, 2}));
    assertEquals(AuthzResult.NOT_AUTHORIZED, result);

    result =
        authzHandler.authorizeReadWriteMultipleRegisters(
            readWriteAuthzContext,
            1,
            new ReadWriteMultipleRegistersRequest(0, 1, 0, 1, new byte[] {1, 2}));
    assertEquals(AuthzResult.AUTHORIZED, result);
  }

  record TestAuthzContext(Optional<String> clientRole, X509Certificate[] clientCertificateChain)
      implements AuthzContext {
    TestAuthzContext(String clientRole) {
      this(Optional.of(clientRole), new X509Certificate[0]);
    }
  }
}
