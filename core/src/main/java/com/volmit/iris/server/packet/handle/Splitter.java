package com.volmit.iris.server.packet.handle;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class Splitter extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> list) throws Exception {
        if (!byteBuf.isReadable(4))
            return;
        byteBuf.markReaderIndex();

        byte[] bytes = new byte[4];
        byteBuf.readBytes(bytes);
        var buffer = Unpooled.wrappedBuffer(bytes);
        try {
            int j = buffer.readInt();
            if (byteBuf.readableBytes() >= j) {
                list.add(byteBuf.readBytes(j));
                return;
            }

            byteBuf.resetReaderIndex();
        } finally {
            buffer.release();
        }
    }
}
