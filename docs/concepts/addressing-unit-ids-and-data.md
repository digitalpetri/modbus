# Addressing, unit IDs, and data

Modbus combines three separate choices in every ordinary request: a unit ID, a function code, and
a zero-based address inside the function's data area. Keeping those choices separate prevents the
most common integration errors.

## Data areas are selected by function

The four familiar Modbus data areas are logical namespaces, not regions of one shared array.

| Data area | Shape | Typical library read request | Writable through standard functions |
| --- | --- | --- | --- |
| Coils | One bit per item | `ReadCoilsRequest` | Yes |
| Discrete inputs | One bit per item | `ReadDiscreteInputsRequest` | No standard write in this library |
| Holding registers | Two bytes per item | `ReadHoldingRegistersRequest` | Yes |
| Input registers | Two bytes per item | `ReadInputRegistersRequest` | No standard write in this library |

Address 12 can exist independently in all four areas. The request type/function code determines
which one is meant.

## Traditional labels versus PDU addresses

Device manuals often print reference labels such as `00001`, `10001`, `30001`, and `40001`. The
leading digit describes the data area and the remaining number is commonly one-based. The PDU
does not carry that leading digit and its address is zero-based.

For the common five-digit convention:

| Manual label | Meaning | Request address |
| --- | --- | --- |
| `00001` | First coil | 0 |
| `10001` | First discrete input | 0 |
| `30001` | First input register | 0 |
| `40001` | First holding register | 0 |
| `40010` | Tenth holding register | 9 |

Some manuals print raw zero-based offsets, use six-digit labels, or use vendor-specific ranges.
Therefore “subtract 40001” is not a universal algorithm. First identify the manual's convention,
then pass the resulting offset directly to the PDU constructor. Request records represent the
two-byte wire address; the current serializers do not validate constructor arguments before
narrowing them to the wire fields.

## Unit IDs and gateways

The unit ID is a one-byte field in both the MBAP header and RTU frame. The public API accepts an
`int`, while framing writes its low eight bits, so applications should supply an intentional value
in the range 0 through 255.

On a serial bus, a nonzero unit ID selects a server on the shared line. On TCP, the field remains
present and is often used by gateways to select a downstream device. A direct TCP device may
expect a fixed value even when there is only one endpoint; follow its manual.

Server services receive the unit ID and decide whether it is owned. The supplied process-image
service base classes use `getProcessImage(unitId)` for that routing decision. With the built-in
server transports, `UnknownUnitIdException` is treated as no response: the request is logged at
debug level and ignored.

## Broadcasts

Unit ID 0 is the RTU broadcast address in the library's dedicated broadcast path.
`ModbusRtuClient.broadcast(...)` sends a request with unit 0 and returns after the frame is sent;
it does not create a response promise or wait for a reply. Broadcasts are write-only.

Calling an ordinary request method with unit 0 is not equivalent: it creates a normal in-flight
request and waits for a response. Modbus TCP client/server code does not add special broadcast
semantics to unit 0. Use the explicit `broadcast(...)` method when you intend a broadcast; do not
rely on unit 0 alone to imply broadcast behavior.

The same applies on the server side: the built-in servers do not treat a received unit-0 frame
specially. A service that claims unit 0 through `getProcessImage(0)` (or custom routing) receives
broadcast requests through normal dispatch, and the server sends a response — which violates the
Modbus broadcast contract and can collide with other traffic on a shared serial bus. A server
implementation that claims unit 0 is responsible for suppressing responses to broadcast requests.

## Register bytes and multi-register values

A Modbus register is 16 bits transmitted as two bytes, high byte then low byte. The library keeps
read data in contiguous `byte[]` values and represents `ProcessImage` register entries as two-byte
arrays. For example, bytes `12 34` represent the single unsigned register value `0x1234`.

Values wider than one register introduce a second ordering question. Two registers containing
`12 34 56 78` may represent `0x12345678`, swapped words, a floating-point value, two unrelated
values, or something else defined by the device. The library deliberately does not reorder words
or apply signedness/scaling. Decode and encode that application meaning at the boundary where the
device data map is known.

Bit responses use a different packing rule: the first addressed coil or discrete input is the
least-significant bit of the first data byte, followed toward the most-significant bit, then into
the next byte.

## Common misconceptions

**“Holding register 40001 means address 40001.”** Under the common reference convention it means
holding-register offset 0.

**“Unit ID does not matter on TCP.”** It can matter to a gateway or direct endpoint and is always
present in the MBAP header.

**“Missing process-image registers are illegal addresses.”** The supplied process-image services
read missing entries as zero. A custom service can implement a stricter device map.

**“The library will decode my float.”** It returns register bytes because the device's word order,
type, and scale are not in the Modbus PDU.

**“Broadcast is a faster read.”** RTU broadcast has no response and is for writes only.

## Related tasks and reference

- [Read and write over Modbus TCP](../how-to/clients/read-and-write-over-tcp.md)
- [Communicate over serial RTU](../how-to/clients/communicate-over-serial-rtu.md)
- [Troubleshoot communication](../how-to/operations/troubleshoot-communication.md)
- [Function codes and PDUs](../reference/function-codes-and-pdus.md)
- [Errors and exceptions](../reference/errors-and-exceptions.md)
