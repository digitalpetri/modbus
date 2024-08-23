package com.digitalpetri.modbus.client;

import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.ModbusTimeoutException;
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
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleCoilsResponse;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteMultipleRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteSingleCoilRequest;
import com.digitalpetri.modbus.pdu.WriteSingleCoilResponse;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterResponse;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public abstract class ModbusClient {

  private final ModbusClientTransport<?> transport;

  ModbusClient(ModbusClientTransport<?> transport) {
    this.transport = transport;
  }

  /**
   * Connect the underlying transport.
   *
   * @throws ModbusExecutionException if the connection fails.
   */
  public void connect() throws ModbusExecutionException {
    try {
      transport.connect().toCompletableFuture().get();
    } catch (ExecutionException e) {
      throw new ModbusExecutionException(e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ModbusExecutionException(e);
    }
  }

  /**
   * Connect the underlying transport asynchronously.
   *
   * @return a {@link CompletionStage} that completes successfully when the connection is
   *     established, or completes exceptionally if the connection fails.
   */
  public CompletionStage<Void> connectAsync() {
    return transport.connect();
  }

  /**
   * Alias for {@link #connect()} that returns {@link ModbusClientAutoCloseable} for use in
   * try-with-resources blocks.
   *
   * @return a {@link ModbusClientAutoCloseable} instance that disconnects this client when closed.
   * @throws ModbusExecutionException if the disconnection fails.
   */
  public ModbusClientAutoCloseable open() throws ModbusExecutionException {
    connect();
    return this::disconnect;
  }

  /**
   * Disconnect the underlying transport.
   *
   * @throws ModbusExecutionException if the disconnection fails.
   */
  public void disconnect() throws ModbusExecutionException {
    try {
      transport.disconnect().toCompletableFuture().get();
    } catch (ExecutionException e) {
      throw new ModbusExecutionException(e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ModbusExecutionException(e);
    }
  }

  /**
   * Disconnect the underlying transport asynchronously.
   *
   * @return a {@link CompletionStage} that completes successfully when the connection is closed, or
   *     completes exceptionally if the disconnection fails.
   */
  public CompletionStage<Void> disconnectAsync() {
    return transport.disconnect();
  }

  /**
   * Check if this client is connected i.e. the underlying transport is connected.
   *
   * @return {@code true} if this client is connected.
   */
  public boolean isConnected() {
    return transport.isConnected();
  }

  /**
   * Send a {@link ModbusRequestPdu} PDU to the remote device identified by {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the request PDU.
   * @return the {@link ModbusResponsePdu} PDU.
   * @throws ModbusExecutionException if any unexpected execution error occurs.
   * @throws ModbusResponseException if the remote device responds with an error.
   * @throws ModbusTimeoutException if the request times out.
   */
  public ModbusResponsePdu send(
      int unitId,
      ModbusRequestPdu request
  ) throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    try {
      return sendAsync(unitId, request).toCompletableFuture().get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof TimeoutException ex) {
        throw new ModbusTimeoutException(ex);
      } else if (cause instanceof ModbusResponseException ex) {
        throw ex;
      } else {
        throw new ModbusExecutionException(cause);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ModbusExecutionException(e);
    }
  }

  /**
   * Send a {@link ModbusRequestPdu} PDU to the remote device identified by {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the request PDU.
   * @return a {@link CompletionStage} that completes successfully with the
   *     {@link ModbusResponsePdu} PDU, or completes exceptionally if an error occurs.
   */
  public abstract CompletionStage<ModbusResponsePdu> sendAsync(
      int unitId,
      ModbusRequestPdu request
  );

  //region Read Coils (function code 0x01)

  /**
   * Send a {@link ReadCoilsRequest} (FC 0x01) to the remote device identified by {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link ReadCoilsRequest}.
   * @return the {@link ReadCoilsResponse}.
   * @throws ModbusExecutionException if any unexpected execution error occurs.
   * @throws ModbusResponseException if the remote device responds with an error.
   * @throws ModbusTimeoutException if the request times out.
   */
  public ReadCoilsResponse readCoils(
      int unitId,
      ReadCoilsRequest request
  ) throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    ModbusResponsePdu response = send(unitId, request);

    if (response instanceof ReadCoilsResponse r) {
      return r;
    } else {
      throw new ModbusExecutionException(
          "unexpected response: 0x%02X"
              .formatted(response.getFunctionCode())
      );
    }
  }

  /**
   * Send a {@link ReadCoilsRequest} (FC 0x01) to the remote device identified by {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link ReadCoilsRequest}.
   * @return a {@link CompletionStage} that completes successfully with the
   *     {@link ReadCoilsResponse}, or completes exceptionally if an error occurs.
   */
  public CompletionStage<ReadCoilsResponse> readCoilsAsync(
      int unitId,
      ReadCoilsRequest request
  ) {

    return sendAsync(unitId, request)
        .thenApply(ReadCoilsResponse.class::cast);
  }

  //endregion

  //region Read Discrete Inputs (function code 0x02)

  /**
   * Send a {@link ReadDiscreteInputsRequest} (FC 0x02) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link ReadDiscreteInputsRequest}.
   * @return the {@link ReadDiscreteInputsResponse}.
   * @throws ModbusExecutionException if any unexpected execution error occurs.
   * @throws ModbusResponseException if the remote device responds with an error.
   * @throws ModbusTimeoutException if the request times out.
   */
  public ReadDiscreteInputsResponse readDiscreteInputs(
      int unitId,
      ReadDiscreteInputsRequest request
  ) throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    ModbusResponsePdu response = send(unitId, request);

    if (response instanceof ReadDiscreteInputsResponse r) {
      return r;
    } else {
      throw new ModbusExecutionException(
          "unexpected response: 0x%02X"
              .formatted(response.getFunctionCode())
      );
    }
  }

  /**
   * Send a {@link ReadDiscreteInputsRequest} (FC 0x02) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link ReadDiscreteInputsRequest}.
   * @return a {@link CompletionStage} that completes successfully with the
   *     {@link ReadDiscreteInputsResponse}, or completes exceptionally if an error occurs.
   */
  public CompletionStage<ReadDiscreteInputsResponse> readDiscreteInputsAsync(
      int unitId,
      ReadDiscreteInputsRequest request
  ) {

    return sendAsync(unitId, request)
        .thenApply(ReadDiscreteInputsResponse.class::cast);
  }

  //endregion

  //region Read Holding Registers (function code 0x03)

  /**
   * Send a {@link ReadHoldingRegistersRequest} (FC 0x03) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link ReadHoldingRegistersRequest}.
   * @return the {@link ReadHoldingRegistersResponse}.
   * @throws ModbusExecutionException if any unexpected execution error occurs.
   * @throws ModbusResponseException if the remote device responds with an error.
   * @throws ModbusTimeoutException if the request times out.
   */
  public ReadHoldingRegistersResponse readHoldingRegisters(
      int unitId,
      ReadHoldingRegistersRequest request
  ) throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    ModbusResponsePdu response = send(unitId, request);

    if (response instanceof ReadHoldingRegistersResponse r) {
      return r;
    } else {
      throw new ModbusExecutionException(
          "unexpected response: 0x%02X"
              .formatted(response.getFunctionCode())
      );
    }
  }

  /**
   * Send a {@link ReadHoldingRegistersRequest} (FC 0x03) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link ReadHoldingRegistersRequest}.
   * @return a {@link CompletionStage} that completes successfully with the
   *     {@link ReadHoldingRegistersResponse}, or completes exceptionally if an error occurs.
   */
  public CompletionStage<ReadHoldingRegistersResponse> readHoldingRegistersAsync(
      int unitId,
      ReadHoldingRegistersRequest request
  ) {

    return sendAsync(unitId, request)
        .thenApply(ReadHoldingRegistersResponse.class::cast);
  }

  //endregion

  //region Read Input Registers (function code 0x04)

  /**
   * Send a {@link ReadInputRegistersRequest} (FC 0x04) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link ReadInputRegistersRequest}.
   * @return the {@link ReadInputRegistersResponse}.
   * @throws ModbusExecutionException if any unexpected execution error occurs.
   * @throws ModbusResponseException if the remote device responds with an error.
   * @throws ModbusTimeoutException if the request times out.
   */
  public ReadInputRegistersResponse readInputRegisters(
      int unitId,
      ReadInputRegistersRequest request
  ) throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    ModbusResponsePdu response = send(unitId, request);

    if (response instanceof ReadInputRegistersResponse r) {
      return r;
    } else {
      throw new ModbusExecutionException(
          "unexpected response: 0x%02X"
              .formatted(response.getFunctionCode())
      );
    }
  }

  /**
   * Send a {@link ReadInputRegistersRequest} (FC 0x04) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link ReadInputRegistersRequest}.
   * @return a {@link CompletionStage} that completes successfully with the
   *     {@link ReadInputRegistersResponse}, or completes exceptionally if an error occurs.
   */
  public CompletionStage<ReadInputRegistersResponse> readInputRegistersAsync(
      int unitId,
      ReadInputRegistersRequest request
  ) {

    return sendAsync(unitId, request)
        .thenApply(ReadInputRegistersResponse.class::cast);
  }

  //endregion

  //region Write Single Coil (0x05)

  /**
   * Send a {@link WriteSingleCoilRequest} (FC 0x05) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link WriteSingleCoilRequest}.
   * @return the {@link WriteSingleCoilResponse}.
   * @throws ModbusExecutionException if any unexpected execution error occurs.
   * @throws ModbusResponseException if the remote device responds with an error.
   * @throws ModbusTimeoutException if the request times out.
   */
  public WriteSingleCoilResponse writeSingleCoil(
      int unitId,
      WriteSingleCoilRequest request
  ) throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    ModbusResponsePdu response = send(unitId, request);

    if (response instanceof WriteSingleCoilResponse r) {
      return r;
    } else {
      throw new ModbusExecutionException(
          "unexpected response: 0x%02X"
              .formatted(response.getFunctionCode())
      );
    }
  }

  /**
   * Send a {@link WriteSingleCoilRequest} (FC 0x05) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link WriteSingleCoilRequest}.
   * @return a {@link CompletionStage} that completes successfully with the
   *     {@link WriteSingleCoilResponse}, or completes exceptionally if an error occurs.
   */
  public CompletionStage<WriteSingleCoilResponse> writeSingleCoilAsync(
      int unitId,
      WriteSingleCoilRequest request
  ) {

    return sendAsync(unitId, request)
        .thenApply(WriteSingleCoilResponse.class::cast);
  }

  //endregion

  //region Write Single Register (0x06)

  /**
   * Send a {@link WriteSingleRegisterRequest} (FC 0x06) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link WriteSingleRegisterRequest}.
   * @return the {@link WriteSingleRegisterResponse}.
   * @throws ModbusExecutionException if any unexpected execution error occurs.
   * @throws ModbusResponseException if the remote device responds with an error.
   * @throws ModbusTimeoutException if the request times out.
   */
  public WriteSingleRegisterResponse writeSingleRegister(
      int unitId,
      WriteSingleRegisterRequest request
  ) throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    ModbusResponsePdu response = send(unitId, request);

    if (response instanceof WriteSingleRegisterResponse r) {
      return r;
    } else {
      throw new ModbusExecutionException(
          "unexpected response: 0x%02X"
              .formatted(response.getFunctionCode())
      );
    }
  }

  /**
   * Send a {@link WriteSingleRegisterRequest} (FC 0x06) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link WriteSingleRegisterRequest}.
   * @return a {@link CompletionStage} that completes successfully with the
   *     {@link WriteSingleRegisterResponse}, or completes exceptionally if an error occurs.
   */
  public CompletionStage<WriteSingleRegisterResponse> writeSingleRegisterAsync(
      int unitId,
      WriteSingleRegisterRequest request
  ) {

    return sendAsync(unitId, request)
        .thenApply(WriteSingleRegisterResponse.class::cast);
  }

  //endregion

  //region Write Multiple Coils (0x0F)

  /**
   * Send a {@link WriteMultipleCoilsRequest} (FC 0x0F) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link WriteMultipleCoilsRequest}.
   * @return the {@link WriteMultipleCoilsResponse}.
   * @throws ModbusExecutionException if any unexpected execution error occurs.
   * @throws ModbusResponseException if the remote device responds with an error.
   * @throws ModbusTimeoutException if the request times out.
   */
  public WriteMultipleCoilsResponse writeMultipleCoils(
      int unitId,
      WriteMultipleCoilsRequest request
  ) throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    ModbusResponsePdu response = send(unitId, request);

    if (response instanceof WriteMultipleCoilsResponse r) {
      return r;
    } else {
      throw new ModbusExecutionException(
          "unexpected response: 0x%02X"
              .formatted(response.getFunctionCode())
      );
    }
  }

  /**
   * Send a {@link WriteMultipleCoilsRequest} (FC 0x0F) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link WriteMultipleCoilsRequest}.
   * @return a {@link CompletionStage} that completes successfully with the
   *     {@link WriteMultipleCoilsResponse}, or completes exceptionally if an error occurs.
   */
  public CompletionStage<WriteMultipleCoilsResponse> writeMultipleCoilsAsync(
      int unitId,
      WriteMultipleCoilsRequest request
  ) {

    return sendAsync(unitId, request)
        .thenApply(WriteMultipleCoilsResponse.class::cast);
  }

  //endregion

  //region Write Multiple Registers (0x10)

  /**
   * Send a {@link WriteMultipleRegistersRequest} (FC 0x10) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link WriteMultipleRegistersRequest}.
   * @return the {@link WriteMultipleRegistersResponse}.
   * @throws ModbusExecutionException if any unexpected execution error occurs.
   * @throws ModbusResponseException if the remote device responds with an error.
   * @throws ModbusTimeoutException if the request times out.
   */
  public WriteMultipleRegistersResponse writeMultipleRegisters(
      int unitId,
      WriteMultipleRegistersRequest request
  ) throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    ModbusResponsePdu response = send(unitId, request);

    if (response instanceof WriteMultipleRegistersResponse r) {
      return r;
    } else {
      throw new ModbusExecutionException(
          "unexpected response: 0x%02X"
              .formatted(response.getFunctionCode())
      );
    }
  }

  /**
   * Send a {@link WriteMultipleRegistersRequest} (FC 0x10) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link WriteMultipleRegistersRequest}.
   * @return a {@link CompletionStage} that completes successfully with the
   *     {@link WriteMultipleRegistersResponse}, or completes exceptionally if an error occurs.
   */
  public CompletionStage<WriteMultipleRegistersResponse> writeMultipleRegistersAsync(
      int unitId,
      WriteMultipleRegistersRequest request
  ) {

    return sendAsync(unitId, request)
        .thenApply(WriteMultipleRegistersResponse.class::cast);
  }

  //endregion

  //region Mask Write Register (0x16)

  /**
   * Send a {@link MaskWriteRegisterRequest} (FC 0x16) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link MaskWriteRegisterRequest}.
   * @return the {@link MaskWriteRegisterResponse}.
   * @throws ModbusExecutionException if any unexpected execution error occurs.
   * @throws ModbusResponseException if the remote device responds with an error.
   * @throws ModbusTimeoutException if the request times out.
   */
  public MaskWriteRegisterResponse maskWriteRegister(
      int unitId,
      MaskWriteRegisterRequest request
  ) throws ModbusExecutionException, ModbusResponseException, ModbusTimeoutException {

    ModbusResponsePdu response = send(unitId, request);

    if (response instanceof MaskWriteRegisterResponse r) {
      return r;
    } else {
      throw new ModbusExecutionException(
          "unexpected response: 0x%02X"
              .formatted(response.getFunctionCode())
      );
    }
  }

  /**
   * Send a {@link MaskWriteRegisterRequest} (FC 0x16) to the remote device identified by
   * {@code unitId}.
   *
   * @param unitId the remote device unit id.
   * @param request the {@link MaskWriteRegisterRequest}.
   * @return a {@link CompletionStage} that completes successfully with the
   *     {@link MaskWriteRegisterResponse}, or completes exceptionally if an error occurs.
   */
  public CompletionStage<MaskWriteRegisterResponse> maskWriteRegisterAsync(
      int unitId,
      MaskWriteRegisterRequest request
  ) {

    return sendAsync(unitId, request)
        .thenApply(MaskWriteRegisterResponse.class::cast);
  }

  //endregion

  public interface ModbusClientAutoCloseable extends AutoCloseable {

    @Override
    void close() throws ModbusExecutionException;
  }

}
