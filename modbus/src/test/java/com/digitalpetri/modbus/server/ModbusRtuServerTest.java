package com.digitalpetri.modbus.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.ModbusPduSerializer;
import com.digitalpetri.modbus.ModbusPduSerializer.DefaultRequestSerializer;
import com.digitalpetri.modbus.ModbusRtuFrame;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.UnknownUnitIdException;
import com.digitalpetri.modbus.pdu.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.pdu.ModbusPdu;
import com.digitalpetri.modbus.pdu.ModbusRequestPdu;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilResponse;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterResponse;
import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusRtuRequestContext;
import com.digitalpetri.modbus.server.authz.AuthzContext;
import com.digitalpetri.modbus.server.authz.AuthzModbusServices;
import com.digitalpetri.modbus.server.authz.ReadWriteAuthzHandler;
import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class ModbusRtuServerTest {

  @Test
  void broadcastWriteUsesUnitZeroWithoutResponding() throws Exception {
    var image = new ProcessImage();
    var services =
        new ReadWriteModbusServices() {
          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            assertEquals(0, unitId);
            return Optional.of(image);
          }
        };
    var transport = start(services);

    assertNull(transport.handle(0, new WriteSingleRegisterRequest(10, 0x1234)));

    assertArrayEquals(new byte[] {0x12, 0x34}, register(image, 10));
  }

  @Test
  void supportedBroadcastWritesReachOnlySelectedRecipientsWithoutEncodingResponses()
      throws Exception {
    Map<Integer, ProcessImage> images =
        Map.of(1, new ProcessImage(), 2, new ProcessImage(), 3, new ProcessImage());
    var transport = new TestTransport();
    var services =
        new ReadWriteModbusServices() {
          @Override
          public Iterable<Integer> getBroadcastUnitIds(ModbusRtuRequestContext context) {
            assertSame(transport.context, context);
            return List.of(1, 3);
          }

          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            return Optional.ofNullable(images.get(unitId));
          }
        };
    var config =
        ModbusServerConfig.create(
            b ->
                b.setResponseSerializer(
                    new ModbusPduSerializer() {
                      @Override
                      public void encode(ModbusPdu pdu, ByteBuffer buffer) {
                        throw new AssertionError("broadcast responses must not be encoded");
                      }

                      @Override
                      public ModbusPdu decode(int functionCode, ByteBuffer buffer) {
                        throw new UnsupportedOperationException();
                      }
                    }));
    new ModbusRtuServer(config, transport, services).start();

    List<ModbusRequestPdu> writes =
        List.of(
            new WriteSingleCoilRequest(10, true),
            new WriteMultipleCoilsRequest(11, 2, new byte[] {3}),
            new WriteSingleRegisterRequest(10, 0x1234),
            new WriteMultipleRegistersRequest(11, 2, new byte[] {0x56, 0x78, 0x12, 0x34}),
            new MaskWriteRegisterRequest(10, 0xFF00, 0x0056));
    for (ModbusRequestPdu write : writes) {
      assertNull(transport.handle(0, write));
    }

    for (int unitId : List.of(1, 3)) {
      ProcessImage image = images.get(unitId);
      assertTrue(image.<Boolean>get(tx -> tx.readCoils(coils -> coils.get(10))));
      assertTrue(image.<Boolean>get(tx -> tx.readCoils(coils -> coils.get(11))));
      assertTrue(image.<Boolean>get(tx -> tx.readCoils(coils -> coils.get(12))));
      assertArrayEquals(new byte[] {0x12, 0x56}, register(image, 10));
      assertArrayEquals(new byte[] {0x56, 0x78}, register(image, 11));
      assertArrayEquals(new byte[] {0x12, 0x34}, register(image, 12));
    }
    assertTrue(images.get(2).<Boolean>get(tx -> tx.readCoils(Map::isEmpty)));
    assertTrue(images.get(2).<Boolean>get(tx -> tx.readHoldingRegisters(Map::isEmpty)));
  }

  @Test
  void rejectedRecipientsDoNotPreventLaterRecipientsOrUnicastResponses() throws Exception {
    List<Integer> calls = new ArrayList<>();
    var services =
        new ModbusServices() {
          @Override
          public Iterable<Integer> getBroadcastUnitIds(ModbusRtuRequestContext context) {
            return List.of(1, 2, 3);
          }

          @Override
          public WriteSingleRegisterResponse writeSingleRegister(
              ModbusRequestContext context, int unitId, WriteSingleRegisterRequest request)
              throws ModbusResponseException, UnknownUnitIdException {
            calls.add(unitId);
            if (unitId == 1) {
              throw new ModbusResponseException(
                  request.getFunctionCode(), ExceptionCode.ILLEGAL_DATA_ADDRESS.getCode());
            }
            if (unitId == 2) {
              throw new UnknownUnitIdException(unitId);
            }
            return new WriteSingleRegisterResponse(request.address(), request.value());
          }
        };
    var transport = start(services);
    var request = new WriteSingleRegisterRequest(10, 0x1234);

    assertNull(transport.handle(0, request));
    assertEquals(List.of(1, 2, 3), calls);

    ModbusRtuFrame response = transport.handle(3, request);
    assertEquals(3, response.unitId());
    assertArrayEquals(new byte[] {6, 0, 10, 0x12, 0x34}, bytes(response.pdu()));
    assertArrayEquals(new byte[] {(byte) 0x86, 2}, bytes(transport.handle(1, request).pdu()));
    assertThrows(UnknownUnitIdException.class, () -> transport.handle(2, request));
  }

  @Test
  void emptyRecipientsIgnoreBroadcastWrites() throws Exception {
    var services =
        new ModbusServices() {
          @Override
          public Iterable<Integer> getBroadcastUnitIds(ModbusRtuRequestContext context) {
            return List.of();
          }

          @Override
          public WriteSingleRegisterResponse writeSingleRegister(
              ModbusRequestContext context, int unitId, WriteSingleRegisterRequest request) {
            throw new AssertionError("no recipients selected");
          }
        };
    assertNull(start(services).handle(0, new WriteSingleRegisterRequest(10, 0x1234)));
  }

  @Test
  void readAndUnsupportedBroadcastsAreIgnoredBeforeDecodingOrSelectingRecipients()
      throws Exception {
    var services =
        new ModbusServices() {
          @Override
          public Iterable<Integer> getBroadcastUnitIds(ModbusRtuRequestContext context) {
            throw new AssertionError("read broadcasts must not select recipients");
          }
        };
    var transport = start(services);
    // A function byte alone cannot decode as a request. Ignored broadcasts never reach decoding.
    for (int function : List.of(1, 2, 3, 4, 0x17, 0x5A)) {
      assertNull(transport.handle(0, new byte[] {(byte) function}));
    }
  }

  @Test
  void invalidBroadcastWriteReachesServicesForRejection() throws Exception {
    List<Integer> values = new ArrayList<>();
    var services =
        new ModbusServices() {
          @Override
          public WriteSingleCoilResponse writeSingleCoil(
              ModbusRequestContext context, int unitId, WriteSingleCoilRequest request)
              throws ModbusResponseException {
            values.add(request.value());
            throw new ModbusResponseException(
                request.getFunctionCode(), ExceptionCode.ILLEGAL_DATA_VALUE.getCode());
          }
        };

    assertNull(start(services).handle(0, new byte[] {5, 0, 10, 0x12, 0x34}));
    assertEquals(List.of(0x1234), values);
  }

  @Test
  void authorizationDecoratorPreservesSelectionAndChecksEachRecipient() throws Exception {
    var image = new ProcessImage();
    List<Integer> authorizedUnits = new ArrayList<>();
    var transport = new TestTransport(new AuthorizationContext());
    var delegate =
        new ReadWriteModbusServices() {
          @Override
          public Iterable<Integer> getBroadcastUnitIds(ModbusRtuRequestContext context) {
            assertSame(transport.context, context);
            return List.of(6, 7);
          }

          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            assertEquals(7, unitId);
            return Optional.of(image);
          }
        };
    var authzHandler =
        new ReadWriteAuthzHandler() {
          @Override
          protected AuthzResult authorizeRead(int unitId, AuthzContext context) {
            return AuthzResult.AUTHORIZED;
          }

          @Override
          protected AuthzResult authorizeWrite(int unitId, AuthzContext context) {
            authorizedUnits.add(unitId);
            return unitId == 7 ? AuthzResult.AUTHORIZED : AuthzResult.NOT_AUTHORIZED;
          }
        };
    var services = new AuthzModbusServices(authzHandler, delegate);
    ModbusRtuServer.create(transport, services).start();

    assertNull(transport.handle(0, new WriteSingleRegisterRequest(10, 0x1234)));
    assertEquals(List.of(6, 7), authorizedUnits);
    assertArrayEquals(new byte[] {0x12, 0x34}, register(image, 10));
  }

  private static class AuthorizationContext implements ModbusRtuRequestContext, AuthzContext {

    @Override
    public Optional<String> clientRole() {
      return Optional.empty();
    }

    @Override
    public X509Certificate[] clientCertificateChain() {
      return new X509Certificate[0];
    }
  }

  private static byte[] register(ProcessImage image, int address) {
    return image.get(tx -> tx.readHoldingRegisters(registers -> registers.get(address)));
  }

  private static byte[] bytes(ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.duplicate().get(bytes);
    return bytes;
  }

  private static TestTransport start(ModbusServices services) throws Exception {
    var transport = new TestTransport();
    ModbusRtuServer.create(transport, services).start();
    return transport;
  }

  private static class TestTransport implements ModbusRtuServerTransport {

    final ModbusRtuRequestContext context;

    TestTransport() {
      this(new ModbusRtuRequestContext() {});
    }

    TestTransport(ModbusRtuRequestContext context) {
      this.context = context;
    }

    FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame> receiver;

    ModbusRtuFrame handle(int unitId, ModbusRequestPdu request) throws Exception {
      ByteBuffer pdu = ByteBuffer.allocate(256);
      DefaultRequestSerializer.INSTANCE.encode(request, pdu);
      return receiver.receive(
          context, new ModbusRtuFrame(unitId, pdu.flip(), ByteBuffer.allocate(2)));
    }

    ModbusRtuFrame handle(int unitId, byte[] pdu) throws Exception {
      return receiver.receive(
          context, new ModbusRtuFrame(unitId, ByteBuffer.wrap(pdu), ByteBuffer.allocate(2)));
    }

    @Override
    public CompletionStage<Void> bind() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> unbind() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void receive(FrameReceiver<ModbusRtuRequestContext, ModbusRtuFrame> receiver) {
      this.receiver = receiver;
    }
  }
}
