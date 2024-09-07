package com.volmit.iris.server.packet.handle;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class Prepender extends MessageToByteEncoder<ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        int i = in.readableBytes();
        out.writeInt(i);
        out.writeBytes(in, in.readerIndex(), i);
    }
}
