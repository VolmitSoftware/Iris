package com.volmit.iris.core.nms.v1_20_R2.headless;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.headless.IRegion;
import com.volmit.iris.core.nms.headless.SerializableChunk;
import lombok.NonNull;
import lombok.Synchronized;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;

class Region implements IRegion {
    private final RegionFile regionFile;
    transient long references;

    Region(Path path, Path folder) throws IOException {
        this.regionFile = new RegionFile(path, folder, true);
    }

    @Override
    @Synchronized
    public boolean exists(int x, int z) {
        try (DataInputStream din = regionFile.getChunkDataInputStream(new ChunkPos(x, z))) {
            if (din == null) return false;
            return !"empty".equals(NbtIo.read(din).getString("Status"));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    @Synchronized
    public void write(@NonNull SerializableChunk chunk) throws IOException {
        try (DataOutputStream dos = regionFile.getChunkDataOutputStream(chunk.getPos().convert(ChunkPos::new))) {
            NbtIo.write((CompoundTag) chunk.serialize(), dos);
        }
    }

    @Override
    public void close() {
        --references;
    }

    public boolean remove() {
        if (references > 0) return false;
        try {
            regionFile.close();
        } catch (IOException e) {
            Iris.error("Failed to close region file");
            e.printStackTrace();
        }
        return true;
    }
}
