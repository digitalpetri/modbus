package com.digitalpetri.modbus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.digitalpetri.modbus.tcp.ModbusTcpCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ModbusTcpCodecTest {

  @Test
  void encodeDecodeFrame() {
    var channel = new EmbeddedChannel(new ModbusTcpCodec());

    var frame = new ModbusTcpFrame(
        new MbapHeader(0, 0, 5, 0),
        ByteBuffer.wrap(new byte[]{0x01, 0x02, 0x03, 0x04})
    );

    channel.writeOutbound(frame);
    ByteBuf encoded = channel.readOutbound();
    System.out.println(ByteBufUtil.hexDump(encoded));

    channel.writeInbound(encoded);
    ModbusTcpFrame decoded = channel.readInbound();

    frame.pdu().flip();

    System.out.println(frame);
    System.out.println(decoded);

    assertEquals(frame, decoded);
  }

}
