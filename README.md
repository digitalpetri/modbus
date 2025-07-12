[![Maven Central](https://img.shields.io/maven-central/v/com.digitalpetri.modbus/modbus.svg)](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.digitalpetri.modbus%22%20AND%20a%3A%22modbus%22)

A modern, performant, easy to use client and server implementation of Modbus, supporting:
- Modbus TCP
- Modbus TCP Security (Modbus TCP with TLS)
- Modbus RTU on Serial
- Modbus RTU on TCP

### Quick Start Examples

#### Modbus TCP Client
```java
var transport = NettyTcpClientTransport.create(cfg -> {
  cfg.setHostname("172.17.0.2");
  cfg.setPort(502);
});

var client = ModbusTcpClient.create(transport);
client.connect();

ReadHoldingRegistersResponse response = client.readHoldingRegisters(
    1,
    new ReadHoldingRegistersRequest(0, 10)
);

System.out.println("Response: " + response);
```

#### Modbus RTU on Serial Client
```java
var transport = SerialPortClientTransport.create(cfg -> {
  cfg.setSerialPort("/dev/ttyUSB0");
  cfg.setBaudRate(115200);
  cfg.setDataBits(8);
  cfg.setParity(SerialPort.NO_PARITY);
  cfg.setStopBits(SerialPort.TWO_STOP_BITS);
});

var client = ModbusRtuClient.create(transport);
client.connect();

client.readHoldingRegisters(
    1,
    new ReadHoldingRegistersRequest(0, 10)
);

System.out.println("Response: " + response);
```

### Maven

#### Modbus TCP

```xml
<dependency>
    <groupId>com.digitalpetri.modbus</groupId>
    <artifactId>modbus-tcp</artifactId>
    <version>2.1.0</version>
</dependency>
```

#### Modbus Serial
```xml
<dependency>
    <groupId>com.digitalpetri.modbus</groupId>
    <artifactId>modbus-serial</artifactId>
    <version>2.1.0</version>
</dependency>
```

### Features

#### Supported Function Codes
Code     | Function | Client | Server
-------- | -------- | ------ | ------
0x01     | Read Coils | ✅ | ✅
0x02     | Read Discrete Inputs | ✅ | ✅
0x03     | Read Holding Registers | ✅ | ✅
0x04     | Read Input Registers | ✅ | ✅
0x05     | Write Single Coil | ✅ | ✅
0x06     | Write Single Register | ✅ | ✅
0x0F     | Write Multiple Coils | ✅ | ✅
0x10     | Write Multiple Registers | ✅ | ✅
0x16     | Mask Write Register | ✅ | ✅
0x17     | Read/Write Multiple Registers | ✅ | ✅

- raw/custom PDUs on Modbus/TCP
- broadcast messages on Modbus/RTU
- pluggable codec implementations
- pluggable transport implementations

### License

Eclipse Public License - v 2.0
