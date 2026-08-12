# API reference

Generated Javadocs are the canonical symbol-level reference for constructors, methods, record
components, implemented interfaces, and declared exceptions. These links use javadoc.io's
`latest` alias; select a concrete version there when matching a pinned dependency.

## Module Javadocs

| Module | Published Javadocs | Primary packages |
| --- | --- | --- |
| `modbus` | [Core Javadocs](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/) | `com.digitalpetri.modbus`, `.client`, `.server`, `.pdu`, `.exceptions`, `.server.authz` |
| `modbus-tcp` | [TCP Javadocs](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-tcp/latest/) | `com.digitalpetri.modbus.tcp`, `.tcp.client`, `.tcp.server`, `.tcp.security` |
| `modbus-serial` | [Serial Javadocs](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-serial/latest/) | `com.digitalpetri.modbus.serial`, `.serial.client`, `.serial.server` |

## Client API

| API | Javadocs | Use |
| --- | --- | --- |
| `ModbusClient` | [Class](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/client/ModbusClient.html) | Lifecycle and typed synchronous/asynchronous operations |
| `ModbusTcpClient` | [Class](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/client/ModbusTcpClient.html) | MBAP correlation and raw TCP PDU operations |
| `ModbusRtuClient` | [Class](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/client/ModbusRtuClient.html) | RTU framing, CRC, and broadcasts |
| `ModbusClientConfig` | [Record](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/client/ModbusClientConfig.html) | Request timeout, scheduler, and serializers |

See [Client and server behavior](client-and-server-behavior.md#client-request-semantics) and
[Client operations](lifecycle-concurrency-and-resources.md#client-operations) for cross-cutting
semantics not usefully repeated on every method.

## Server API

| API | Javadocs | Use |
| --- | --- | --- |
| `ModbusServer` | [Interface](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/server/ModbusServer.html) | Server lifecycle and service replacement |
| `ModbusTcpServer` | [Class](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/server/ModbusTcpServer.html) | Modbus TCP decode/dispatch/encode path |
| `ModbusRtuServer` | [Class](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/server/ModbusRtuServer.html) | Modbus RTU decode/dispatch/encode path |
| `ModbusServices` | [Interface](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/server/ModbusServices.html) | Typed service operations and range helpers |
| `ProcessImage` | [Class](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/server/ProcessImage.html) | In-memory data areas and transactions |
| `ReadOnlyModbusServices` | [Class](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/server/ReadOnlyModbusServices.html) | Process-image reads |
| `ReadWriteModbusServices` | [Class](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/server/ReadWriteModbusServices.html) | Process-image reads and writes |
| Authorization (`AuthzModbusServices`, `AuthzHandler`) | [Package](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/server/authz/package-summary.html) | Certificate-derived authorization around service calls |

## PDU API

The [PDU package](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/pdu/package-summary.html)
contains request and response records for the ten default typed function codes. Use
[Function codes and PDUs](function-codes-and-pdus.md) as the scannable function-to-type map, then
use the generated record page for exact components and serializer methods.

`ModbusPduSerializer` and its default implementations are documented in the
[core Javadocs](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/ModbusPduSerializer.html).

## Transport and security API

| Area | Javadocs |
| --- | --- |
| TCP client transport/config | [`NettyTcpClientTransport`](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-tcp/latest/com.digitalpetri.modbus.tcp/com/digitalpetri/modbus/tcp/client/NettyTcpClientTransport.html), [`NettyClientTransportConfig`](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-tcp/latest/com.digitalpetri.modbus.tcp/com/digitalpetri/modbus/tcp/client/NettyClientTransportConfig.html) |
| RTU-over-TCP client | [`NettyRtuClientTransport`](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-tcp/latest/com.digitalpetri.modbus.tcp/com/digitalpetri/modbus/tcp/client/NettyRtuClientTransport.html) |
| TCP/RTU-over-TCP server config | [`NettyServerTransportConfig`](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-tcp/latest/com.digitalpetri.modbus.tcp/com/digitalpetri/modbus/tcp/server/NettyServerTransportConfig.html) |
| TCP server transport | [`NettyTcpServerTransport`](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-tcp/latest/com.digitalpetri.modbus.tcp/com/digitalpetri/modbus/tcp/server/NettyTcpServerTransport.html) |
| RTU-over-TCP server | [`NettyRtuServerTransport`](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-tcp/latest/com.digitalpetri.modbus.tcp/com/digitalpetri/modbus/tcp/server/NettyRtuServerTransport.html) |
| TLS helper | [`SecurityUtil`](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-tcp/latest/com.digitalpetri.modbus.tcp/com/digitalpetri/modbus/tcp/security/SecurityUtil.html) |
| Serial config | [`SerialPortTransportConfig`](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-serial/latest/com.digitalpetri.modbus.serial/com/digitalpetri/modbus/serial/SerialPortTransportConfig.html) |
| Serial client/server | [`SerialPortClientTransport`](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-serial/latest/com.digitalpetri.modbus.serial/com/digitalpetri/modbus/serial/client/SerialPortClientTransport.html), [`SerialPortServerTransport`](https://javadoc.io/doc/com.digitalpetri.modbus/modbus-serial/latest/com.digitalpetri.modbus.serial/com/digitalpetri/modbus/serial/server/SerialPortServerTransport.html) |

See [Transport configuration](transport-configuration.md) for defaults in one table.

## Exceptions

The [exceptions package](https://javadoc.io/doc/com.digitalpetri.modbus/modbus/latest/com.digitalpetri.modbus/com/digitalpetri/modbus/exceptions/package-summary.html)
contains the checked exception hierarchy. See [Errors and exceptions](errors-and-exceptions.md) for
where each exception surfaces and how synchronous wrappers differ from asynchronous stages.

## Build Javadocs from this checkout

```shell
mvn -pl modbus,modbus-tcp,modbus-serial -am javadoc:javadoc
```

Each module writes generated HTML below its `target/reports/apidocs` directory. A local
`javadoc:javadoc` build uses a non-modular page layout, so its paths differ from the module-based
layout of the published javadoc.io links. Generated files are build artifacts and are not part of
the portable Markdown documentation tree.

## Source entry points

| Area | Source |
| --- | --- |
| Client API | [`ModbusClient.java`](../../../modbus/src/main/java/com/digitalpetri/modbus/client/ModbusClient.java) |
| Server API | [`ModbusServer.java`](../../../modbus/src/main/java/com/digitalpetri/modbus/server/ModbusServer.java) |
| TCP client transports | [`tcp/client`](../../../modbus-tcp/src/main/java/com/digitalpetri/modbus/tcp/client) |
| TCP server transports | [`tcp/server`](../../../modbus-tcp/src/main/java/com/digitalpetri/modbus/tcp/server) |
| Serial transports | [`serial`](../../../modbus-serial/src/main/java/com/digitalpetri/modbus/serial) |
| Integration tests | [`modbus-tests`](../../../modbus-tests/src/test/java/com/digitalpetri/modbus/test) |
