# Expose data over Modbus TCP

Use this guide to expose an in-memory `ProcessImage` through a Modbus TCP server. It is suitable
for a small server whose Modbus data can be represented directly in the library's process image.
Application-backed services with external I/O need a purpose-built `ModbusServices`
implementation and are outside this guide.

## Prerequisites

You need Java 17, the `modbus-tcp` dependency, a local bind address and port, and a decision about
which unit IDs the server owns. The example uses port 1502, which does not require special OS
privileges.

## Create a process image and server

This standalone server exposes only unit ID 1. It initializes holding register 0 to `0x1234`,
accepts the default typed read/write function codes, and waits for Enter before stopping.

```java
package example;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.Netty;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import java.util.Optional;

public final class TcpServerExample {

  public static void main(String[] args) throws Exception {
    var processImage = new ProcessImage();
    processImage.with(
        tx ->
            tx.writeHoldingRegisters(
                registers -> registers.put(0, new byte[] {0x12, 0x34})));

    var services =
        new ReadWriteModbusServices() {
          @Override
          protected Optional<ProcessImage> getProcessImage(int unitId) {
            return unitId == 1 ? Optional.of(processImage) : Optional.empty();
          }
        };

    var transport =
        NettyTcpServerTransport.create(
            cfg -> {
              cfg.setBindAddress("0.0.0.0");
              cfg.setPort(1502);
            });

    var server = ModbusTcpServer.create(transport, services);

    try {
      server.start();
      System.out.println("Modbus TCP server listening on port 1502; press Enter to stop");
      System.in.read();
    } finally {
      try {
        server.stop();
      } finally {
        Netty.releaseSharedResources();
        Modbus.releaseSharedResources();
      }
    }
  }
}
```

Register entries are two-byte arrays in Modbus wire order. Missing entries in the supplied
`ReadWriteModbusServices` process image read as zero. Its write implementation removes zero-valued
entries, so absence and an all-zero register have the same externally visible value.

Unknown unit IDs return `Optional.empty()`, causing the built-in TCP transport to ignore the
request rather than manufacture a Modbus exception response.

The same service and process-image code works for the other server wire formats: substitute
`SerialPortServerTransport` or `NettyRtuServerTransport` with `ModbusRtuServer` for serial RTU or
RTU over TCP. See [Transport configuration](../../reference/transport-configuration.md) for those
transports' settings.

## Keep application data current

Update inputs or registers inside a `ProcessImage.with(...)` transaction. For example, to refresh
an input register from application data on a schedule:

```java
scheduler.scheduleAtFixedRate(
    () ->
        processImage.with(
            tx -> tx.writeInputRegisters(registers -> registers.put(0, readSensorValue()))),
    0,
    1,
    TimeUnit.SECONDS);
```

Do not retain the transaction or the map view after the callback returns. Use an exclusive
transaction only when a multi-area operation must exclude all other process-image transactions.

Modification listeners run while the corresponding write lock is held. Queue slow or blocking
listener work onto another executor.

When values do not live in memory — they come from a database, controller, or remote system —
implement `ModbusServices` (or override the relevant `ReadWriteModbusServices` methods) instead of
mirroring into a `ProcessImage`. See the
[`ModbusServices` Javadocs](../../reference/api-reference.md#server-api) and the service dispatch
rules in [Client and server behavior](../../reference/client-and-server-behavior.md#service-dispatch).

## Verify the server

Start the program, then configure the [TCP client example](../clients/read-and-write-over-tcp.md)
for `localhost`, port 1502, unit 1, and holding-register offset 0. A read should initially return
`0x1234`; a supported write should be visible in a subsequent read.

## Resource cleanup

`server.stop()` unbinds the listener and closes accepted client channels. It does not release the
process-wide Netty event loop or Modbus executor. Release those shared resources only when the
application is finished with all clients and servers, as the standalone example does.

## Common failure symptoms

| Symptom | Check |
| --- | --- |
| Bind fails | Address availability, port collision, OS privilege, and firewall policy |
| Client times out only for some unit IDs | `getProcessImage` returns a value for each owned unit ID |
| Client receives illegal function | The service implements that function; unoverridden `ModbusServices` methods reject it |
| A long handler stalls unrelated requests | Built-in server transports dispatch service work through a serial execution queue |
| Values have swapped bytes or words | Each register is exactly two wire-order bytes; apply device-specific multi-register ordering outside the process image |

## Related reference

- [Client and server behavior](../../reference/client-and-server-behavior.md#server-lifecycle)
- [Function codes and PDUs](../../reference/function-codes-and-pdus.md)
- [Lifecycle, concurrency, and resources](../../reference/lifecycle-concurrency-and-resources.md)
- [Server API Javadocs](../../reference/api-reference.md#server-api)
