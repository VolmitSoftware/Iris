package com.volmit.block;

import com.volmit.random.WorldRandom;

/** Resolved, immutable palette. X and Z are world coordinates, Y is the height above the
 * dimension's bottom (0 = bottom); selection uses only the given stream.
 * An empty/unresolvable palette is a pack-load error. Implementations must support concurrent calls.
 */
@FunctionalInterface
public interface BlockPalette {
    BlockState select(int worldX, int y, int worldZ, WorldRandom random);
}
