# Client and server behavior

This page describes protocol and wire behavior: correlation, response validation, exception
responses, and service dispatch. For object lifecycle, threading, and shared resources, see
[Lifecycle, concurrency, and resources](lifecycle-concurrency-and-resources.md).

## Client request semantics

Synchronous connection/disconnection failures are wrapped in `ModbusExecutionException`.
Synchronous request calls translate request deadline expiry to `ModbusTimeoutException`, preserve
`ModbusResponseException`, and wrap other failures in `ModbusExecutionException`. Asynchronous
stages report failures without the synchronous execution wrapper. See
[Typed response validation](#typed-response-validation) for malformed-response failures.

A request timeout removes and fails the pending response promise; it does not cancel a transport
send or channel wait already in progress. A request can still reach the server after the caller's
timeout. Applications must resolve the outcome of timed-out writes or use idempotent application
semantics before retrying.

## Connection loss

When a Netty TCP or RTU-over-TCP transport detects that an established connection has closed,
including an explicit disconnect, the client fails requests already handed to the transport with
`ModbusConnectionLostException` and cancels their request timeouts. This includes raw TCP requests
and RTU broadcasts whose sends have not completed. Synchronous calls wrap the exception in
`ModbusExecutionException`.

Requests still waiting in the RTU client's queue keep their original timeouts and submission order.
The next request is handed to the transport, where it follows the configured connection and
reconnection policy. Connection loss does not retry a failed request. A write may already have
reached the device, so its outcome is unknown.

Custom transports can report connection loss through `ModbusClientTransport.onConnectionLost`.
Its `Supplier<Runnable>` captures affected requests during the connection-state transition, before
reconnection. The transport then runs the returned cleanup task in order with response delivery,
after responses queued before the loss. This keeps a delayed notification from failing requests on
a replacement connection. The supplier must return promptly without waiting for transport operations.
Netty logs exceptions from either phase and still notifies its existing connection listeners.
The default implementation does nothing, so transports without loss detection retain their timeout
behavior. The serial transport does not currently report connection loss through this callback.
The client installs this callback; applications observing a Netty transport should use its existing
`addConnectionListener` API.

## Typed response validation

TCP and RTU clients decode normal responses with `responseSerializer`, then call
`responseValidator.validate(request, response)` before completing the request successfully.
This applies to `send` / `sendAsync` and all synchronous and asynchronous typed operations.
Raw TCP calls bypass both the codec and the validator. Modbus exception responses keep their
existing exception handling and do not invoke the validator.

The default `ModbusResponseValidator.DefaultResponseValidator` checks matching built-in request
and response record pairs. Register reads must contain two bytes per requested register. Coil and
discrete-input reads must contain the requested packed byte count, rounded up to a whole byte.
Writes must echo the request's address, quantity, value, or masks. Write Single Register (`0x06`)
values may have any length, but every value byte must be echoed exactly. Other PDU types are ignored by
the standard validator, including custom PDUs that use a standard function code.

Replace the validator independently of the codec with
`ModbusClientConfig.Builder.setResponseValidator(...)` or the builder's `responseValidator` field.
Applications using built-in records with a different register width or echo semantics must replace
both the codec and the validator. Replacing only the codec retains standard semantic validation.
Write Single Register already supports arbitrary value lengths with the built-in codec and validator.
Validators must support concurrent use by the clients that share them.

Format checks belong to the selected codec. Built-in response decoders require the buffer's
remaining bytes to contain exactly one complete PDU. They reject truncation, unexpected trailing
bytes, invalid byte counts, and invalid standard response fields with an `IllegalArgumentException`
that describes the protocol error. Typed clients retain this decoder exception as the cause of
`ModbusMalformedResponseException`. Custom codecs determine their own format and consumption rules;
the generic client path does not require their buffers to be fully consumed.
For Write Single Register, every byte after the address is part of the value, so the decoder only
requires a complete function code and address and consumes all remaining value bytes.

Malformed PDUs, response mismatches, and codec or validator failures complete asynchronous
operations with `ModbusMalformedResponseException`. The original decoder or validator exception is
retained as the cause unless it already has this type. Synchronous calls wrap the malformed-response
exception in `ModbusExecutionException`. CRC failures retain `ModbusCrcException`, and valid Modbus
exception responses retain `ModbusResponseException`.

A rejected complete response fails that request only. TCP correlation
continues normally, and RTU sends the next queued request without resetting the frame parser when
the frame has a valid CRC. Existing timeout and CRC recovery still apply when needed.

`ModbusClientConfig` has six record components. The five-argument constructor selects the default
validator. Record reflection and generated `equals`, `hashCode`, and `toString` include the
validator.

## TCP client behavior

| Behavior | Detail |
| --- | --- |
| Transaction ID | Allocated in the range 0 through 65535 and wraps to 0 |
| Correlation | Pending responses are selected by MBAP transaction ID |
| Request timeout | One timeout per in-flight request; default 5 seconds |
| Typed response validation | Empty PDUs, malformed exception PDUs, unexpected function codes, codec failures, and request-relative validation failures fail the request |
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
| Correlation | A response is matched to the outstanding request. The client checks the unit ID and the function code. An exception response must carry the request's function code plus `0x80`. There is no transaction ID or ordering field |
| Request timeout | Starts when the request is submitted, so it includes time spent waiting behind other requests. A request that times out while waiting is never sent |
| CRC | Calculated on send and verified on receive |
| Timeout recovery | Fails the outstanding request, resets the transport frame parser, and sends the next waiting request |
| CRC recovery | Fails with `ModbusCrcException` and resets the frame parser |
| Unit/function mismatch (reported as `slave id mismatch` / `function code mismatch`) | Fails the outstanding request only; requests waiting behind it are not failed. A late response with a matching unit and function code is accepted as the response to the next request |
| Broadcast | `broadcast` sends with unit ID 0 and waits only for send completion. Broadcasts wait their turn in submission order like other requests, and the request timeout applies until the broadcast is written. A timeout is reported by `broadcast` as `ModbusExecutionException` with a `TimeoutException` cause. Cancelling the `broadcastAsync` future attempts to cancel its transport send; a queued write that has not started is skipped. After a broadcast is written, the next request waits for `broadcastTurnaroundDelay`, which defaults to zero. If a broadcast times out while the serial transport is still writing it, the caller gets the timeout, but the next request waits for the write to finish and then for the turnaround delay |
| Exception response | Produces `ModbusResponseException` with the request's function code and the response's exception code. An exception response without an exception code fails with `ModbusMalformedResponseException` and the diagnostic `malformed exception response PDU` |

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
