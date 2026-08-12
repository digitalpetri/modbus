# Errors and exceptions

## Exception hierarchy

All library exceptions below extend the checked `ModbusException`.

| Exception | Produced by | Information |
| --- | --- | --- |
| `ModbusConnectException` | Supplied serial transports when opening the port fails | Message/cause; a failed `openPort()` includes the port and last error code |
| `ModbusCrcException` | RTU client when a received frame's CRC differs | `getFrame()` returns the rejected RTU frame; synchronous typed calls wrap it in `ModbusExecutionException` |
| `ModbusExecutionException` | Synchronous client wrapper for unexpected connection, transport, serialization, correlation, or interruption failures | Inspect `getCause()`; interruption also restores the thread interrupt flag |
| `ModbusResponseException` | Typed client for a Modbus exception response; server services to request a Modbus exception response | `getFunctionCode()` and `getExceptionCode()`; see the [RTU exception-code limitation](#rtu-exception-code-limitation) |
| `ModbusTimeoutException` | Synchronous request when the configured request deadline expires | Cause is the internal `TimeoutException` |
| `UnknownUnitIdException` | Server service cannot route the requested unit | Unit ID appears in the message; supplied transports ignore the request |

Synchronous `connect()` wraps a transport connection failure in `ModbusExecutionException`. For a
serial transport, the cause can be `ModbusConnectException`; Netty TCP supplies its underlying
connect or TLS-handshake cause. Code using the transport stage directly or asynchronous client
stages may observe that cause instead. Synchronous request methods specifically translate timeouts
and Modbus exception responses before applying the general execution wrapper.

## RTU exception-code limitation

For typed TCP responses, `ModbusResponseException.getExceptionCode()` contains the byte following
the exception function code. The current RTU client decoder instead records the exception-function
byte itself as the exception code, and it records that byte sign-extended — for exception function
`0x83` the returned value is `-125`, not `131` — so the value is typically negative in logs. RTU
applications should not branch on `getExceptionCode()` until that implementation limitation is
fixed; use endpoint diagnostics or a frame capture when the exact RTU exception code is required.
This section is the canonical statement of the limitation; other pages link here.

## Modbus exception response codes

`ExceptionCode` recognizes the following on-wire codes.

| Code | Enum value | Meaning |
| --- | --- | --- |
| `0x01` | `ILLEGAL_FUNCTION` | Function is not allowed/supported by the server |
| `0x02` | `ILLEGAL_DATA_ADDRESS` | Address plus transfer length is not allowed |
| `0x03` | `ILLEGAL_DATA_VALUE` | Request data structure or value is not allowed |
| `0x04` | `SLAVE_DEVICE_FAILURE` | Server failed while performing the action |
| `0x05` | `ACKNOWLEDGE` | Long-running specialized command was accepted |
| `0x06` | `SLAVE_DEVICE_BUSY` | Server is busy with a long-running command |
| `0x08` | `MEMORY_PARITY_ERROR` | Specialized file-record memory consistency failure |
| `0x0A` | `GATEWAY_PATH_UNAVAILABLE` | Gateway could not allocate a path to the target |
| `0x0B` | `GATEWAY_TARGET_DEVICE_FAILED_TO_RESPONSE` | Gateway received no response from its target |

`ExceptionCode.from(int)` returns an empty `Optional` for unknown codes. A
`ModbusResponseException` still preserves an unknown numeric code and labels it `UNKNOWN` in its
message.

## Typed TCP response validation

| Response condition | Result |
| --- | --- |
| Matching function code | Decode with configured response serializer |
| Request function code plus `0x80` and exception byte | `ModbusResponseException` |
| Exception function code without an exception byte | `ModbusException` for malformed exception PDU |
| Empty typed response PDU | `ModbusException` for empty response PDU |
| Any other function code | `ModbusException` for unexpected function code |
| No response before deadline | Timeout; synchronous API exposes `ModbusTimeoutException` |

Raw TCP calls return the response PDU bytes without these typed checks.

## Server error behavior

| Server condition | Supplied behavior |
| --- | --- |
| Service throws `ModbusResponseException` | Encode standard exception PDU and keep processing |
| Service throws `UnknownUnitIdException` | Transport logs at debug and sends no response |
| Unsupported typed function through default service | `ILLEGAL_FUNCTION` exception response, for the ten typed function codes only |
| Function code without default serializer support | Request decode fails before dispatch and enters the transport error path: the Netty TCP transport closes the client channel; serial drops the frame. No exception response is sent |
| Other handler/codec exception on Netty TCP | Log error and close the client channel |
| RTU parse error over TCP | Log, reset parser, and close the client channel |

## Diagnostic handling

| Failure category | Application action |
| --- | --- |
| Connection/open failure | Inspect nested cause and endpoint/port/serial/TLS configuration before retrying |
| Timeout | Verify unit, framing, address, server load, and timeout budget; do not retry writes blindly |
| Modbus response exception | For typed TCP, branch on numeric exception code and function; for RTU, use endpoint/frame diagnostics — see the [RTU exception-code limitation](#rtu-exception-code-limitation) |
| CRC/synchronization failure | Stop concurrent RTU requests, inspect the link, then allow parser reset/reconnect before retrying |
| Interrupted synchronous call | Treat it as a cancellation: stop the operation and propagate the interrupt; the library restores the thread's interrupt flag |

## Related material

- [Troubleshoot communication](../how-to/operations/troubleshoot-communication.md)
- [Configure timeouts and reconnection](../how-to/operations/configure-timeouts-and-reconnection.md)
- [Exception Javadocs](api-reference.md#exceptions)
