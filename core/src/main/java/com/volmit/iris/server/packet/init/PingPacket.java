package com.volmit.iris.server.packet.init;

import com.volmit.iris.server.packet.Packet;
import com.volmit.iris.server.util.ByteBufUtil;
import com.volmit.iris.util.collection.KList;
import io.netty.buffer.ByteBuf;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.UUID;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PingPacket implements Packet {
    private UUID id = UUID.randomUUID();
    private KList<String> version = new KList<>();

    @Override
    public void read(ByteBuf byteBuf) throws IOException {
        id = new UUID(byteBuf.readLong(), byteBuf.readLong());
        int size = byteBuf.readInt();

        version = new KList<>();
        for (int i = 0; i < size; i++) {
            version.add(ByteBufUtil.readString(byteBuf));
        }
    }

    @Override
    public void write(ByteBuf byteBuf) throws IOException {
        byteBuf.writeLong(id.getMostSignificantBits());
        byteBuf.writeLong(id.getLeastSignificantBits());

        byteBuf.writeInt(version.size());
        for (String s : version) {
            ByteBufUtil.writeString(byteBuf, s);
        }
    }

    public PingPacket setBukkit() {
        this.version = new KList<>(Bukkit.getBukkitVersion().split("-")[0]);
        return this;
    }

    public PingPacket setVersion(String version) {
        this.version = new KList<>(version);
        return this;
    }

    public PingPacket setVersion(KList<String> version) {
        this.version = version;
        return this;
    }
}
