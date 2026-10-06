package com.volmit.block;

import com.volmit.random.WorldRandom;

/** Resolved, immutable palette. Coordinates are world blocks; selection uses only the given stream.
 * An empty/unresolvable palette is a pack-load error. Implementations must support concurrent calls.
 */
@FunctionalInterface
public interface BlockPalette {
    BlockState select(int worldX, int worldY, int worldZ, WorldRandom random);
}
