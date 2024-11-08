package com.digitalpetri.modbus.tcp;

import com.digitalpetri.modbus.MbapHeader;
import com.digitalpetri.modbus.ModbusTcpFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import java.nio.ByteBuffer;
import java.util.List;

public class ModbusTcpCodec extends ByteToMessageCodec<ModbusTcpFrame> {

  public static final int MBAP_TOTAL_LENGTH = 7;
  public static final int MBAP_LENGTH_FIELD_OFFSET = 4;

  @Override
  protected void encode(ChannelHandlerContext ctx, ModbusTcpFrame msg, ByteBuf out) {
    var buffer = ByteBuffer.allocate(MBAP_TOTAL_LENGTH + msg.pdu().limit() - msg.pdu().position());
    MbapHeader.Serializer.encode(msg.header(), buffer);
    buffer.put(msg.pdu());

    buffer.flip();
    out.writeBytes(buffer);
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
