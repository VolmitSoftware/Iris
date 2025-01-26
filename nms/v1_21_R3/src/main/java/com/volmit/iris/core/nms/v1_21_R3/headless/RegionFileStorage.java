package com.volmit.iris.core.nms.v1_21_R3.headless;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RegionFileStorage implements AutoCloseable {
    private static final RegionStorageInfo info = new RegionStorageInfo("headless", Level.OVERWORLD, "headless");
    private final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap<>();
    private final Path folder;

    public RegionFileStorage(File folder) {
        this.folder = new File(folder, "region").toPath();
    }

    public RegionFile getRegionFile(ChunkPos chunkPos, boolean existingOnly) throws IOException {
        long id = ChunkPos.asLong(chunkPos.getRegionX(), chunkPos.getRegionZ());
        RegionFile regionFile;
        synchronized (this.regionCache) {
            regionFile = this.regionCache.getAndMoveToFirst(id);
        }
        if (regionFile != null) {
            return regionFile;
        } else {
            if (this.regionCache.size() >= 256) {
                synchronized (this.regionCache) {
                    this.regionCache.removeLast().close();
                }
            }

            FileUtil.createDirectoriesSafe(this.folder);
            Path path = folder.resolve("r." + chunkPos.getRegionX() + "." + chunkPos.getRegionZ() + ".mca");
            if (existingOnly && !Files.exists(path)) {
                return null;
            } else {
                regionFile = new RegionFile(info, path, this.folder, true);
                synchronized (this.regionCache) {
                    this.regionCache.putAndMoveToFirst(id, regionFile);
                }
                return regionFile;
            }
        }
    }

    @Nullable
    public CompoundTag read(ChunkPos chunkPos) throws IOException {
        RegionFile regionFile = this.getRegionFile(chunkPos, true);
        if (regionFile == null) return null;

        try (DataInputStream din = regionFile.getChunkDataInputStream(chunkPos)) {
            if (din == null) return null;
            return NbtIo.read(din);
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
}
