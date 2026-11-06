package com.volmit.generation;

import com.volmit.config.FeatureSettings;
import com.volmit.geometry.BlockBox;
import com.volmit.world.TerrainSampler;

/** What chunk and mantle invocations share: the chunk they run for, its terrain and its settings. X and Z are world
 * coordinates; heights are above the dimension's bottom. Must not be retained after execution.
 */
public interface ChunkContext extends InvocationContext {
    /** The width of a chunk along X and Z, in blocks. */
    int CHUNK_WIDTH = 16;

    /** The local X and Z of a chunk's centre column, where something that happens once per chunk reads its settings. */
    int CENTRE_COLUMN = 7;

    /** The chunk's first block along X, in world coordinates. */
    int originX();

    /** The chunk's first block along Z, in world coordinates. */
    int originZ();

    TerrainSampler terrain();

    FeatureSettings settings();

    /** The chunk's X in chunk coordinates. */
    default int chunkX() {
        return Math.floorDiv(originX(), CHUNK_WIDTH);
    }

    /** The chunk's Z in chunk coordinates. */
    default int chunkZ() {
        return Math.floorDiv(originZ(), CHUNK_WIDTH);
    }

    /** The world X of the chunk's centre column (local 7). */
    default int centreX() {
        return originX() + CENTRE_COLUMN;
    }

    /** The world Z of the chunk's centre column (local 7). */
    default int centreZ() {
        return originZ() + CENTRE_COLUMN;
    }

    /** The chunk's 16 by 16 columns in world coordinates, at the full height the terrain covers. */
    default BlockBox area() {
        var bounds = terrain().bounds();
        return new BlockBox(originX(), bounds.minY(), originZ(), originX() + CHUNK_WIDTH, bounds.maxY(), originZ() + CHUNK_WIDTH);
    }
}
