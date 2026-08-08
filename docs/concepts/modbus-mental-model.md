# A Modbus mental model

Modbus is a request/response protocol for addressing small, typed regions of device data. The
digitalpetri library separates the meaning of a request from the way its bytes travel: PDU classes
describe operations, a client or server supplies protocol behavior and constructs logical frames,
and a transport encodes or parses those frames and performs TCP or serial I/O.

## Client, server, and unit

Modern Modbus documents use *client* for the side that initiates a request and *server* for the
side that answers it. Older device manuals often say *master* and *slave*. In this library:

- `ModbusClient` and its TCP/RTU subclasses initiate requests;
- `ModbusServer` and its TCP/RTU implementations receive requests;
- a transport connects or binds and moves framed bytes; and
- a unit ID selects the logical or downstream device that should handle a request.

A single TCP endpoint can route several unit IDs, particularly when it is a gateway. Conversely,
an application can expose one unit ID and ignore all others. The network address and unit ID solve
different routing problems.

## From application intent to bytes

A request crosses four conceptual layers:

1. The application chooses an operation, unit ID, zero-based address, and quantity or value.
2. A typed request PDU, such as `ReadHoldingRegistersRequest`, represents the Modbus operation.
3. `ModbusTcpClient` or `ModbusRtuClient` serializes the PDU, constructs the TCP or RTU frame, and
   manages matching, timeouts, and Modbus exception responses.
4. The selected transport encodes that logical frame, exchanges bytes, and parses received bytes
   back into a frame.

The response travels back through the reverse path and becomes a typed response PDU. This split is
why the same `ReadHoldingRegistersRequest` can be used over ordinary TCP, serial RTU, or RTU over
TCP while framing and response correlation differ.

## Data areas and operations

Modbus exposes four independent logical data areas:

- coils: single-bit values that read and write functions can address;
- discrete inputs: single-bit values read by the discrete-input function;
- holding registers: 16-bit register slots that read and write functions can address; and
- input registers: 16-bit register slots read by the input-register function.

An address does not identify its data area by itself. The function code does. Address 0 in coils
and address 0 in holding registers refer to different logical values.

The library's PDU records stay close to the wire model. Register responses contain a `byte[]`, two
bytes per register, rather than guessing whether an application wants an unsigned integer, signed
integer, float, string, scaled engineering value, or vendor-specific multi-register layout.

## What the client owns

A client owns the request lifecycle: it connects a transport, serializes a request, applies the
configured request timeout, recognizes Modbus exception responses, and returns either a typed
response or an exception. Synchronous methods block the calling thread on the same asynchronous
stage; asynchronous methods return `CompletionStage` values.

TCP responses carry transaction identifiers, so the TCP client can correlate in-flight requests.
RTU responses have no transaction identifier, so an application should keep one RTU request in
flight and preserve request/response order.

## What the server owns

A server binds a transport, decodes requests, routes them to `ModbusServices`, and encodes
responses. A service can implement operations directly or use `ReadOnlyModbusServices` or
`ReadWriteModbusServices` with a `ProcessImage`.

`ProcessImage` is an in-memory, lock-protected representation of the four data areas. It is useful
when the values truly live in memory. It is not an abstraction for arbitrary device I/O by itself;
applications backed by databases, controllers, or remote systems should implement service
behavior appropriate to their latency, error, and consistency needs.

## Common misconceptions

**“The `4` in `40001` is part of the PDU address.”** It is traditional documentation notation.
The request carries a zero-based offset, and the function code selects holding registers.

**“A TCP connection identifies the Modbus device.”** It identifies the endpoint. The unit ID may
still select a logical device or a device behind a gateway.

**“A register is a Java `short`.”** On the wire it is two bytes. Signedness, scaling, and grouping
are application/device concerns.

**“RTU over TCP is Modbus TCP.”** Both use TCP as a byte stream, but one carries an RTU unit/PDU/CRC
frame and the other carries an MBAP header plus PDU.

**“Disconnecting one client releases every library thread.”** A client closes its transport
connection. Default executors and Netty event-loop resources are shared and have a separate
application-shutdown lifecycle.

## Related tasks and reference

- [Read and write over Modbus TCP](../how-to/clients/read-and-write-over-tcp.md)
- [Expose data over Modbus TCP](../how-to/servers/expose-data-over-modbus-tcp.md)
- [Addressing, unit IDs, and data](addressing-unit-ids-and-data.md)
- [Transports, framing, and security](transports-framing-and-security.md)
- [Client and server behavior](../reference/client-and-server-behavior.md)
