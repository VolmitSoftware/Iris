package com.volmit.world;

import com.volmit.mantle.BlockBox;

/** Read-only host sampling for the same pack snapshot as the invocation.
 * X and Z are world coordinates; Y is the height above the dimension's bottom (0 = bottom).
 * Bounds use the same coordinates, maxima exclusive. Sampling outside prepared bounds fails with
 * IndexOutOfBoundsException. Views expire with the invocation and must not escape it.
 */
public interface TerrainSampler {
    BlockBox bounds();
    TerrainSample column(int worldX, int worldZ);
    BiomeSample biome(int worldX, int y, int worldZ);
}
