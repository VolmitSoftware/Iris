package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.math.RNG;

public interface IrisStaticPlacement {
    int x();
    int y();
    int z();

    @ChunkCoordinates
    default boolean shouldPlace(int chunkX, int chunkZ) {
        return x() >> 4 == chunkX && z() >> 4 == chunkZ;
    }

    void place(MantleWriter writer, RNG rng, IrisData data);
}
