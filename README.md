High-performance, non-blocking, zero-buffer-copying Modbus implementation for Java.

Quick Start
--------
  ```java
  ModbusTcpMasterConfig config = new ModbusTcpMasterConfig.Builder("localhost").build();
  ModbusTcpMaster master = new ModbusTcpMaster(config);

  CompletableFuture<ReadHoldingRegistersResponse> future =
          master.sendRequest(new ReadHoldingRegistersRequest(0, 10), 0);

  future.thenAccept(response -> {
      System.out.println("Response: " + ByteBufUtil.hexDump(response.getRegisters()));

      ReferenceCountUtil.release(response);
  });
  ```
  
  See the examples project for more.
  
Get Help
--------

See the examples project or contact kevinherron@gmail.com for more information.


License
--------

Apache License, Version 2.0
