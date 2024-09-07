package com.volmit.iris.server.packet.work;

import com.volmit.iris.server.util.ByteBufUtil;
import com.volmit.iris.server.packet.Packet;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkPacket implements Packet {
    private UUID pregenId;
    private int x, z;
    private byte[] data;

    @Override
    public void read(ByteBuf byteBuf) throws IOException {
        pregenId = new UUID(byteBuf.readLong(), byteBuf.readLong());
        x = byteBuf.readInt();
        z = byteBuf.readInt();
        data = ByteBufUtil.readBytes(byteBuf);
    }

    @Override
    public void write(ByteBuf byteBuf) throws IOException {
        byteBuf.writeLong(pregenId.getMostSignificantBits());
        byteBuf.writeLong(pregenId.getLeastSignificantBits());
        byteBuf.writeInt(x);
        byteBuf.writeInt(z);
        ByteBufUtil.writeBytes(byteBuf, data);
    }
}
