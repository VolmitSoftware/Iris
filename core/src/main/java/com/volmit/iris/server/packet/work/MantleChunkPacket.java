package com.volmit.iris.server.packet.work;

import com.volmit.iris.server.util.ByteBufUtil;
import com.volmit.iris.server.packet.Packet;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.MantleChunk;
import com.volmit.iris.util.math.Position2;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.*;
import java.util.UUID;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class MantleChunkPacket implements Packet {
    private UUID pregenId;
    private int x, z;
    private MantleChunk chunk;

    @Override
    public void read(ByteBuf byteBuf) throws IOException {
        pregenId = new UUID(byteBuf.readLong(), byteBuf.readLong());
        x = byteBuf.readInt();
        z = byteBuf.readInt();
        int sectionHeight = byteBuf.readInt();
        try (var din = new DataInputStream(new BufferedInputStream(new LZ4BlockInputStream(new ByteArrayInputStream(ByteBufUtil.readBytes(byteBuf)))))) {
            chunk = new MantleChunk(sectionHeight, din);
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to read chunk", e);
        }
    }

    @Override
    public void write(ByteBuf byteBuf) throws IOException {
        byteBuf.writeLong(pregenId.getMostSignificantBits());
        byteBuf.writeLong(pregenId.getLeastSignificantBits());
        byteBuf.writeInt(x);
        byteBuf.writeInt(z);
        byteBuf.writeInt(chunk.getSectionHeight());
        var out = new ByteArrayOutputStream();
        try (var dos = new DataOutputStream(new LZ4BlockOutputStream(out))) {
            chunk.write(dos);
        }
        ByteBufUtil.writeBytes(byteBuf, out.toByteArray());
    }

    @ChunkCoordinates
    public MantleChunkPacket read(Position2 pos, Mantle mantle) {
        this.x = pos.getX();
        this.z = pos.getZ();
        this.chunk = mantle.getChunk(x, z);
        return this;
    }

    public void set(Mantle mantle) {
        mantle.setChunk(x, z, chunk);
    }

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class Request implements Packet {
        private UUID pregenId;
        private KList<Position2> positions = new KList<>();

        @Override
        public void read(ByteBuf byteBuf) throws IOException {
            pregenId = new UUID(byteBuf.readLong(), byteBuf.readLong());
            var count = byteBuf.readInt();
            positions = new KList<>(count);
            for (int i = 0; i < count; i++) {
                positions.add(new Position2(byteBuf.readInt(), byteBuf.readInt()));
            }
        }

        @Override
        public void write(ByteBuf byteBuf) throws IOException {
            byteBuf.writeLong(pregenId.getMostSignificantBits());
            byteBuf.writeLong(pregenId.getLeastSignificantBits());
            byteBuf.writeInt(positions.size());
            for (Position2 p : positions) {
                byteBuf.writeInt(p.getX());
                byteBuf.writeInt(p.getZ());
            }
        }

        @ChunkCoordinates
        public void add(Position2 chunkPos) {
            if (positions == null)
                positions = new KList<>();
            positions.add(chunkPos);
        }
    }
}
