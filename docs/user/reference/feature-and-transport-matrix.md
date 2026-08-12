# Feature and transport matrix

## Transport implementations

| Wire format | Client transport | Server transport | Client/server API | TLS option | Default port |
| --- | --- | --- | --- | --- | --- |
| Modbus TCP (MBAP + PDU) | `NettyTcpClientTransport` | `NettyTcpServerTransport` | `ModbusTcpClient` / `ModbusTcpServer` | Yes | 502; 802 with TLS |
| Modbus RTU over serial | `SerialPortClientTransport` | `SerialPortServerTransport` | `ModbusRtuClient` / `ModbusRtuServer` | No | Not applicable |
| Modbus RTU over TCP (unit + PDU + CRC) | `NettyRtuClientTransport` | `NettyRtuServerTransport` | `ModbusRtuClient` / `ModbusRtuServer` | Yes | 502; 802 with TLS |

The default port is selected only when a Netty port is not configured. RTU-over-TCP endpoints
often use deployment-specific ports; match the peer instead of assuming 502.

## Typed function-code support

The default request/response serializers, typed client methods, and server dispatch support the
same ten function codes.

| Code | Function | Typed client | Default server dispatch |
| --- | --- | --- | --- |
| `0x01` | Read Coils | Yes | Yes |
| `0x02` | Read Discrete Inputs | Yes | Yes |
| `0x03` | Read Holding Registers | Yes | Yes |
| `0x04` | Read Input Registers | Yes | Yes |
| `0x05` | Write Single Coil | Yes | Yes |
| `0x06` | Write Single Register | Yes | Yes |
| `0x0F` | Write Multiple Coils | Yes | Yes |
| `0x10` | Write Multiple Registers | Yes | Yes |
| `0x16` | Mask Write Register | Yes | Yes |
| `0x17` | Read/Write Multiple Registers | Yes | Yes |

`FunctionCode` also names protocol functions for which the default serializers and typed
client/server dispatch do not provide PDU implementations. See [Function codes and PDUs](function-codes-and-pdus.md).

## Additional capabilities

| Capability | TCP | Serial RTU | RTU over TCP | Notes |
| --- | --- | --- | --- | --- |
| Synchronous typed client calls | Yes | Yes | Yes | Implemented over asynchronous stages |
| Asynchronous typed client calls | Yes | Yes | Yes | Return `CompletionStage` |
| Concurrent in-flight correlation | Yes | No | No | TCP uses MBAP transaction IDs; keep one RTU request in flight |
| Automatic connection recovery | Yes | No | Yes | Netty client state machine; serial reconnect requires another explicit `connect()` |
| Mutual TLS | Yes | No | Yes | Netty transports; TLS 1.2 and 1.3 |
| Raw PDU client/server path | Yes | No | No | `sendRaw` / `RawModbusTcpServices`, with MBAP handled by the library |
| RTU broadcast send | No | Yes | Yes | `ModbusRtuClient.broadcast`; send completion only, no response |
| Pluggable PDU serializers | Yes | Yes | Yes | Client and server configuration accept `ModbusPduSerializer` |
| Pluggable transports | Yes | Yes | Yes | Core transport interfaces are public |
| In-memory process image | Yes | Yes | Yes | `ProcessImage` with read-only/read-write service base classes |
| Request context addresses | Yes | No | Yes | Netty contexts expose local and remote socket addresses |
| Certificate-derived authorization context | Yes (TLS only) | No | Yes (TLS only) | Core authorization types (`AuthzModbusServices`, `AuthzHandler`) are available; no dedicated how-to guide yet — see the [authorization package Javadocs](api-reference.md#server-api) |

## Supplied server service choices

| Type | Behavior |
| --- | --- |
| `ModbusServices` | All operation methods default to `ILLEGAL_FUNCTION`; override supported operations |
| `ReadOnlyModbusServices` | Implements the four standard reads against a unit-selected `ProcessImage` |
| `ReadWriteModbusServices` | Adds standard coil and holding-register writes against a `ProcessImage` |
| `RawModbusTcpServices` | Optionally handles a TCP PDU before typed decode, or declines into normal typed dispatch |
| `AuthzModbusServices` | Wraps service calls with authorization behavior defined by the authz APIs |

## Related material

- [Transports, framing, and security](../concepts/transports-framing-and-security.md)
- [Client and server behavior](client-and-server-behavior.md)
- [Transport configuration](transport-configuration.md)
- [API reference](api-reference.md)
