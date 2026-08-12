# digitalpetri Modbus

[![Maven Central](https://img.shields.io/maven-central/v/com.digitalpetri.modbus/modbus.svg)](https://central.sonatype.com/search?q=g%3Acom.digitalpetri.modbus)

digitalpetri Modbus is a Java 17 client and server library for Modbus TCP, Modbus TCP Security
(TLS), Modbus RTU over serial, and Modbus RTU over TCP. It provides typed request and response PDUs,
pluggable transports and codecs, and both synchronous and asynchronous client operations.

Reading holding registers from a Modbus TCP device looks like this:

```java
var transport = NettyTcpClientTransport.create(cfg -> cfg.setHostname("10.0.0.100"));
var client = ModbusTcpClient.create(transport);

try (var ignored = client.open()) {
  var response = client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 2));
  System.out.println("register bytes: " + HexFormat.of().formatHex(response.registers()));
}
```

## Installation and modules

Most applications depend on the transport module they use; Maven brings in the core `modbus`
module transitively.

| Module | Use it for |
| --- | --- |
| `modbus` | Core client/server APIs, PDUs, framing, services, and transport interfaces |
| `modbus-tcp` | Netty transports for Modbus TCP, TCP with TLS, and RTU over TCP |
| `modbus-serial` | jSerialComm transports for Modbus RTU over a serial port |

For a Modbus TCP application:

```xml
<dependency>
  <groupId>com.digitalpetri.modbus</groupId>
  <artifactId>modbus-tcp</artifactId>
  <version>2.1.6</version>
</dependency>
```

See [Installation and modules](docs/user/reference/installation-and-modules.md) for serial coordinates,
Java requirements, and module details.

## Transport capabilities

| Transport | Client | Server | TLS |
| --- | --- | --- | --- |
| Modbus TCP | Yes | Yes | Optional mutual TLS |
| Modbus RTU over serial | Yes | Yes | No |
| Modbus RTU over TCP | Yes | Yes | Optional mutual TLS |

See the [feature and transport matrix](docs/user/reference/feature-and-transport-matrix.md) for typed
function-code, raw-PDU, and broadcast support.

## Documentation

The [documentation index](docs/user/index.md) lists every how-to, concept, and reference page.

Common tasks:

- [Read and write over Modbus TCP](docs/user/how-to/clients/read-and-write-over-tcp.md)
- [Communicate over serial RTU](docs/user/how-to/clients/communicate-over-serial-rtu.md)
- [Secure a Modbus TCP client with TLS](docs/user/how-to/clients/secure-a-modbus-tcp-client-with-tls.md)
- [Expose data over Modbus TCP](docs/user/how-to/servers/expose-data-over-modbus-tcp.md)
- [Configure timeouts and reconnection](docs/user/how-to/operations/configure-timeouts-and-reconnection.md)
- [Troubleshoot communication](docs/user/how-to/operations/troubleshoot-communication.md)

Concepts and reference:

- Start with the [Modbus mental model](docs/user/concepts/modbus-mental-model.md) if the relationship
  among data areas, unit IDs, PDUs, and transports is unfamiliar.
- Use [Addressing, unit IDs, and data](docs/user/concepts/addressing-unit-ids-and-data.md) before
  translating a device manual into requests.
- Browse the [API reference links](docs/user/reference/api-reference.md) for generated Javadocs.

## License

digitalpetri Modbus is licensed under the [Eclipse Public License 2.0](LICENSE.md).
