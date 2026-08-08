# Function codes and PDUs

## Default typed support

| Code | Function | Request type | Response type | Protocol quantity/value limit |
| --- | --- | --- | --- | --- |
| `0x01` | Read Coils | `ReadCoilsRequest` | `ReadCoilsResponse` | 1–2000 bits |
| `0x02` | Read Discrete Inputs | `ReadDiscreteInputsRequest` | `ReadDiscreteInputsResponse` | 1–2000 bits |
| `0x03` | Read Holding Registers | `ReadHoldingRegistersRequest` | `ReadHoldingRegistersResponse` | 1–125 registers |
| `0x04` | Read Input Registers | `ReadInputRegistersRequest` | `ReadInputRegistersResponse` | 1–125 registers |
| `0x05` | Write Single Coil | `WriteSingleCoilRequest` | `WriteSingleCoilResponse` | `0x0000` off; `0xFF00` on |
| `0x06` | Write Single Register | `WriteSingleRegisterRequest` | `WriteSingleRegisterResponse` | One 16-bit value |
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
| Single coil convenience value | `boolean` constructor | Maps to `0xFF00` for true, `0x0000` for false |

Array-valued PDU records expose the supplied arrays and do not make defensive copies. Treat an
array as owned by that request/response path while it may be encoded, decoded, or consumed.

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
| Custom `ModbusServices` | Entirely application-defined |

## Related material

- [Feature and transport matrix](feature-and-transport-matrix.md)
- [Client and server behavior](client-and-server-behavior.md)
- [Errors and exceptions](errors-and-exceptions.md)
- [PDU Javadocs](api-reference.md#pdu-api)
