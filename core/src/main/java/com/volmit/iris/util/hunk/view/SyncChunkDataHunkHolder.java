package com.volmit.iris.util.hunk.view;

import com.volmit.iris.Iris;
import com.volmit.iris.util.hunk.storage.ArrayHunk;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

public class SyncChunkDataHunkHolder extends ArrayHunk<BlockData> {
    private static final BlockData AIR = Material.AIR.createBlockData();
    private final ChunkGenerator.ChunkData chunk;
    private final Thread mainThread = Thread.currentThread();

    public SyncChunkDataHunkHolder(ChunkGenerator.ChunkData chunk) {
        super(16, chunk.getMaxHeight() - chunk.getMinHeight(), 16);
        this.chunk = chunk;
    }

    @Override
    public void setRaw(int x, int y, int z, BlockData data) {
        if (Thread.currentThread() != mainThread)
            Iris.warn("SyncChunkDataHunkHolder is not on the main thread");
        super.setRaw(x, y, z, data);
    }

    @Override
    public BlockData getRaw(int x, int y, int z) {
        if (Thread.currentThread() != mainThread)
            Iris.warn("SyncChunkDataHunkHolder is not on the main thread");
        BlockData b = super.getRaw(x, y, z);

        return b != null ? b : AIR;
    }

    public void apply() {
        for (int i = getHeight()-1; i >= 0; i--) {
            for (int j = 0; j < getWidth(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    BlockData b = super.getRaw(j, i, k);

                    if (b != null) {
                        chunk.setBlock(j, i + chunk.getMinHeight(), k, b);
                    }
                }
            }
        }
    }
}
