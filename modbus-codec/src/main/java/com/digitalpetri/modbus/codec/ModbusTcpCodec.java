package com.digitalpetri.modbus.codec;

import java.util.List;

import com.digitalpetri.modbus.ModbusPdu;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusTcpCodec extends ByteToMessageCodec<ModbusTcpPayload> {

    private static final int HeaderLength = MbapHeader.LENGTH;
    private static final int HeaderSize = 6;
    private static final int LengthFieldIndex = 4;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ModbusPduEncoder encoder;
    private final ModbusPduDecoder decoder;

    public ModbusTcpCodec(ModbusPduEncoder encoder, ModbusPduDecoder decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ModbusTcpPayload payload, ByteBuf buffer) throws Exception {
        int headerStartIndex = buffer.writerIndex();
        buffer.writeZero(MbapHeader.LENGTH);

        int pduStartIndex = buffer.writerIndex();
        encoder.encode(payload.getModbusPdu(), buffer);
        int pduLength = buffer.writerIndex() - pduStartIndex;

        MbapHeader header = new MbapHeader(
                payload.getTransactionId(),
                pduLength + 1,
                payload.getUnitId()
        );

        int currentWriterIndex = buffer.writerIndex();
        buffer.writerIndex(headerStartIndex);
        MbapHeader.encode(header, buffer);
        buffer.writerIndex(currentWriterIndex);
    }

//    @Override
//    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
//        int startIndex = buffer.readerIndex();
//
//        while (buffer.readableBytes() >= HeaderLength &&
//                buffer.readableBytes() >= getLength(buffer, startIndex) + HeaderSize) {
//
//            try {
//                MbapHeader mbapHeader = MbapHeader.decode(buffer);
//                ModbusPdu modbusPdu = decoder.decode(buffer);
//
//                if (modbusPdu instanceof UnsupportedPdu) {
//                    // Advance past any bytes we should have read but didn't...
//                    int endIndex = startIndex + getLength(buffer, startIndex) + 6;
//                    buffer.readerIndex(endIndex);
//                }
//
//                out.add(new ModbusTcpPayload(mbapHeader.getTransactionId(), mbapHeader.getUnitId(), modbusPdu));
//            } catch (Exception e) {
//                logger.debug("Error decoding header/pdu: {}", e.getMessage(), e);
//
//                // Advance past any bytes we should have read but didn't...
//                int endIndex = startIndex + getLength(buffer, startIndex) + 6;
//                buffer.readerIndex(endIndex);
//            }
//
//            startIndex = buffer.readerIndex();
//        }
//    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        int startIndex = buffer.readerIndex();

        while (buffer.readableBytes() >= HeaderLength &&
                buffer.readableBytes() >= getLength(buffer, startIndex) + HeaderSize) {

            ByteBuf slice = buffer.readSlice(getLength(buffer, startIndex) + HeaderSize).retain();

            Modbus.sharedExecutor().submit(() -> {
                try {
                    MbapHeader mbapHeader = MbapHeader.decode(slice);
                    ModbusPdu modbusPdu = decoder.decode(slice);

                    ctx.fireChannelRead(new ModbusTcpPayload(mbapHeader.getTransactionId(), mbapHeader.getUnitId(), modbusPdu));
                } catch (Exception e) {
                    logger.debug("Error decoding header/pdu: {}", e.getMessage(), e);
                } finally {
                    ReferenceCountUtil.release(slice);
                }
            });

            startIndex = buffer.readerIndex();
        }
    }

    private int getLength(ByteBuf in, int startIndex) {
        return in.getUnsignedShort(startIndex + LengthFieldIndex);
    }

}
