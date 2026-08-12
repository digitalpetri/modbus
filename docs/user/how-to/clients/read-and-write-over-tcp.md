# Read and write over Modbus TCP

Use this guide to connect a Java application to a Modbus TCP server, read a holding register, and
then write one and read it back. It applies to ordinary Modbus TCP without TLS; use the
[TLS guide](secure-a-modbus-tcp-client-with-tls.md) when the endpoint requires Modbus TCP Security.

## Prerequisites

You need:

- Java 17 or newer;
- the `modbus-tcp` dependency described in [Installation and modules](../../reference/installation-and-modules.md);
- the server hostname and TCP port (normally 502 for non-TLS Modbus TCP);
- the target unit ID; and
- the device manual's zero-based holding-register offset and value encoding.

If the manual uses labels such as `40001`, translate them before constructing a request. See
[Addressing, unit IDs, and data](../../concepts/addressing-unit-ids-and-data.md).

No device available yet? Run the standalone server from
[Expose data over Modbus TCP](../servers/expose-data-over-modbus-tcp.md) on `localhost` port 1502
and point this example at it.

## Create the client and read a register

The following standalone program reads holding-register offset 0 from unit 1. Reading is the safe
first step against unfamiliar equipment. Change the host, port, unit ID, and address for your
device.

```java
package example;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.tcp.Netty;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import java.nio.ByteBuffer;
import java.time.Duration;

public final class TcpReadExample {

  public static void main(String[] args) throws Exception {
    var transport =
        NettyTcpClientTransport.create(
            cfg -> {
              cfg.setHostname("192.0.2.10");
              cfg.setPort(502);
              cfg.setConnectPersistent(false);
            });

    var client =
        ModbusTcpClient.create(
            transport, cfg -> cfg.setRequestTimeout(Duration.ofSeconds(3)));

    try (var ignored = client.open()) {
      int unitId = 1;
      int address = 0;

      var response =
          client.readHoldingRegisters(
              unitId, new ReadHoldingRegistersRequest(address, 1));

      int value = ByteBuffer.wrap(response.registers()).getShort() & 0xFFFF;
      System.out.printf("holding register %d = 0x%04X%n", address, value);
    } finally {
      // Do this only when the whole application is finished with the library.
      Netty.releaseSharedResources();
      Modbus.releaseSharedResources();
    }
  }
}
```

`client.open()` connects and returns an `AutoCloseable` whose `close()` disconnects the transport.
This short-lived example disables persistent connection retries so that a failed `open()` does not
leave a reconnect loop running in the background. Long-running applications can use the default
reconnect policy described in [Configure timeouts and reconnection](../operations/configure-timeouts-and-reconnection.md).
The final cleanup releases process-wide shared resources because this is a standalone program. A
larger application should reuse those resources and release them only during application or
ClassLoader shutdown.

The response exposes two bytes per register in wire order. `ByteBuffer` is big-endian by default,
which reconstructs the single 16-bit register shown here. Do not apply that conversion blindly to
multi-register values; the device manual determines word order and signed/scaled interpretation.

## Write and read back

Writing changes live process data. Before running a write against real equipment, confirm from the
device manual that the target register is safe to write and that no controller logic depends on
it. To write `0x1234` to the same register and read it back, add
`com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest` to the imports and insert the following
before the read:

```java
client.writeSingleRegister(unitId, new WriteSingleRegisterRequest(address, 0x1234));
```

## Verify the result

Run the read-only program while the endpoint is reachable; it prints the register's current value.
With the write step added, for a device that accepts the write, the output should be:

```text
holding register 0 = 0x1234
```

Also verify the value through the device's own diagnostics or engineering software. Some devices
make a documented register read-only or require an enable state before writes take effect.

## Common failure symptoms

| Symptom | Check |
| --- | --- |
| Connection fails immediately | Host, port, routing, firewall, and whether the endpoint expects TLS |
| `ModbusTimeoutException` | Unit ID, request address, server availability, and request timeout |
| `ModbusResponseException` with illegal data address | The zero-based offset and requested range |
| Read bytes are present but the value is wrong | Register byte order, multi-register word order, signedness, and scaling |
| Value reads correctly but write has no effect | Whether that data area is writable and whether the device requires a control state |

For a systematic sequence, use [Troubleshoot communication](../operations/troubleshoot-communication.md).

## Related reference

- [Client and server behavior](../../reference/client-and-server-behavior.md)
- [Function codes and PDUs](../../reference/function-codes-and-pdus.md)
- [Transport configuration](../../reference/transport-configuration.md)
- [Lifecycle, concurrency, and resources](../../reference/lifecycle-concurrency-and-resources.md)
- [Generated client API reference](../../reference/api-reference.md#client-api)
