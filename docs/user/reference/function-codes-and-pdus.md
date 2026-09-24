# Function codes and PDUs

## Default typed support

| Code | Function | Request type | Response type | Protocol quantity/value limit |
| --- | --- | --- | --- | --- |
| `0x01` | Read Coils | `ReadCoilsRequest` | `ReadCoilsResponse` | 1–2000 bits |
| `0x02` | Read Discrete Inputs | `ReadDiscreteInputsRequest` | `ReadDiscreteInputsResponse` | 1–2000 bits |
| `0x03` | Read Holding Registers | `ReadHoldingRegistersRequest` | `ReadHoldingRegistersResponse` | 1–125 registers |
| `0x04` | Read Input Registers | `ReadInputRegistersRequest` | `ReadInputRegistersResponse` | 1–125 registers |
| `0x05` | Write Single Coil | `WriteSingleCoilRequest` | `WriteSingleCoilResponse` | `0x0000` off; `0xFF00` on |
| `0x06` | Write Single Register | `WriteSingleRegisterRequest` | `WriteSingleRegisterResponse` | One 2-byte value; see [Write Single Register values](#write-single-register-values) for 4-byte values |
| `0x0F` | Write Multiple Coils | `WriteMultipleCoilsRequest` | `WriteMultipleCoilsResponse` | 1–1968 bits |
| `0x10` | Write Multiple Registers | `WriteMultipleRegistersRequest` | `WriteMultipleRegistersResponse` | 1–123 registers |
| `0x16` | Mask Write Register | `MaskWriteRegisterRequest` | `MaskWriteRegisterResponse` | One register, 16-bit AND and OR masks |
| `0x17` | Read/Write Multiple Registers | `ReadWriteMultipleRegistersRequest` | `ReadWriteMultipleRegistersResponse` | Read 1–125; write 1–121 registers |

Addresses are two-byte, zero-based fields in the range 0 through 65535. A valid range must not
extend past address 65535. See [Addressing, unit IDs, and data](../concepts/addressing-unit-ids-and-data.md)
for translation from traditional reference labels.

The limits above describe the PDU contracts documented by the public types. Request records and
serializers do not comprehensively reject out-of-range constructor values or inconsistent array
lengths before encoding. Custom service implementations must validate requests against both the
protocol and their device data map.

## PDU representation

| Data | Java representation | Wire behavior |
| --- | --- | --- |
| Function code | `int` from `getFunctionCode()` | Encoded as one byte |
| Address/quantity/value fields | `int` | Encoded into the PDU's one- or two-byte field as defined by the type |
| Coil/discrete response values | `byte[]` | First addressed bit is bit 0 (LSB) of the first byte |
| Register response/write values | `byte[]` | Two bytes per register, high byte then low byte |
| Write Single Register value | `byte[]` of 2 or 4 bytes | Encoded as-is after the address |
| Single coil convenience value | `boolean` constructor | Maps to `0xFF00` for true, `0x0000` for false |

Array-valued PDU records expose the supplied arrays and do not make defensive copies. Treat an
array as owned by that request/response path while it may be encoded, decoded, or consumed.

## Write Single Register values

`WriteSingleRegisterRequest` and `WriteSingleRegisterResponse` have the components
`(int address, byte[] value)`. The value must be exactly 2 or 4 bytes; the constructors throw
`IllegalArgumentException` for any other length. Serializers encode the bytes as-is and decode all
bytes that follow the address in the framed PDU.

A 2-byte value is standard Modbus. A 4-byte value supports devices that store a 32-bit value at a
single register address, such as Enron/Daniels Modbus devices. Send one only to a device or
service that expects it. A successful response echoes the address and every value byte, so compare
the full response value with the request.

The `(int address, int value)` constructors remain. They encode the low 16 bits of `value` as two
big-endian bytes, as before. They never produce a 4-byte value.

Four-byte values work over Modbus TCP and Modbus TCP with TLS, where MBAP delimits the PDU. The
built-in RTU frame parsers, for serial RTU and RTU over TCP, assume the standard 2-byte value.
Four-byte values are not supported on those transports. The built-in RTU client does not block them.
It sends the 10-byte request, and the device may apply the write. Its 10-byte response then fails
the CRC check, and the client reports a `ModbusCrcException`. Retrying after that error can repeat
the write. Do not send 4-byte values through the built-in RTU transports.

### Upgrading from `int` values

This is a breaking API change, so it requires a new major version under semantic versioning.
Downstream code that reads the value must be migrated and recompiled. The `value()` accessor now
returns `byte[]` instead of `int`. Code that calls the accessor or deconstructs the record with a
record pattern no longer compiles. Already-compiled code that calls the old accessor fails at run
time with `NoSuchMethodError`. Code that only calls the `(int address, int value)` constructors
stays source and binary compatible. Equality and hash codes now compare the encoded value bytes, and
`toString()` formats them as hex.

`ReadWriteModbusServices.writeSingleRegister` now declares `ModbusResponseException`. Code that
calls it through a `ReadWriteModbusServices` reference, including a subclass calling the super
method, must handle or declare that exception. This affects source compatibility only.

Decoding is stricter. The old decoders ignored bytes after a 2-byte value. An FC 06 PDU whose value
is not exactly 2 or 4 bytes now fails to decode with `IllegalArgumentException`.

To read a 2-byte value as an unsigned integer, use
`((value[0] & 0xFF) << 8) | (value[1] & 0xFF)`.

## Default serializers

| Serializer | Client use | Server use | Supported codes |
| --- | --- | --- | --- |
| `DefaultRequestSerializer` | Encodes requests | Decodes requests | The ten codes in [Default typed support](#default-typed-support) |
| `DefaultResponseSerializer` | Decodes responses | Encodes responses | The same ten codes |

Both default serializer instances are stateless and documented as safe for concurrent use. For an
unsupported function code they throw `ModbusException` with `no serializer for functionCode=...`.

`FunctionCode` additionally enumerates `0x07`, `0x08`, `0x0B`, `0x0C`, `0x11`, `0x14`, `0x15`,
`0x18`, and `0x2B`. Enumeration does not mean a typed PDU class or default serializer exists for
that code.

## Custom and raw paths

Custom `ModbusPduSerializer` implementations can add typed encoding/decoding for protocol paths
that use the configured serializer. This is an extension API; compatibility, validation, and
thread safety belong to the implementation.

For Modbus TCP only, `ModbusTcpClient.sendRaw` and `RawModbusTcpServices` exchange complete PDU
byte arrays while the library supplies and correlates MBAP framing. The raw client deliberately
does not translate exception-shaped response bytes into `ModbusResponseException`.

## Supplied service validation

| Service behavior | Validation |
| --- | --- |
| `ReadOnlyModbusServices` bit reads | Address 0–65535, quantity 1–2000, range does not cross 65536 |
| `ReadOnlyModbusServices` register reads | Address 0–65535, quantity 1–125, range does not cross 65536 |
| `ReadWriteModbusServices` writes | No range or quantity validation; this includes the read portion of Read/Write Multiple Registers (`0x17`) |
| `ReadWriteModbusServices` Write Single Register | Rejects a 4-byte value with `ILLEGAL_DATA_VALUE`; its process image holds 16-bit registers |
| Custom `ModbusServices` | Entirely application-defined |

## Related material

- [Feature and transport matrix](feature-and-transport-matrix.md)
- [Client and server behavior](client-and-server-behavior.md)
- [Errors and exceptions](errors-and-exceptions.md)
- [PDU Javadocs](api-reference.md#pdu-api)
