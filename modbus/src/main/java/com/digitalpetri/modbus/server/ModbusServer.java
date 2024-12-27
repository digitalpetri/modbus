package com.digitalpetri.modbus.server;

public interface ModbusServer {

  /**
   * Start the server.
   *
   * @throws Exception if the server could not be started.
   */
  void start() throws Exception;

  /**
   * Stop the server.
   *
   * @throws Exception if the server could not be stopped.
   */
  void stop() throws Exception;

  /**
   * Set the {@link ModbusServices} that will be used to handle requests.
   *
   * @param services the {@link ModbusServices} to use.
   */
  void setModbusServices(ModbusServices services);
}
