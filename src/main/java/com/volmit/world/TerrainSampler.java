package com.volmit.world;

import com.volmit.mantle.BlockBox;

/** Read-only host sampling for the same pack snapshot as the invocation.
 * Bounds are world blocks, maxima exclusive. Sampling outside prepared bounds fails with
 * IndexOutOfBoundsException. Views expire with the invocation and must not escape it.
 */
public interface TerrainSampler {
    BlockBox bounds();
    TerrainSample column(int worldX, int worldZ);
    BiomeSample biome(int worldX, int worldY, int worldZ);
}
