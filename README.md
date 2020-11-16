[![Maven Central](https://img.shields.io/maven-central/v/com.digitalpetri.modbus/modbus.svg)](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.digitalpetri.modbus%22%20AND%20a%3A%22modbus%22)

High-performance, non-blocking, zero-buffer-copying Modbus for Java.

Quick Start
--------
```java
ModbusTcpMasterConfig config = new ModbusTcpMasterConfig.Builder("localhost").build();
ModbusTcpMaster master = new ModbusTcpMaster(config);

master.connect();

CompletableFuture<ReadHoldingRegistersResponse> future =
    master.sendRequest(new ReadHoldingRegistersRequest(0, 10), 0);

future.thenAccept(response -> {
    System.out.println("Response: " + ByteBufUtil.hexDump(response.getRegisters()));

    ReferenceCountUtil.release(response);
});
```

See the examples project for more.

Maven
--------

#### Modbus Master

```xml
<dependency>
    <groupId>com.digitalpetri.modbus</groupId>
    <artifactId>modbus-master-tcp</artifactId>
    <version>1.2.0</version>
</dependency>
```

#### Modbus Slave
```xml
<dependency>
    <groupId>com.digitalpetri.modbus</groupId>
    <artifactId>modbus-slave-tcp</artifactId>
    <version>1.2.0</version>
</dependency>
```
  
Supported Function Codes
-------
Code     | Function
-------- | ----
0x01     | Read Coils
0x02     | Read Discrete Inputs
0x03     | Read Holding Registers
0x04     | Read Input Registers
0x05     | Write Single Coil
0x06     | Write Single Register
0x0F     | Write Multiple Coils
0x10     | Write Multiple Registers
0x16     | Mask Write Register
0x17     | Read/Write Multiple Registers

Get Help
--------

See the examples project or contact kevinherron@gmail.com for more information.


License
--------

Apache License, Version 2.0
