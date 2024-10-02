package com.volmit.iris.server.packet.init;

import com.volmit.iris.server.packet.Packet;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.IOException;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class InfoPacket implements Packet {
    private int nodeCount = -1;
    private int cps = -1;
    private int generated = -1;

    @Override
    public void read(ByteBuf byteBuf) throws IOException {
        nodeCount = byteBuf.readInt();
        cps = byteBuf.readInt();
        generated = byteBuf.readInt();
    }

    @Override
    public void write(ByteBuf byteBuf) throws IOException {
        byteBuf.writeInt(nodeCount);
        byteBuf.writeInt(cps);
        byteBuf.writeInt(generated);
    }
}
