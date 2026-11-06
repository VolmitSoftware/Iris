package com.volmit.generation;

import com.volmit.mantle.Mantle;
import com.volmit.placement.PlacementTerrain;
import com.volmit.world.TerrainSampler;

/** Per-invocation inputs of the mantle pipeline for one chunk. Must not be retained after execution. */
public interface MantleGenerationContext extends ChunkContext {
    /** The terrain of the chunk and of the larger of the stage's reach and 16 blocks around it on every side, at full
     * height: a mantle stage writes only the mantle, but may look at the terrain around its chunk, such as the heights
     * a cave blends over its borders.
     */
    @Override
    TerrainSampler terrain();

    /** The mantle of the chunk widened by the pipeline's reach, full height (heights 0 up to the dimension height). Writes outside it throw. */
    Mantle mantle();

    /**
     * The mantle of the chunk and its reach as a world to place objects in ({@link com.volmit.placement.Placements}):
     * its blocks are the mantle's planned blocks, and placements write theirs into the mantle.
     */
    PlacementTerrain placementTerrain();
}
