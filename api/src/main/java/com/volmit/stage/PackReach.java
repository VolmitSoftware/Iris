package com.volmit.stage;

import com.volmit.config.PackSettings;

/** How far a mantle stage writes beyond its chunk, worked out when the pack loads, for work whose size
 * depends on the pack, such as the largest object or the longest vein any level configures. The host
 * calls it once per pack load, before generating, and prepares that much around every chunk.
 */
@FunctionalInterface
public interface PackReach {
    /** Blocks along X and Z beyond the chunk's 16 by 16 columns; never negative. */
    int blocks(PackSettings settings);
}
