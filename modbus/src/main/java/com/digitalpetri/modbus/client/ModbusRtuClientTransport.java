package com.digitalpetri.modbus.client;

import com.digitalpetri.modbus.ModbusRtuFrame;

public interface ModbusRtuClientTransport extends ModbusClientTransport<ModbusRtuFrame> {

  /**
   * Reset the frame parser.
   *
   * <p>This method should be called after a timeout or CRC error to reset the parser state.
   */
  void resetFrameParser();

}
