# Transport configuration

Configuration builders expose public fields and fluent setter methods. The setter names below are
the stable, readable form used in examples.

## Netty TCP client transport

`NettyClientTransportConfig` configures both `NettyTcpClientTransport` and
`NettyRtuClientTransport`.

| Setter | Type | Default | Behavior or constraint |
| --- | --- | --- | --- |
| `setHostname` | `String` | None | Required; build throws `NullPointerException` when absent |
| `setPort` | `int` | 502, or 802 when TLS is enabled | Explicit values replace protocol-derived default |
| `setConnectTimeout` | `Duration` | 5 seconds | Applied to each Netty socket connection attempt; does not bound the TLS handshake |
| `setConnectPersistent` | `boolean` | `true` | Continue reconnect state after an initial attempt fails; that attempt's future still fails |
| `setReconnectLazy` | `boolean` | `false` | After loss/failure, wait for connection demand instead of reconnecting immediately |
| `setEventLoopGroup` | Netty `EventLoopGroup` | `Netty.sharedEventLoop()` | Caller-supplied groups remain caller-owned |
| `setExecutor` | `ExecutorService` | `Modbus.sharedExecutor()` | Delivers state and receive callbacks; caller-supplied executor remains caller-owned |
| `setBootstrapCustomizer` | `Consumer<Bootstrap>` | No-op | Advanced Netty bootstrap customization |
| `setPipelineCustomizer` | `Consumer<ChannelPipeline>` | No-op | Runs after TLS/framing handlers are installed |
| `setChannelFsmCustomizer` | `Consumer<ChannelFsmConfigBuilder>` | No-op | Advanced reconnect state-machine configuration |
| `setTlsEnabled` | `boolean` | `false` | Adds TLS before TCP or RTU framing |
| `setKeyManagerFactory` | `KeyManagerFactory` | None | Required when TLS is enabled |
| `setTrustManagerFactory` | `TrustManagerFactory` | None | Required when TLS is enabled |

When TLS is enabled, the client builder validates that both manager factories are present. Both
Netty client transports enable TLS 1.2 and 1.3. `NettyTcpClientTransport` waits for the handshake
before reporting connection success; the RTU-over-TCP transport reports the socket connection and
can surface a handshake failure on subsequent TLS I/O. Neither enables endpoint/hostname
identification automatically. Use the pipeline customizer to configure the `SslHandler` engine's
`SSLParameters`, as shown in [Secure a Modbus TCP client with TLS](../how-to/clients/secure-a-modbus-tcp-client-with-tls.md),
or enforce an equivalent endpoint-identity policy. Netty's current `SslHandler` handshake timeout
defaults to 10 seconds and is separate from `connectTimeout`; call
`SslHandler.setHandshakeTimeoutMillis(...)` from that same customizer to change it.

The current channel-FSM dependency defaults to a 32-second maximum exponential reconnect delay.
`setChannelFsmCustomizer(fsm -> fsm.setMaxReconnectDelaySeconds(n))` changes that ceiling; values
are rounded up to a power of two. The current sequence starts at one second and doubles to the
ceiling.

