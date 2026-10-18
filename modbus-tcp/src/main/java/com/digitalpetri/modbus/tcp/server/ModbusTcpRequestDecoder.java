package com.digitalpetri.modbus.tcp.server;

import static com.digitalpetri.modbus.tcp.ModbusTcpCodec.MBAP_LENGTH_FIELD_OFFSET;
import static com.digitalpetri.modbus.tcp.ModbusTcpCodec.MBAP_TOTAL_LENGTH;

import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusTcpFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes {@link ModbusTcpFrame} requests for one server connection.
 *
 * <p>When bytes arrive after at least {@code partialFrameTimeout} of silence, any incomplete frame
 * buffered from before the silence is discarded, and the new bytes start a new frame.
 */
final class ModbusTcpRequestDecoder extends ByteToMessageDecoder {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final long partialFrameTimeoutNanos;
  private final LongSupplier nanoTime;

  private long lastReceivedNanos;

  ModbusTcpRequestDecoder(Duration partialFrameTimeout, LongSupplier nanoTime) {
    this.partialFrameTimeoutNanos = partialFrameTimeout.toNanos();
    this.nanoTime = nanoTime;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof ByteBuf) {
      long now = nanoTime.getAsLong();
      ByteBuf partialFrame = internalBuffer();

      if (partialFrame.isReadable() && now - lastReceivedNanos >= partialFrameTimeoutNanos) {
        logger.debug(
            "Discarding {} bytes of an incomplete request frame after silence",
            partialFrame.readableBytes());

        partialFrame.skipBytes(partialFrame.readableBytes());
      }

      lastReceivedNanos = now;
    }

    super.channelRead(ctx, msg);
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    if (in.readableBytes() >= MBAP_TOTAL_LENGTH) {
      int frameLength = in.getUnsignedShort(in.readerIndex() + MBAP_LENGTH_FIELD_OFFSET) + 6;

      if (in.readableBytes() >= frameLength) {
        ByteBuffer buffer = ByteBuffer.allocate(frameLength);
        in.readBytes(buffer);
        buffer.flip();

        MbapHeader header = MbapHeader.Serializer.decode(buffer);
        ByteBuffer pdu = buffer.slice();

        out.add(new ModbusTcpFrame(header, pdu));
      }
    }
  }
}
