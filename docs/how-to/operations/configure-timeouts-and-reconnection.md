# Configure timeouts and reconnection

Use this guide to bound connection and request waits and to choose how a Netty TCP client recovers
from connection loss. The same Netty transport configuration is used by Modbus TCP and RTU over
TCP clients. Serial RTU clients have a request timeout but no TCP connect/reconnect state machine.

## Prerequisites

Start with a working client from [Read and write over Modbus TCP](../clients/read-and-write-over-tcp.md).
Choose timeout values from the device's documented response time, network latency, and the
application's recovery budget. A timeout is a failure boundary, not a polling interval.

## Set separate connection and request timeouts

Configure the TCP connection attempt on the transport and the Modbus response wait on the client:

```java
var transport =
    NettyTcpClientTransport.create(
        cfg -> {
          cfg.setHostname("192.0.2.10");
          cfg.setPort(502);
          cfg.setConnectTimeout(Duration.ofSeconds(3));
        });

var client =
    ModbusTcpClient.create(
        transport, cfg -> cfg.setRequestTimeout(Duration.ofSeconds(2)));
```

The connection timeout applies to each Netty connection attempt. The request timeout starts when
the client creates an in-flight request and bounds how long its response promise remains pending
while transport acquisition, send, and response are in progress. Its default is 5 seconds. The
TCP connect timeout also defaults to 5 seconds.

Expiring the promise does not cancel `transport.send(...)` or an underlying `getChannel()` wait.
A request queued during reconnection can therefore be written after the caller has already
received `ModbusTimeoutException`; a response then has no pending promise. Never retry a timed-out
write blindly. Determine whether the operation reached the server or make the application
operation idempotent before retrying.

For TLS, `setConnectTimeout` bounds the socket connection only. `NettyTcpClientTransport.connect()`
then waits for the TLS handshake, whose current Netty `SslHandler` default timeout is 10 seconds.
Set a separate handshake bound in the pipeline customizer when required:

```java
cfg.setPipelineCustomizer(
    pipeline -> pipeline.get(SslHandler.class).setHandshakeTimeoutMillis(5_000));
```

The complete [TLS client guide](../clients/secure-a-modbus-tcp-client-with-tls.md) combines that
setting with endpoint identification.

## Handle request failures

Synchronous typed calls throw three checked exception types. Distinguish them, because the safe
reaction differs:

```java
try {
  var response =
      client.readHoldingRegisters(unitId, new ReadHoldingRegistersRequest(address, 1));
  process(response.registers());
} catch (ModbusTimeoutException e) {
  // No response before the deadline. The request may still have reached the server;
  // never blindly retry a timed-out write.
} catch (ModbusResponseException e) {
  // The server answered with a Modbus exception response.
  System.err.printf(
      "modbus exception: function=0x%02X code=%d%n", e.getFunctionCode(), e.getExceptionCode());
} catch (ModbusExecutionException e) {
  // Connection, transport, serialization, or interruption failure; inspect the cause.
  e.getCause().printStackTrace();
}
```

Asynchronous calls expose the same causes on the returned `CompletionStage` without the
synchronous wrappers:

```java
client
    .readHoldingRegistersAsync(unitId, new ReadHoldingRegistersRequest(address, 1))
    .whenComplete(
        (response, ex) -> {
          if (ex != null) {
            // ex may be the direct failure or wrapped in CompletionException.
            System.err.println("read failed: " + ex);
          } else {
            process(response.registers());
          }
        });
```

See [Errors and exceptions](../../reference/errors-and-exceptions.md) for where each exception
type surfaces.

## Choose a reconnection policy

The two transport flags control different points in the lifecycle:

| Goal | Configuration |
| --- | --- |
| Keep retrying after an initial connection failure and after an unexpected loss | Defaults: `connectPersistent=true`, `reconnectLazy=false` |
| Fail the initial `connect()` attempt and stop | `setConnectPersistent(false)` |
| After a loss, wait until `connect()` or a send needs a channel before reconnecting | `setReconnectLazy(true)` |
| Stop reconnection intentionally | Call `client.disconnect()` |

For example, to fail fast on the initial connection and reconnect only on demand after a later
loss:

```java
var transport =
    NettyTcpClientTransport.create(
        cfg -> {
          cfg.setHostname("192.0.2.10");
          cfg.setConnectPersistent(false);
          cfg.setReconnectLazy(true);
        });
```

With persistent, non-lazy defaults, failed reconnects use an exponential delay of 1, 2, 4, 8, 16,
then 32 seconds. The advanced state-machine customizer can change the maximum delay:

```java
cfg.setChannelFsmCustomizer(fsm -> fsm.setMaxReconnectDelaySeconds(8));
```

Even with persistent reconnection enabled, the initial `connect()` future completes exceptionally
if its own attempt fails; the state machine keeps retrying in the background. Handle that failure;
do not assume a successful return merely because retries remain enabled.

## Observe connection changes

Register a listener on the concrete Netty transport when the application must update health state
or metrics:

```java
transport.addConnectionListener(
    new NettyTcpClientTransport.ConnectionListener() {
      @Override
      public void onConnection() {
        System.out.println("Modbus connection established");
      }

      @Override
      public void onConnectionLost() {
        System.out.println("Modbus connection lost; transport policy handles reconnection");
      }
    });
```

Callbacks are delivered on the configured transport executor. Keep them short and do not initiate
a second reconnect loop from `onConnectionLost()`.

## Clean up intentionally

Call `disconnect()` during planned application shutdown. This stops the transport state machine's
reconnection cycle. Release `Netty` and `Modbus` shared resources only after every client and
server in the application is stopped.

## Verify the policy

1. Connect to a reachable test server and confirm `isConnected()` becomes true.
2. Stop the server without disconnecting the client and observe `onConnectionLost()`.
3. Restart the server. With default non-lazy reconnection, observe `onConnection()` without
   manually calling `connect()`.
4. Repeat with `reconnectLazy=true`; confirm reconnection begins only when the client connects or a
   request needs the channel.
5. Point the client at a non-responding unit on a reachable server and confirm the configured
   request timeout produces `ModbusTimeoutException`.

## Common failure symptoms

| Symptom | Check |
| --- | --- |
| `connect()` failed but connection appears later | Persistent initial retries are enabled; the failed attempt's future still reports its failure |
| Client never reconnects after a loss | An explicit `disconnect()` occurred, lazy mode awaits demand, or the endpoint remains unavailable |
| Requests wait longer than the request timeout | Confirm the timeout was configured on `ModbusTcpClient`, not only the transport's connect timeout |
| Reconnect loop is too aggressive or too slow | Set the state machine's maximum reconnect delay and inspect connection logs/listeners |
| Application exits but resources are still in use | Disconnect clients/stop servers before releasing shared resources |

## Related reference

- [Transport configuration](../../reference/transport-configuration.md)
- [Client and server behavior](../../reference/client-and-server-behavior.md)
- [Errors and exceptions](../../reference/errors-and-exceptions.md)
- [Lifecycle, concurrency, and resources](../../reference/lifecycle-concurrency-and-resources.md)
