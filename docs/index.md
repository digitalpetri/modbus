# digitalpetri Modbus documentation

digitalpetri Modbus provides Java 17 client and server APIs for Modbus TCP, Modbus TCP Security,
Modbus RTU over serial, and Modbus RTU over TCP. Choose a how-to guide to build or operate an
integration, a concept page for the underlying model, or a reference page for exact library
behavior.

New to this library? Read in this order: the
[Modbus mental model](concepts/modbus-mental-model.md), then
[Installation and modules](reference/installation-and-modules.md), then
[Read and write over Modbus TCP](how-to/clients/read-and-write-over-tcp.md). To try the client
without device hardware, run the local server from
[Expose data over Modbus TCP](how-to/servers/expose-data-over-modbus-tcp.md) first and point the
client example at it.

## How-to guides

### Clients

- [Read and write over Modbus TCP](how-to/clients/read-and-write-over-tcp.md)
- [Communicate over serial RTU](how-to/clients/communicate-over-serial-rtu.md)
- [Secure a Modbus TCP client with TLS](how-to/clients/secure-a-modbus-tcp-client-with-tls.md)

### Servers

- [Expose data over Modbus TCP](how-to/servers/expose-data-over-modbus-tcp.md)

### Operations

- [Configure timeouts and reconnection](how-to/operations/configure-timeouts-and-reconnection.md)
- [Troubleshoot communication](how-to/operations/troubleshoot-communication.md)

## Concepts

- [A Modbus mental model](concepts/modbus-mental-model.md)
- [Addressing, unit IDs, and data](concepts/addressing-unit-ids-and-data.md)
- [Transports, framing, and security](concepts/transports-framing-and-security.md)

## Reference

- [Installation and modules](reference/installation-and-modules.md)
- [Feature and transport matrix](reference/feature-and-transport-matrix.md)
- [Client and server behavior](reference/client-and-server-behavior.md)
- [Transport configuration](reference/transport-configuration.md)
- [Function codes and PDUs](reference/function-codes-and-pdus.md)
- [Errors and exceptions](reference/errors-and-exceptions.md)
- [Lifecycle, concurrency, and resources](reference/lifecycle-concurrency-and-resources.md)
- [API reference](reference/api-reference.md)

See [Installation and modules](reference/installation-and-modules.md) for published versions and
Maven coordinates.
