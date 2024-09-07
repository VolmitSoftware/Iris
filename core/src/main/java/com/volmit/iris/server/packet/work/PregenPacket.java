package com.volmit.iris.server.packet.work;

import com.volmit.iris.server.packet.Packet;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.util.UUID;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PregenPacket implements Packet {
    private UUID id = UUID.randomUUID();
    private int x, z;

    @Override
    public void read(ByteBuf byteBuf) throws IOException {
        id = new UUID(byteBuf.readLong(), byteBuf.readLong());
        x = byteBuf.readInt();
        z = byteBuf.readInt();
    }

    @Override
    public void write(ByteBuf byteBuf) throws IOException {
        byteBuf.writeLong(id.getMostSignificantBits());
        byteBuf.writeLong(id.getLeastSignificantBits());
        byteBuf.writeInt(x);
        byteBuf.writeInt(z);
    }
}
