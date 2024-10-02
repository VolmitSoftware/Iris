package com.volmit.iris.server.packet.work;

import com.volmit.iris.server.packet.Packet;
import io.netty.buffer.ByteBuf;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.util.UUID;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DonePacket implements Packet {
    private UUID id = UUID.randomUUID();

    @Override
    public void read(ByteBuf byteBuf) throws IOException {
        id = new UUID(byteBuf.readLong(), byteBuf.readLong());
    }

    @Override
    public void write(ByteBuf byteBuf) throws IOException {
        byteBuf.writeLong(id.getMostSignificantBits());
        byteBuf.writeLong(id.getLeastSignificantBits());
    }
}
