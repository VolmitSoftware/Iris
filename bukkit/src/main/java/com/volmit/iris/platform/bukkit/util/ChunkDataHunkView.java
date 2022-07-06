package com.volmit.iris.platform.bukkit.util;

import art.arcane.spatial.hunk.Hunk;
import com.volmit.iris.platform.PlatformBlock;
import com.volmit.iris.platform.bukkit.wrapper.BukkitBlock;
import org.bukkit.generator.ChunkGenerator;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkDataHunkView implements Hunk<PlatformBlock> {
    private final ChunkGenerator.ChunkData chunk;

    public ChunkDataHunkView(ChunkGenerator.ChunkData chunk) {
        this.chunk = chunk;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getDepth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return chunk.getMaxHeight() - chunk.getMinHeight();
    }

    @Override
    public synchronized void set(int x1, int y1, int z1, int x2, int y2, int z2, PlatformBlock t) {
        if(t == null) {
            return;
        }

        chunk.setRegion(x1, y1 + chunk.getMinHeight(), z1, x2, y2 + chunk.getMinHeight(), z2, ((BukkitBlock)t).getDelegate());
    }

    @Override
    public synchronized void setRaw(int x, int y, int z, PlatformBlock t) {
        if(t == null) {
            return;
        }

        chunk.setBlock(x, y + chunk.getMinHeight(), z, ((BukkitBlock)t).getDelegate());
    }

    @Override
    public PlatformBlock getRaw(int x, int y, int z) {
        return chunk.getBlockData(x, y + chunk.getMinHeight(), z).bukkitBlock();
    }
}