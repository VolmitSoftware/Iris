package com.volmit.iris.server.packet.handle;

import com.volmit.iris.server.packet.Packets;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class Decoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> list) throws Exception {
        var packet = Packets.newPacket(byteBuf.readByte());
        packet.read(byteBuf);
        list.add(packet);
    }
}
