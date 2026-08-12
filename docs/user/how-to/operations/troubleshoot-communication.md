# Troubleshoot communication

Use this guide when a connection, request, response, or decoded value does not behave as expected.
Work from the transport upward; changing addresses or byte order cannot repair a broken serial
link, and changing a timeout cannot repair a wrong unit ID.

## Prerequisites

Collect the transport type, endpoint or serial settings, unit ID, function code, zero-based
address, quantity, expected response time, exception type and cause, and any device-side event log.
Use a known-good request from the device manual where possible.

## 1. Confirm the selected transport and endpoint

- For Modbus TCP, use `ModbusTcpClient` with `NettyTcpClientTransport`.
- For serial RTU, use `ModbusRtuClient` with `SerialPortClientTransport`.
- For RTU over TCP, use `ModbusRtuClient` with `NettyRtuClientTransport`; it is not interchangeable
  with Modbus TCP because the framing differs.
- When TLS is enabled, confirm both peers expect TLS on that port and both have key and trust
  material.

For TCP, verify routing and port reachability independently. For serial, verify the exact OS port,
exclusive access, wiring, and adapter driver.

## 2. Match framing and line settings

For a serial link, compare baud rate, data bits, parity, stop bits, and RS-485 direction control on
both ends. Repeated CRC errors normally indicate corrupted or misframed bytes rather than an
application address problem.

For a TCP link, determine whether the gateway expects a seven-byte MBAP header (Modbus TCP) or an
RTU unit/PDU/CRC frame carried as a byte stream (RTU over TCP). A socket connection can succeed
while every request times out if this choice is wrong.

## 3. Verify unit ID and address translation

Pass the on-wire unit ID to the client. For serial RTU, unit ID 0 is the broadcast address and does
not return a response; use `ModbusRtuClient.broadcast(...)` only for deliberate writes.

Pass the raw zero-based PDU offset to request constructors. A manual label such as holding
register `40001` commonly maps to address 0, not integer 40001. Confirm the vendor's convention
rather than relying on the prefix alone.

## 4. Reduce the request

Use one typed read for one item in a documented data area, for example:

```java
var response =
    client.readHoldingRegisters(unitId, new ReadHoldingRegistersRequest(address, 1));
```

Do not begin with a maximum-length range, concurrent RTU requests, a vendor function, or a
multi-register value conversion. Once one register succeeds, expand one variable at a time.

## 5. Classify the exception

| Observation | Meaning and next check |
| --- | --- |
| `ModbusConnectException` or a cause wrapped by `ModbusExecutionException` | The transport could not connect/open; inspect the root cause, endpoint, permissions, and TLS handshake |
| `ModbusTimeoutException` | No matching response arrived before the request deadline; check unit, framing, server load, and timeout budget |
| `ModbusResponseException` | The server returned a Modbus exception; TCP exposes both numeric fields, while RTU is subject to the [RTU exception-code limitation](../../reference/errors-and-exceptions.md#rtu-exception-code-limitation) |
| `ModbusExecutionException` caused by `ModbusCrcException` | A synchronous RTU call received a frame with the wrong CRC; inspect physical/link settings and interference |
| Unit/function mismatch (reported as `slave id mismatch` / `function code mismatch`) or synchronization error | RTU response order was lost, often after a late response or concurrent requests |
| Successful response with unexpected bytes | Communication works; move to byte/word order, signedness, scale, and data-map diagnosis |

The synchronous client wraps unexpected transport, serialization, and correlation failures in
`ModbusExecutionException`. Inspect `getCause()` rather than logging only the outer message. The
asynchronous RTU stage exposes `ModbusCrcException` directly.

## 6. Enable focused logging

The library uses SLF4J. Configure your application's SLF4J provider to enable debug logging for
`com.digitalpetri.modbus` and, for Modbus TCP connection state, `com.digitalpetri.modbus.client.ChannelFsm`.
That logger name applies to `NettyTcpClientTransport` only; the RTU-over-TCP client transport logs
connection-state transitions under the netty-channel-fsm dependency's default logger
(`com.digitalpetri.netty.fsm.ChannelFsm`). Capture the exception chain and connection transitions,
but treat raw industrial data and certificate details as potentially sensitive.

## 7. Verify cleanup before retrying

Disconnect a failed client before replacing it. Stop a server before rebinding the same port. For
serial, make sure the previous process actually closed the port. Do not release process-wide
shared resources merely to retry one connection; release them at final application shutdown.

## Verify the diagnosis

Record the smallest request that reproduces the problem, the layer at which it fails, and the one
change that makes it pass. Re-run the original request only after the reduced case works. For
intermittent faults, repeat long enough to cover the device's slowest documented response and the
TCP reconnection backoff.

## Common symptom index

| Symptom | Most likely layer | Next page |
| --- | --- | --- |
| Cannot open serial port | Resource/OS | [Serial RTU guide](../clients/communicate-over-serial-rtu.md) |
| TCP connects, all requests time out | Framing, TLS, or unit ID | [Transports and framing](../../concepts/transports-framing-and-security.md) |
| Illegal data address | Address map/range | [Addressing and data](../../concepts/addressing-unit-ids-and-data.md) |
| Wrong numeric value from stable bytes | Data representation | [Addressing and data](../../concepts/addressing-unit-ids-and-data.md#register-bytes-and-multi-register-values) |
| Repeated reconnect transitions | Network/endpoint availability | [Timeouts and reconnection](configure-timeouts-and-reconnection.md) |
| Server ignores one unit | Service routing | [Client and server behavior](../../reference/client-and-server-behavior.md#unknown-unit-ids) |

## Related reference

- [Errors and exceptions](../../reference/errors-and-exceptions.md)
- [Transport configuration](../../reference/transport-configuration.md)
- [Function codes and PDUs](../../reference/function-codes-and-pdus.md)
