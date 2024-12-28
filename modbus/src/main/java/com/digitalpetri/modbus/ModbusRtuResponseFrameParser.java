package com.digitalpetri.modbus;

import com.digitalpetri.modbus.internal.util.Hex;
import java.nio.ByteBuffer;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

public class ModbusRtuResponseFrameParser {

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

  /** Reset this parser to the {@link Idle} state. */
  public ParserState reset() {
    return state.getAndSet(new Idle());
  }

  public sealed interface ParserState permits Idle, Accumulating, Accumulated, ParseError {

    ParserState parse(byte[] data);
  }

  /** Waiting to receive initial data. */
  public record Idle() implements ParserState {

    @Override
    public ParserState parse(byte[] data) {
      var accumulating = new Accumulating(ByteBuffer.allocate(256), -1);

      return accumulating.parse(data);
    }
  }

  /**
   * Holds a {@link ByteBuffer} that accumulates data and, if known, the expected total length.
   *
   * @param buffer the buffer to accumulate data in.
   * @param expectedLength the expected total length; -1 if not yet known.
   */
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
        case 0x01, 0x02, 0x03, 0x04, 0x17 -> {
          int count = buffer.get(2) & 0xFF;
          int calculatedLength = count + 5;

          if (readableBytes >= calculatedLength) {
            ModbusRtuFrame frame = readFrame(buffer.flip(), calculatedLength);
            return new Accumulated(frame);
          } else {
            return new Accumulating(buffer, calculatedLength);
          }
        }
        case 0x05, 0x06, 0x0F, 0x10 -> {
          // the body of each of these is 4 bytes, so we know the total length
          int fixedLength = 8;
          if (readableBytes >= fixedLength) {
            ModbusRtuFrame frame = readFrame(buffer.flip(), fixedLength);
            return new Accumulated(frame);
          } else {
            return new Accumulating(buffer, fixedLength);
          }
        }
        case 0x16 -> {
          int fixedLength = 10;
          if (readableBytes >= fixedLength) {
            ModbusRtuFrame frame = readFrame(buffer.flip(), fixedLength);
            return new Accumulated(frame);
          } else {
            return new Accumulating(buffer, fixedLength);
          }
        }
        case 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x8F, 0x90, 0x96 -> {
          // error response for one of the supported function codes
          int fixedLength = 5;
          if (readableBytes >= fixedLength) {
            ModbusRtuFrame frame = readFrame(buffer.flip(), fixedLength);
            return new Accumulated(frame);
          } else {
            return new Accumulating(buffer, fixedLength);
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

    @Override
    public String toString() {
      int limit = buffer.limit();
      int position = buffer.position();
      buffer.flip();
      try {
        return new StringJoiner(", ", Accumulating.class.getSimpleName() + "[", "]")
            .add("buffer=" + Hex.format(buffer))
            .add("expectedLength=" + expectedLength)
            .toString();
      } finally {
        buffer.limit(limit).position(position);
      }
    }
  }

  /**
   * Contains an accumulated {@link ModbusRtuFrame}, with a PDU of some function code we understand
   * enough to parse. The CRC has not been validated.
   *
   * @param frame the accumulated {@link ModbusRtuFrame}.
   */
  public record Accumulated(ModbusRtuFrame frame) implements ParserState {

    @Override
    public ParserState parse(byte[] data) {
      return this;
    }
  }

  /**
   * Parser received a function code it doesn't recognize.
   *
   * @param buffer the accumulated data buffer.
   * @param error a message describing the error.
   */
  public record ParseError(ByteBuffer buffer, String error) implements ParserState {

    @Override
    public ParserState parse(byte[] data) {
      buffer.put(data);
      return this;
    }
  }
}
