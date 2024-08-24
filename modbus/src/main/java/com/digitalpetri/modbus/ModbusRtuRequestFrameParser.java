package com.digitalpetri.modbus;


import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

public class ModbusRtuRequestFrameParser {

  private final AtomicReference<ParserState> state = new AtomicReference<>(new Idle());

  /**
   * Parse incoming data and return the updated {@link ParserState}.
   *
   * @param data the incoming data to parse.
   * @return the updated {@link ParserState}.
   */
  public ParserState parse(byte[] data) {
    return state.updateAndGet(s -> s.parse(data));
  }

  /**
   * Get the current {@link ParserState}.
   *
   * @return the current {@link ParserState}.
   */
  public ParserState getState() {
    return state.get();
  }

  /**
   * Reset this parser to the {@link Idle} state.
   */
  public ParserState reset() {
    return state.getAndSet(new Idle());
  }

  public sealed interface ParserState
      permits Idle, Accumulating, Accumulated, ParseError {

    ParserState parse(byte[] data);

  }

  /**
   * Waiting to receive initial data.
   */
  public record Idle() implements ParserState {

    @Override
    public ParserState parse(byte[] data) {
      var accumulating = new Accumulating(ByteBuffer.allocate(256), -1);

      return accumulating.parse(data);
    }
  }

  public record Accumulating(ByteBuffer buffer, int expectedLength) implements ParserState {

    @Override
    public ParserState parse(byte[] data) {
      buffer.put(data);

      int readableBytes = buffer.position();

      if (readableBytes < 3 || readableBytes < expectedLength) {
        return this;
      }

      byte fcb = buffer.get(1);

      switch (fcb & 0xFF) {
        case 0x01, 0x02, 0x03, 0x04, 0x05, 0x06 -> {
          int fixedLength = 1 + (1 + 2 + 2) + 2;
          if (readableBytes >= fixedLength) {
            ModbusRtuFrame frame = readFrame(buffer.flip(), fixedLength);
            return new Accumulated(frame);
          } else {
            return new Accumulating(buffer, fixedLength);
          }
        }

        case 0x0F, 0x10 -> {
          int minimum = 1 + (1 + 2 + 2 + 1) + 2;
          if (readableBytes >= minimum) {
            int byteCount = buffer.get(6);
            if (readableBytes >= minimum + byteCount) {
              ModbusRtuFrame frame = readFrame(buffer.flip(), minimum + byteCount);
              return new Accumulated(frame);
            } else {
              return new Accumulating(buffer, minimum + byteCount);
            }
          } else {
            return new Accumulating(buffer, minimum);
          }
        }

        case 0x16 -> {
          int fixedLength = 1 + (1 + 2 + 2 + 2) + 2;
          if (readableBytes >= fixedLength) {
            ModbusRtuFrame frame = readFrame(buffer.flip(), fixedLength);
            return new Accumulated(frame);
          } else {
            return new Accumulating(buffer, fixedLength);
          }
        }

        case 0x17 -> {
          int minimum = 1 + (1 + 2 + 2 + 2 + 1 + 2) + 2;
          if (readableBytes >= minimum) {
            int byteCount = buffer.get(10);
            if (readableBytes >= minimum + byteCount) {
              ModbusRtuFrame frame = readFrame(buffer.flip(), minimum + byteCount);
              return new Accumulated(frame);
            } else {
              return new Accumulating(buffer, minimum + byteCount);
            }
          } else {
            return new Accumulating(buffer, minimum);
          }
        }

        default -> {
          return new ParseError(buffer, "unsupported function code: 0x%02X".formatted(fcb));
        }
      }
    }

    private static ModbusRtuFrame readFrame(ByteBuffer buffer, int length) {
      int slaveId = buffer.get() & 0xFF;

      ByteBuffer payload = buffer.slice(buffer.position(), length - 3);
      ByteBuffer crc = buffer.slice(buffer.position() + length - 3, 2);

      return new ModbusRtuFrame(slaveId, payload, crc);
    }
  }

  public record Accumulated(ModbusRtuFrame frame) implements ParserState {

    @Override
    public ParserState parse(byte[] data) {
      return this;
    }
  }

  public record ParseError(ByteBuffer buffer, String message) implements ParserState {

    @Override
    public ParserState parse(byte[] data) {
      buffer.put(data);
      return this;
    }
  }
}
