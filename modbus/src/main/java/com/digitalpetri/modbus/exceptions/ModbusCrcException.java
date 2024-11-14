package com.digitalpetri.modbus.exceptions;

import com.digitalpetri.modbus.ModbusRtuFrame;
import java.io.Serial;

public class ModbusCrcException extends ModbusException {

  @Serial
  private static final long serialVersionUID = -5350159787088895451L;

  private final ModbusRtuFrame frame;

  public ModbusCrcException(ModbusRtuFrame frame) {
    super("CRC mismatch");

    this.frame = frame;
  }

  /**
   * Get the frame that caused the exception.
   *
   * @return the frame that caused the exception.
   */
  public ModbusRtuFrame getFrame() {
    return frame;
  }

}
