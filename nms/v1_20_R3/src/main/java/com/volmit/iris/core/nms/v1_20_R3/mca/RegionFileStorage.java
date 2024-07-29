package com.volmit.iris.core.nms.v1_20_R3.mca;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;

public final class RegionFileStorage implements AutoCloseable {
    public final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap<>();
    private final Path folder;
    private final boolean sync;

    public RegionFileStorage(Path folder, boolean sync) {
        this.folder = folder;
        this.sync = sync;
    }

    public RegionFile getRegionFile(ChunkPos chunkPos, boolean existingOnly) throws IOException {
        long id = ChunkPos.asLong(chunkPos.getRegionX(), chunkPos.getRegionZ());
        RegionFile regionFile = this.regionCache.getAndMoveToFirst(id);
        if (regionFile != null) {
            return regionFile;
        } else {
            if (this.regionCache.size() >= 256) {
                this.regionCache.removeLast().close();
            }

            FileUtil.createDirectoriesSafe(this.folder);
            Path path = folder.resolve("r." + chunkPos.getRegionX() + "." + chunkPos.getRegionZ() + ".mca");
            if (existingOnly && !Files.exists(path)) {
                return null;
            } else {
                regionFile = new RegionFile(path, this.folder, this.sync);
                this.regionCache.putAndMoveToFirst(id, regionFile);
                return regionFile;
            }
        }
    }

    @Nullable
    public CompoundTag read(ChunkPos chunkPos) throws IOException {
        RegionFile regionFile = this.getRegionFile(chunkPos, true);
        if (regionFile != null) {
            try (DataInputStream datainputstream = regionFile.getChunkDataInputStream(chunkPos)) {
                if (datainputstream != null) {
                    return NbtIo.read(datainputstream);
                }
            }

        }
        return null;
    }

    public void scanChunk(ChunkPos chunkPos, StreamTagVisitor visitor) throws IOException {
        RegionFile regionFile = this.getRegionFile(chunkPos, true);
        if (regionFile != null) {
            try (DataInputStream din = regionFile.getChunkDataInputStream(chunkPos)) {
                if (din != null) {
                    NbtIo.parse(din, visitor, NbtAccounter.unlimitedHeap());
                }
            }
        }
    }

    public void write(ChunkPos chunkPos, @Nullable CompoundTag compound) throws IOException {
        RegionFile regionFile = this.getRegionFile(chunkPos, false);
        Preconditions.checkArgument(regionFile != null, "Failed to find region file for chunk %s", chunkPos);
        if (compound == null) {
            regionFile.clear(chunkPos);
        } else {
            try (DataOutputStream dos = regionFile.getChunkDataOutputStream(chunkPos)) {
                NbtIo.write(compound, dos);
            }
        }
    }

    @Override
    public void close() throws IOException {
        ExceptionCollector<IOException> collector = new ExceptionCollector<>();

        for (RegionFile regionFile : this.regionCache.values()) {
            try {
                regionFile.close();
            } catch (IOException e) {
                collector.add(e);
            }
        }

        collector.throwIfPresent();
    }

    public void flush() throws IOException {
        for (RegionFile regionfile : this.regionCache.values()) {
            regionfile.flush();
        }
    }
}