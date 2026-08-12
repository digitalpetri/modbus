# Lifecycle, concurrency, and resources

## Lifecycle summary

| Object | Start/open operation | Stop/close operation | What the stop operation releases |
| --- | --- | --- | --- |
| `ModbusTcpClient` / `ModbusRtuClient` | `connect()`, `connectAsync()`, or `open()` | `disconnect()` or `disconnectAsync()` | The transport connection or serial port |
| `ModbusTcpServer` / `ModbusRtuServer` | `start()` | `stop()` | Bound listener/port and built-in transport client channels |
| `NettyTcpClientTransport` / `NettyRtuClientTransport` | `connect()` | `disconnect()` | Active channel and reconnect state |
| `SerialPortClientTransport` | `connect()` | `disconnect()` | Open jSerialComm port |
| Netty server transports | `bind()` | `unbind()` | Server channel and accepted channel(s) |
| `SerialPortServerTransport` | `bind()` | `unbind()` | Open jSerialComm port |

`ModbusClient.open()` first connects, then returns a `ModbusClientAutoCloseable` whose `close()`
calls `disconnect()`. The client object itself does not implement `AutoCloseable`; use the returned
scope value in try-with-resources.

Stopping a connection or server does not shut down shared executors, schedulers, or Netty event
loops. Those resources can be shared by several library objects.

## Client operations

Every client lifecycle and request operation has a synchronous form and an asynchronous
counterpart operating on the same underlying stage.

| Operation | Synchronous behavior | Asynchronous behavior |
| --- | --- | --- |
| Connect | `connect()` blocks until the transport's connection stage completes | `connectAsync()` returns the transport stage |
| Connect for try-with-resources | `open()` connects, then returns an `AutoCloseable` that calls `disconnect()` | No asynchronous `open` equivalent |
| Check connection | `isConnected()` delegates to the transport's current state | Not applicable |
| Disconnect | `disconnect()` blocks until the transport's disconnection stage completes | `disconnectAsync()` returns the transport stage |
| Send typed request | `send()` and typed helpers block for the response or exception | `sendAsync()` and typed helpers return `CompletionStage` |

See [Client and server behavior](client-and-server-behavior.md#client-request-semantics) for how
synchronous calls wrap failures and how request timeouts interact with in-progress sends.

## Client concurrency

| Client | In-flight behavior | Application rule |
| --- | --- | --- |
| `ModbusTcpClient` | Concurrent pending map and thread-safe transaction sequence; responses correlate by MBAP transaction ID | Concurrent calls are supported by the correlation design; bound concurrency below transaction-ID reuse and endpoint capacity |
| `ModbusRtuClient` | Responses have no transaction ID; correlation is described in [RTU client behavior](client-and-server-behavior.md#rtu-client-behavior) | Keep one request in flight for serial RTU and RTU over TCP |

The typed default request and response serializer singletons are stateless and documented as safe
for concurrent use. A custom serializer must define and enforce its own thread-safety policy.

Synchronous methods block the calling thread on the same asynchronous stage returned by their
asynchronous counterpart. Transport receive and connection-state callbacks are submitted to
configured/shared executors. `CompletionStage` continuations follow `CompletableFuture` execution
rules and may run inline on the thread that completes the stage unless an asynchronous continuation
and executor are selected.

## Server concurrency

| Component | Current concurrency behavior |
| --- | --- |
| `ModbusTcpServer.setModbusServices` | Atomic delegate replacement |
| Netty TCP server transport | Accepts multiple client channels, but submits all frame handling to one serial `ExecutionQueue` |
| Netty RTU-over-TCP server transport | Allows one active client channel and serializes frame handling |
| Serial server transport | Delivers frame handling through a serial `ExecutionQueue` |
| `ProcessImage` | Lock-protected areas plus an optional process-image-wide exclusive transaction lock |

Service methods should not block longer than the request budget. A long call on a built-in server
transport delays subsequent calls. Applications that need external I/O, parallelism, or custom
backpressure should implement and test that behavior deliberately rather than assuming the
transport invokes services concurrently.

## `ProcessImage` concurrency

| Behavior | Contract |
| --- | --- |
| Ordinary transaction | Holds the process-image-wide read lock; multiple ordinary transactions may coexist |
| Exclusive transaction | Holds the process-image-wide write lock; excludes all other transactions |
| Area read | Holds that area's read lock and supplies an unmodifiable map view |
| Area write | Holds that area's write lock and supplies a transaction-scoped mutable map view |
| Nested transaction on same thread | Rejected with `IllegalStateException("nested transaction")` |
| Use after callback/close | Transaction and scoped write maps reject access as closed |
| Modification listener | Invoked while the corresponding area write lock is held |

Do not retain a transaction, supplied map view, or mutable register byte array for unsynchronized
use after its intended scope. Modification listeners should queue blocking work elsewhere so they
do not extend the write-lock hold time.

## Shared resources

| Resource accessor | Used as default by | Release operation |
| --- | --- | --- |
| `Modbus.sharedExecutor()` | Client/server transport callback queues and the default timeout scheduler | `Modbus.releaseSharedResources()` |
| `Modbus.sharedScheduledExecutor()` | Default `TimeoutScheduler` | `Modbus.releaseSharedResources()` |
| `Netty.sharedEventLoop()` | Netty client/server transports | `Netty.releaseSharedResources()` |
| `Netty.sharedWheelTimer()` | Only when explicitly used with `NettyTimeoutScheduler` | `Netty.releaseSharedResources()` |
| netty-channel-fsm `channel-fsm-shared-scheduler` | Netty client reconnection state machines when no scheduler is customized | No `Netty`/`Modbus` release hook; dependency-owned daemon scheduler |

The shared threads are daemon threads, but applications and reloadable ClassLoaders should still
release the resources they own explicitly at final shutdown. The `Netty` and `Modbus` release
methods have overloads accepting a timeout and `TimeUnit`; the no-argument forms wait at most
5 seconds per resource they manage. They do not manage the channel-FSM dependency scheduler.

Applications that require explicit ownership of that scheduler can supply a caller-owned
`ScheduledExecutorService` through the transport configuration:

```java
cfg.setChannelFsmCustomizer(fsm -> fsm.setScheduler(myScheduledExecutor));
```

The caller then shuts that executor down after all clients using it are disconnected.

Use this shutdown order:

1. Stop issuing new requests and application updates.
2. Disconnect every client and stop every server.
3. Shut down any caller-created executor, scheduler, or event-loop group according to its owner's
   policy.
4. Call `Netty.releaseSharedResources()` if the application used Netty defaults.
5. Call `Modbus.releaseSharedResources()` if the application used core defaults.

The library does not shut down an `ExecutorService` or `EventLoopGroup` supplied through a
configuration setter. Supplying one transfers use, not ownership, to the transport.

## Buffer and array ownership

Typed PDU records containing `byte[]` values and raw TCP request/response records do not clone
their arrays. Frame records similarly expose their `ByteBuffer` values. Do not mutate a buffer or
array while an asynchronous send, encode, response handler, or service call may still use it.

## Related material

- [Configure timeouts and reconnection](../how-to/operations/configure-timeouts-and-reconnection.md)
- [Expose data over Modbus TCP](../how-to/servers/expose-data-over-modbus-tcp.md)
- [Client and server behavior](client-and-server-behavior.md)
- [Lifecycle API Javadocs](api-reference.md)
