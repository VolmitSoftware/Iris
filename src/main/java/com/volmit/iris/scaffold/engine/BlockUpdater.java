package com.volmit.iris.scaffold.engine;

import com.volmit.iris.util.RNG;
import org.bukkit.Chunk;
import org.bukkit.block.data.BlockData;

public interface BlockUpdater {

    void catchBlockUpdates(int x, int y, int z, BlockData data);

    void updateChunk(Chunk c);

    void update(int x, int y, int z, Chunk c, RNG rf);
}
