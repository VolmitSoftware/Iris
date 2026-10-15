package com.volmit.generation;

import com.volmit.config.FeatureSettings;
import com.volmit.mantle.Mantle;
import com.volmit.world.TerrainSampler;

/** Per-invocation inputs of the mantle pipeline for one chunk. Must not be retained after execution. */
public interface MantleGenerationContext extends InvocationContext {
    /** The chunk's first block along X, in world coordinates. Heights are above the dimension's bottom. */
    int originX();

    /** The chunk's first block along Z, in world coordinates. */
    int originZ();

    /** The terrain of the chunk and of the larger of the stage's reach and 16 blocks around it on every side, at full
     * height: a mantle stage writes only the mantle, but may look at the terrain around its chunk, such as the heights
     * a cave blends over its borders.
     */
    TerrainSampler terrain();

    FeatureSettings settings();

    /** The mantle of the chunk widened by the pipeline's reach, full height (heights 0 up to the dimension height). Writes outside it throw. */
    Mantle mantle();
}
