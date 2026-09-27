# Client and server behavior

This page describes protocol and wire behavior: correlation, response validation, exception
responses, and service dispatch. For object lifecycle, threading, and shared resources, see
[Lifecycle, concurrency, and resources](lifecycle-concurrency-and-resources.md).

## Client request semantics

Synchronous connection/disconnection failures are wrapped in `ModbusExecutionException`.
Synchronous request calls translate request deadline expiry to `ModbusTimeoutException`, preserve
`ModbusResponseException`, and wrap other failures in `ModbusExecutionException`. Asynchronous
stages expose their exceptional causes directly.

A request timeout removes and fails the pending response promise; it does not cancel a transport
send or channel wait already in progress. A request can still reach the server after the caller's
timeout. Applications must resolve the outcome of timed-out writes or use idempotent application
semantics before retrying.

## TCP client behavior

| Behavior | Detail |
| --- | --- |
| Transaction ID | Allocated in the range 0 through 65535 and wraps to 0 |
| Correlation | Pending responses are selected by MBAP transaction ID |
| Request timeout | One timeout per in-flight request; default 5 seconds |
| Typed response validation | Empty PDUs, malformed exception PDUs, and unexpected function codes fail the request |
| Standard exception response | Function code `request + 0x80` and an exception byte become `ModbusResponseException` |
| Late/unmatched response | Logged when its transaction ID has no promise; after ID wrap/reuse, a sufficiently late response can match the newer promise |
| Raw request | `sendRaw` accepts PDU bytes only, adds MBAP, and returns response PDU bytes only |
| Raw response interpretation | Bytes are returned as received; exception-shaped and empty PDUs are not decoded |

The transaction sequence contract is thread-safe and the client uses a concurrent pending map.
Applications should still bound the number of simultaneous requests so a transaction ID is not
reused while its previous request remains in flight.

## RTU client behavior

| Behavior | Detail |
| --- | --- |
| Request order | One request is outstanding at a time. Other requests wait in submission order and are sent when the current request gets a response, times out, or fails to send |
| Correlation | A response is matched to the outstanding request. The client checks the unit ID and, for normal responses, the function code. There is no transaction ID or ordering field |
| Request timeout | Starts when the request is submitted, so it includes time spent waiting behind other requests. A request that times out while waiting is never sent |
| CRC | Calculated on send and verified on receive |
| Timeout recovery | Fails the outstanding request, resets the transport frame parser, and sends the next waiting request |
| CRC recovery | Fails with `ModbusCrcException` and resets the frame parser |
| Unit/function mismatch (reported as `slave id mismatch` / `function code mismatch`) | Fails the outstanding request only; requests waiting behind it are not failed. A late response with a matching unit and function code is accepted as the response to the next request |
| Broadcast | `broadcast` sends with unit ID 0 and waits only for send completion. Broadcasts wait their turn in submission order like other requests, and the request timeout applies until the broadcast is written. After a broadcast is written, the next request waits for `broadcastTurnaroundDelay`, which defaults to zero |
| Exception response | Produces `ModbusResponseException`, but `getExceptionCode()` is currently unreliable for RTU; see the [RTU exception-code limitation](errors-and-exceptions.md#rtu-exception-code-limitation) |

These behaviors apply to both serial RTU and RTU over TCP. For concurrent callers, see
[Client concurrency](lifecycle-concurrency-and-resources.md#client-concurrency).

## Server lifecycle

| Operation | Behavior |
| --- | --- |
| `start()` | Installs the frame receiver and blocks until the transport binds |
| `stop()` | Blocks until the transport unbinds |
| `setModbusServices()` | Atomically replaces the service delegate; rejects `null` |

`ModbusTcpServer` and `ModbusRtuServer` decode with the configured request serializer, dispatch by
function code, and encode with the configured response serializer. A `ModbusResponseException`
from a service becomes a standard exception response. Other service/codec exceptions enter the
transport error path; the Netty TCP transport logs the error and closes that client channel.

The built-in TCP and RTU-over-TCP server transports serialize service work through one
`ExecutionQueue` per server transport. Long-running service calls delay later requests, including
requests received from other TCP client channels.

## Service dispatch

| Service type | Exact behavior |
| --- | --- |
| Bare `ModbusServices` | Each unoverridden operation throws `ILLEGAL_FUNCTION` |
| `ReadOnlyModbusServices` | Validates read ranges, chooses a `ProcessImage` by unit ID, and reads missing bits/registers as zero |
| `ReadWriteModbusServices` | Adds coil and holding-register writes, Mask Write Register (`0x16`), and Read/Write Multiple Registers (`0x17`); writing zero removes the corresponding map entry |
| Custom `ModbusServices` | Application controls returned PDU, Modbus exception, unit routing, and backing store |

The request records and default serializers mirror wire fields and do not comprehensively validate
constructor values, byte-array lengths, or every protocol range. `ReadOnlyModbusServices` validates
read address/quantity ranges. The write implementations in `ReadWriteModbusServices` perform no
range or quantity validation, and that includes the read portion of Read/Write Multiple Registers
(`0x17`). Applications implementing custom services must enforce their own data map and value
constraints.

## Unknown unit IDs

`ReadOnlyModbusServices` and `ReadWriteModbusServices` throw `UnknownUnitIdException` when
`getProcessImage(unitId)` is empty. Supplied TCP, RTU-over-TCP, and serial server transports catch
that exception, log at debug level, and send no response. A client therefore normally observes a
timeout for an unknown unit, not a Modbus exception response.

## Process image behavior

| Area | Stored value | Missing-entry read | Standard external write support |
| --- | --- | --- | --- |
| Coils | `Boolean` | `false` | Yes |
| Discrete inputs | `Boolean` | `false` | No; application can update through a transaction |
| Holding registers | two-byte `byte[]` | `00 00` | Yes |
| Input registers | two-byte `byte[]` | `00 00` | No; application can update through a transaction |

`ProcessImage` transactions are callback-scoped and cannot be nested on the same thread. Reads
receive unmodifiable map views; writes receive transaction-scoped mutable views. See
[Lifecycle, concurrency, and resources](lifecycle-concurrency-and-resources.md#processimage-concurrency).

## Related material

- [Errors and exceptions](errors-and-exceptions.md)
- [Function codes and PDUs](function-codes-and-pdus.md)
- [Expose data over Modbus TCP](../how-to/servers/expose-data-over-modbus-tcp.md)
- [API reference](api-reference.md)
