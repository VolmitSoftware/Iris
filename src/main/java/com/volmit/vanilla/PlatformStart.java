package com.volmit.vanilla;

import com.volmit.mantle.BlockBox;
import com.volmit.resource.ResourceId;

/** One of the platform's own starts, as the platform made it, before the host publishes it. Valid only while the
 * provider runs.
 */
public interface PlatformStart {
    ResourceId structure();

    int chunkX();

    int chunkZ();

    /** The box around all of the start's pieces; Y is the height above the dimension's bottom. */
    BlockBox bounds();

    /** Whether the platform generates the structure in one of its underground steps (strongholds, mineshafts, trial
     * chambers, ancient cities).
     */
    boolean underground();

    /** Whether the host aligns this structure itself: an ocean monument to the sea level, a desert or jungle temple to
     * the terrain. A shift then applies on top of that alignment instead of to the platform's height.
     */
    boolean aligned();

    /** The world height of the dimension's bottom, for rules written in world heights, such as a band of world Y. */
    int bottomY();

    /** The height of the terrain's surface at a column, above the dimension's bottom: the highest block of the terrain
     * the host generated there, fluids ignored.
     */
    int surfaceY(int x, int z);
}
