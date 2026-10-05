package com.volmit.block;

import java.util.random.RandomGenerator;

/** Resolved, immutable palette. Coordinates are world blocks; selection uses only this RNG.
 * An empty/unresolvable palette is a pack-load error. Implementations must support concurrent calls.
 */
@FunctionalInterface
public interface BlockPalette {
    BlockState select(int worldX, int worldY, int worldZ, RandomGenerator random);
}
