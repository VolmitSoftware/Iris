package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.B;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import org.bukkit.block.data.BlockData;

public interface EngineDecorator extends EngineComponent {
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1, Hunk<BlockData> data, IrisBiome biome, int height, int max);

    default void decorate(int x, int z, int realX, int realZ, Hunk<BlockData> data, IrisBiome biome, int height, int max)
    {
        decorate(x, z, realX, realX, realX, realZ, realZ, realZ, data, biome, height, max);
    }

    default boolean canGoOn(BlockData decorant, BlockData atop)
    {
        return B.canPlaceOnto(decorant.getMaterial(), atop.getMaterial());
    }
}