If the customizer does not set a scheduler, netty-channel-fsm uses its dependency-owned daemon
`channel-fsm-shared-scheduler`. It has no release hook in `Netty` or `Modbus`. Applications that
need explicit or ClassLoader-scoped scheduler ownership can supply their own scheduler through the
same customizer; see [Shared resources](lifecycle-concurrency-and-resources.md#shared-resources).

## Netty server transport

`NettyServerTransportConfig` configures both `NettyTcpServerTransport` and
`NettyRtuServerTransport`.

| Setter | Type | Default | Behavior or constraint |
| --- | --- | --- | --- |
| `setBindAddress` | `String` | `0.0.0.0` | Address supplied to Netty bind |
| `setPort` | `int` | 502, or 802 when TLS is enabled | Explicit values replace protocol-derived default |
| `setEventLoopGroup` | Netty `EventLoopGroup` | `Netty.sharedEventLoop()` | Shared for accept/client channels in the supplied bootstrap |
| `setExecutor` | `ExecutorService` | `Modbus.sharedExecutor()` | Runs serialized frame/service work |
| `setBootstrapCustomizer` | `Consumer<ServerBootstrap>` | No-op | Advanced server bootstrap customization |
| `setPipelineCustomizer` | `Consumer<ChannelPipeline>` | No-op | Runs after built-in TLS/framing handlers are installed |
| `setTlsEnabled` | `boolean` | `false` | Adds TLS and requires client authentication |
| `setKeyManagerFactory` | `KeyManagerFactory` | None | Required when the first TLS client channel initializes |
| `setTrustManagerFactory` | `TrustManagerFactory` | None | Required when the first TLS client channel initializes |

Unlike the client config builder, the server config builder stores missing TLS manager factories
as empty optionals. A TLS-enabled server can bind without both because accepted-channel pipeline
initialization is deferred; the first client connection then fails when that pipeline is created.
Treat both factories as required before `start()` even though the builder does not validate them.

`NettyTcpServerTransport` accepts multiple client channels. `NettyRtuServerTransport` accepts one
active client channel and closes additional channels until that client disconnects.

## Serial transport

`SerialPortTransportConfig` configures both `SerialPortClientTransport` and
`SerialPortServerTransport`.

| Setter | Type | Default | Behavior or constraint |
| --- | --- | --- | --- |
| `setSerialPort` | `String` | None | Required OS-dependent descriptor such as `/dev/ttyUSB0` or `COM3` |
| `setBaudRate` | `int` | 9600 | Must match the peer |
| `setDataBits` | `int` | 8 | Must match the peer |
| `setStopBits` | `int` | `SerialPort.ONE_STOP_BIT` | Use jSerialComm stop-bit constants |
| `setParity` | `int` | `SerialPort.NO_PARITY` | Use jSerialComm parity constants |
| `setRs485Mode` | `boolean` | `false` | Requests RTS-based transmit/receive signaling; driver support required |
| `setRs485RtsActiveHigh` | `boolean` | `true` | RTS transmit polarity; effective only on Linux |
| `setRs485Termination` | `boolean` | `false` | Requests supported bus termination; effective only on Linux |
| `setRs485RxDuringTx` | `boolean` | `false` | Receive while transmitting; effective only on Linux |
| `setRs485DelayBefore` | `int` microseconds | 0 | Delay after transmit enable; effective only on Linux |
| `setRs485DelayAfter` | `int` microseconds | 0 | Delay before transmit disable; effective only on Linux |
| `setExecutor` | `ExecutorService` | `Modbus.sharedExecutor()` | Delivers parsed frame callbacks |

The port object is created lazily. `connect()`/`bind()` opens it and installs a data listener;
`disconnect()`/`unbind()` closes it. Open and close failures report jSerialComm's last error code.
Client write failures also include the code; the serial server logs a generic write error.

## Client protocol configuration

`ModbusClientConfig` applies above the transport.

| Setter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `setRequestTimeout` | `Duration` | 5 seconds | Deadline for each typed or raw client request |
| `setTimeoutScheduler` | `TimeoutScheduler` | Scheduler backed by `Modbus` shared executor and scheduled executor | Creates/cancels per-request timeouts |
| `setRequestSerializer` | `ModbusPduSerializer` | `DefaultRequestSerializer.INSTANCE` | Encodes outgoing typed request PDUs |
| `setResponseSerializer` | `ModbusPduSerializer` | `DefaultResponseSerializer.INSTANCE` | Decodes incoming typed response PDUs |

`NettyTimeoutScheduler` is an alternative adapter over `Netty.sharedWheelTimer()`; the integration
tests use it explicitly. It is not the default client scheduler.

## Server protocol configuration

| Setter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `setRequestSerializer` | `ModbusPduSerializer` | `DefaultRequestSerializer.INSTANCE` | Decodes incoming typed request PDUs |
| `setResponseSerializer` | `ModbusPduSerializer` | `DefaultResponseSerializer.INSTANCE` | Encodes outgoing typed response PDUs |

## Related material

- [Configure timeouts and reconnection](../how-to/operations/configure-timeouts-and-reconnection.md)
- [Secure a Modbus TCP client with TLS](../how-to/clients/secure-a-modbus-tcp-client-with-tls.md)
- [Lifecycle, concurrency, and resources](lifecycle-concurrency-and-resources.md)
- [Transport and security Javadocs](api-reference.md#transport-and-security-api)
