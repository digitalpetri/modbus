# Installation and modules

## Requirements

| Requirement | Value |
| --- | --- |
| Java | 17 or newer |
| Build layout | Maven multi-module project |
| Published group ID | `com.digitalpetri.modbus` |

The dependency examples below pin release `2.1.6`. Check
[Maven Central](https://central.sonatype.com/search?q=g%3Acom.digitalpetri.modbus) for the latest
published version.

## Published modules

| Artifact ID | Automatic module name | Contents | Main runtime dependencies |
| --- | --- | --- | --- |
| `modbus` | `com.digitalpetri.modbus` | Core clients, servers, PDUs, framing, services, exceptions, and transport interfaces | SLF4J API |
| `modbus-tcp` | `com.digitalpetri.modbus.tcp` | Netty Modbus TCP and RTU-over-TCP client/server transports, TLS support | `modbus`, Netty, Netty channel FSM, SLF4J API |
| `modbus-serial` | `com.digitalpetri.modbus.serial` | jSerialComm RTU client/server transports | `modbus`, jSerialComm |

`modbus-tests` is a reactor integration-test module, not a published application dependency.

Depending on either transport module brings in `modbus` transitively. Depend directly on `modbus`
only when implementing against the core APIs without either supplied transport, or when declaring
module boundaries explicitly.

## Declaring the dependency

### Modbus TCP, TLS, or RTU over TCP

Maven:

```xml
<dependency>
  <groupId>com.digitalpetri.modbus</groupId>
  <artifactId>modbus-tcp</artifactId>
  <version>2.1.6</version>
</dependency>
```

Gradle:

```groovy
implementation("com.digitalpetri.modbus:modbus-tcp:2.1.6")
```

### Modbus RTU over serial

Maven:

```xml
<dependency>
  <groupId>com.digitalpetri.modbus</groupId>
  <artifactId>modbus-serial</artifactId>
  <version>2.1.6</version>
</dependency>
```

Gradle:

```groovy
implementation("com.digitalpetri.modbus:modbus-serial:2.1.6")
```

The library logs through SLF4J. Applications should provide the SLF4J implementation appropriate
to their runtime; the published modules depend on the API, not a production logging backend.

## Source module layout

| Reactor module | Source purpose |
| --- | --- |
| [`modbus`](../../modbus) | Transport-independent implementation and public contracts |
| [`modbus-tcp`](../../modbus-tcp) | Netty transport implementation and TLS helpers |
| [`modbus-serial`](../../modbus-serial) | jSerialComm transport implementation |
| [`modbus-tests`](../../modbus-tests) | Cross-module TCP, RTU, raw-PDU, and TLS integration tests |

## Version and API documentation

Use one digitalpetri Modbus version for all explicitly declared modules. Browse the current
published artifacts in [Maven Central](https://central.sonatype.com/search?q=g%3Acom.digitalpetri.modbus)
and use the matching generated Javadocs linked from [API reference](api-reference.md).

## Related material

- [Feature and transport matrix](feature-and-transport-matrix.md)
- [Transport configuration](transport-configuration.md)
- [Read and write over Modbus TCP](../how-to/clients/read-and-write-over-tcp.md)
- [Communicate over serial RTU](../how-to/clients/communicate-over-serial-rtu.md)
