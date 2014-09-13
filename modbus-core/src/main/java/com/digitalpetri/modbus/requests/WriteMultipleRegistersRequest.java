package com.digitalpetri.modbus.requests;

import java.nio.ByteBuffer;

import com.digitalpetri.modbus.FunctionCode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * This function code is used to write a block of contiguous registers (1 to 123 registers) in a remote device.
 * <p>
 * The requested written values are specified in the request data field. Data is packed as two bytes per register.
 */
public class WriteMultipleRegistersRequest extends ByteBufModbusRequest {

    private final int address;
    private final int quantity;

    /**
     * @param address  0x0000 to 0xFFFF (0 to 65535)
     * @param quantity 0x0001 to 0x007B (1 to 123)
     * @param values   buffer of at least N bytes, where N = quantity * 2
     */
    public WriteMultipleRegistersRequest(int address, int quantity, byte[] values) {
        this(address, quantity, Unpooled.wrappedBuffer(values));
    }

    /**
     * @param address  0x0000 to 0xFFFF (0 to 65535)
     * @param quantity 0x0001 to 0x007B (1 to 123)
     * @param values   buffer of at least N bytes, where N = quantity * 2
     */
    public WriteMultipleRegistersRequest(int address, int quantity, ByteBuffer values) {
        this(address, quantity, Unpooled.wrappedBuffer(values));
    }

    /**
     * Create a request using a {@link ByteBuf}. The buffer will have its reference count decremented after encoding.
     *
     * @param address  0x0000 to 0xFFFF (0 to 65535)
     * @param quantity 0x0001 to 0x007B (1 to 123)
     * @param values   buffer of at least N bytes, where N = quantity * 2
     */
    public WriteMultipleRegistersRequest(int address, int quantity, ByteBuf values) {
        super(values, FunctionCode.WriteMultipleRegisters);

        this.address = address;
        this.quantity = quantity;
    }

    public int getAddress() {
        return address;
    }

    public int getQuantity() {
        return quantity;
    }

    public ByteBuf getValues() {
        return super.content();
    }

}
