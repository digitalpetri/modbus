# Communicate over serial RTU

Use this guide to send Modbus RTU requests through a local serial port. It applies when the Java
process is directly connected to an RS-232 or RS-485 adapter. It does not apply to an RTU-over-TCP
gateway.

## Prerequisites

You need:

- Java 17 or newer and the `modbus-serial` dependency;
- the operating-system port name, such as `/dev/ttyUSB0` or `COM3`;
- permission for the process to open that port;
- matching baud rate, data bits, parity, and stop bits; and
- the target unit ID and zero-based data address.

For RS-485, also confirm the wiring, biasing, termination, and adapter direction-control behavior.
The library can request RTS-based RS-485 mode, but the operating system, driver, and adapter must
support it.

## Configure the transport and send a request

This standalone program reads two holding registers starting at offset 0 from unit 1. Match every
serial parameter to the remote device.

```java
package example;

import com.digitalpetri.modbus.Modbus;
import com.digitalpetri.modbus.client.ModbusRtuClient;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.serial.client.SerialPortClientTransport;
import com.fazecast.jSerialComm.SerialPort;
import java.time.Duration;
import java.util.HexFormat;

public final class SerialRtuExample {

  public static void main(String[] args) throws Exception {
    var transport =
        SerialPortClientTransport.create(
            cfg -> {
              cfg.setSerialPort("/dev/ttyUSB0");
              cfg.setBaudRate(115200);
              cfg.setDataBits(8);
              cfg.setParity(SerialPort.NO_PARITY);
              cfg.setStopBits(SerialPort.ONE_STOP_BIT);
              // Enable only when the driver and adapter require RTS direction control:
              // cfg.setRs485Mode(true);
            });

    var client =
        ModbusRtuClient.create(
            transport, cfg -> cfg.setRequestTimeout(Duration.ofSeconds(2)));

    try (var ignored = client.open()) {
      var response =
          client.readHoldingRegisters(1, new ReadHoldingRegistersRequest(0, 2));

      System.out.println("register bytes = " + HexFormat.of().formatHex(response.registers()));
    } finally {
      // Do this only when the whole application is finished with the library.
      Modbus.releaseSharedResources();
    }
  }
}
```

The client adds the RTU unit byte and CRC. Pass only the typed PDU request and the unit ID; do not
construct an RTU frame yourself.

## Clean up the port

Closing the value returned by `client.open()` calls `disconnect()` and closes the serial port.
Always disconnect in a `finally` block or use try-with-resources as above. Releasing `Modbus`
shared resources is a process-level shutdown action, not a per-request action.

## Verify the result

With a responding unit, the program prints four register bytes, for example:

```text
register bytes = 12345678
```

The byte sequence alone does not establish a 32-bit numeric interpretation. Confirm byte and word
ordering against the device map.

## Common failure symptoms

| Symptom | Check |
| --- | --- |
| Port cannot be opened | Port name, OS permissions, another process holding the port, and driver state |
| Every request times out | Unit ID, wiring polarity, baud/data/parity/stop settings, and RS-485 direction control |
| `ModbusExecutionException` caused by `ModbusCrcException` | Noise, termination, grounding, baud/parity mismatch, or bytes from another device |
| Unit/function mismatch (reported as `slave id mismatch` / `function code mismatch`) or synchronization error | More than one in-flight RTU request, late responses, or another client on the bus |
| Bytes are stable but values are wrong | Device-specific register byte/word ordering and scaling |

Keep only one request in flight on an RTU client. RTU responses do not contain the Modbus TCP
transaction identifier used to correlate concurrent requests.

Synchronous typed calls wrap a CRC failure in `ModbusExecutionException`; inspect its cause. The
corresponding asynchronous stage completes exceptionally with `ModbusCrcException` directly.

## Related reference

- [Addressing, unit IDs, and data](../../concepts/addressing-unit-ids-and-data.md)
- [Transports, framing, and security](../../concepts/transports-framing-and-security.md)
- [Transport configuration](../../reference/transport-configuration.md#serial-transport)
- [Errors and exceptions](../../reference/errors-and-exceptions.md)
- [Lifecycle, concurrency, and resources](../../reference/lifecycle-concurrency-and-resources.md)
