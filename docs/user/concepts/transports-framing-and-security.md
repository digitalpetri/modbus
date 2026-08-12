# Transports, framing, and security

The same Modbus PDU can travel inside different application data units. digitalpetri models that
separation explicitly: TCP and RTU clients share typed PDU operations, while their transports and
framing rules differ.

## The PDU is the common center

A PDU begins with a function code and contains function-specific data. It has no transaction ID,
network address, unit byte outside the function payload, or CRC. Those belong to the surrounding
transport frame.

This distinction matters when choosing a gateway mode. “It uses TCP” describes the byte-stream
carrier, not necessarily the frame inside that stream.

## Modbus TCP framing

A Modbus TCP application data unit is:

```text
MBAP header (7 bytes) | PDU
```

The MBAP header contains a two-byte transaction ID, two-byte protocol ID, two-byte length, and
one-byte unit ID. The library uses protocol ID 0 for typed Modbus traffic. `ModbusTcpClient`
allocates transaction IDs from 0 through 65535, wraps at the end, and correlates responses by that
ID.

Transaction correlation allows more than one request to be in flight on a TCP client, provided
the endpoint supports that usage. After a timeout, the client removes that transaction's promise;
a response that arrives later, with an ID no longer mapped to a pending request, is only logged.
Transaction IDs eventually wrap, so
applications should bound in-flight requests and connection/request lifetimes to avoid reusing an
ID while an old response could still arrive.

## RTU framing over serial

A Modbus RTU application data unit is:

```text
unit ID (1 byte) | PDU | CRC-16 (2 bytes, low byte first)
```

The CRC detects corrupted frames. There is no transaction ID, so response unit and function must
match the outstanding request. The RTU client resets its frame parser after a timeout or CRC error
to recover from an incomplete or invalid response. Keeping one request in flight preserves the
serial request/response order that this matching depends on.

Serial correctness also depends on out-of-band settings—baud rate, data bits, parity, stop bits,
wiring, and sometimes RS-485 direction control. Those settings do not appear inside the Modbus
frame.

## RTU over TCP

RTU over TCP carries the entire RTU frame, including unit ID and CRC, in a TCP byte stream. The
library provides `NettyRtuClientTransport` and `NettyRtuServerTransport` for this mode, paired with
`ModbusRtuClient` and `ModbusRtuServer`.

It is not Modbus TCP: it has no MBAP header or transaction identifier. The built-in RTU-over-TCP
server accepts one client channel at a time, matching the ordered nature of the RTU framing path.
Use this mode only when the peer explicitly documents RTU framing over a TCP connection.

## TLS and Modbus TCP Security

The Netty TCP transports can place TLS below the Modbus framing layer. Enabling TLS does not change
typed PDU construction or MBAP fields; it encrypts and authenticates the TCP stream carrying them.

The built-in configuration requires mutual key/trust material: the client presents a certificate
and verifies the server's chain through its trust manager, while the server uses
`ClientAuth.REQUIRE` and verifies the client through its trust manager. The client transport does
not enable TLS endpoint/hostname identification automatically; applications that identify the
server by host name must enable it or enforce an equivalent policy. The implementation enables TLS
1.2 and TLS 1.3. With no explicit port, TLS-enabled Netty transport configuration selects 802
instead of cleartext port 502.

The same Netty configuration machinery can wrap RTU-over-TCP in TLS, but that does not turn it into
MBAP-framed Modbus TCP Security. Both peers must agree on the inner framing as well as TLS.

## Tradeoffs

Modbus TCP provides transaction correlation and natural integration with routed IP networks, but
does not provide confidentiality or peer identity without TLS. Serial RTU works on shared
industrial buses and includes CRC protection, but communication is ordered and sensitive to
physical/link configuration. RTU over TCP is useful for gateways that preserve RTU frames, at the
cost of retaining RTU's correlation limits on a TCP stream.

TLS protects the connection between its two peers. It does not validate whether an authenticated
client should read or write a particular unit or data area; authorization is a separate server
policy concern.

## Common misconceptions

**“TCP makes the CRC unnecessary in every mode.”** Modbus TCP omits the RTU CRC, but RTU over TCP
retains it because the inner frame is still RTU.

**“Port 502 proves the peer is Modbus TCP.”** Port selection does not establish whether the peer
expects MBAP, RTU-over-TCP, TLS, or a vendor gateway mode.

**“TLS changes Modbus addresses or function codes.”** TLS wraps the byte stream; the inner Modbus
request model remains the same.

**“A trusted certificate grants Modbus permissions.”** TLS establishes peer trust. Application
authorization, when needed, is additional policy.

## Related tasks and reference

- [Communicate over serial RTU](../how-to/clients/communicate-over-serial-rtu.md)
- [Secure a Modbus TCP client with TLS](../how-to/clients/secure-a-modbus-tcp-client-with-tls.md)
- [Troubleshoot communication](../how-to/operations/troubleshoot-communication.md)
- [Feature and transport matrix](../reference/feature-and-transport-matrix.md)
- [Transport configuration](../reference/transport-configuration.md)
