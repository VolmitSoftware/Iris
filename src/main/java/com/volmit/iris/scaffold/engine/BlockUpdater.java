package com.volmit.iris.scaffold.engine;

import com.volmit.iris.util.RNG;
import org.bukkit.Chunk;
import org.bukkit.block.data.BlockData;

public interface BlockUpdater {

    public void catchBlockUpdates(int x, int y, int z, BlockData data);

    public void updateChunk(Chunk c);

    public void update(int x, int y, int z, Chunk c, RNG rf);
}
