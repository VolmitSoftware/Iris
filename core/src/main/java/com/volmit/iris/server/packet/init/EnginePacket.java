package com.volmit.iris.server.packet.init;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineTarget;
import com.volmit.iris.engine.object.IrisWorld;
import com.volmit.iris.server.util.ByteBufUtil;
import com.volmit.iris.server.packet.Packet;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EnginePacket implements Packet {
    private UUID id = UUID.randomUUID();
    private String dimension;
    private long seed;
    private int radius;

    @Override
    public void read(ByteBuf byteBuf) throws IOException {
        id = new UUID(byteBuf.readLong(), byteBuf.readLong());
        dimension = ByteBufUtil.readString(byteBuf);
        seed = byteBuf.readLong();
        radius = byteBuf.readInt();
    }

    @Override
    public void write(ByteBuf byteBuf) throws IOException {
        byteBuf.writeLong(id.getMostSignificantBits());
        byteBuf.writeLong(id.getLeastSignificantBits());
        ByteBufUtil.writeString(byteBuf, dimension);
        byteBuf.writeLong(seed);
        byteBuf.writeInt(radius);
    }

    public Engine getEngine(File base) {
        var data = IrisData.get(new File(base, "iris/pack"));
        var type = data.getDimensionLoader().load(dimension);
        var world = IrisWorld.builder()
                .name(base.getName())
                .seed(seed)
                .worldFolder(base)
                .minHeight(type.getMinHeight())
                .maxHeight(type.getMaxHeight())
                .environment(type.getEnvironment())
                .build();

        return new IrisEngine(new EngineTarget(world, type, data), false);
    }
}
