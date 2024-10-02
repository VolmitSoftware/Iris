package com.volmit.iris.server.packet.handle;

import com.volmit.iris.server.packet.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class Encoder extends MessageToByteEncoder<Packet> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf byteBuf) throws Exception {
        byteBuf.writeByte(packet.getType().getId());
        packet.write(byteBuf);
    }
}
