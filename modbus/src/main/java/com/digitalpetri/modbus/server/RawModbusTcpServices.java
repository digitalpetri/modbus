package com.digitalpetri.modbus.server;

import com.digitalpetri.modbus.server.ModbusRequestContext.ModbusTcpRequestContext;
import java.util.Optional;

/**
 * TCP-only raw Modbus service extension.
 *
 * <p>Implement this interface when a server needs to inspect or handle raw Modbus/TCP PDUs before
 * the standard-typed request decoder runs. Returning {@link Optional#empty()} declines the request
 * and lets the server continue with the existing typed Modbus path. Returning a response means the
 * request was handled and no typed decoding should occur.
 *
 * <p>Exceptions are propagated to the existing transport error handling path. Throwing {@link
 * com.digitalpetri.modbus.exceptions.UnknownUnitIdException} preserves existing TCP behavior: the
 * request is ignored.
 */
@FunctionalInterface
public interface RawModbusTcpServices extends ModbusServices {

  /**
   * Handle an incoming raw Modbus/TCP request.
   *
   * @param context the TCP request context.
   * @param request the raw request.
   * @return a raw response if this service handled the request, or {@link Optional#empty()} to
   *     continue with typed Modbus request decoding.
   * @throws Exception if there is an error handling the raw request.
   */
  Optional<RawModbusTcpResponse> handleRawTcpRequest(
      ModbusTcpRequestContext context, RawModbusTcpRequest request) throws Exception;
}
