package com.volmit.generation;

import com.volmit.hunk.Hunk;
import com.volmit.mantle.Mantle;
import com.volmit.world.TerrainSampler;

/** Per-invocation chunk generation inputs. Must not be retained after execution. */
public interface GenerationContext extends ChunkContext {
    /** World coordinates of the local volume's first column. The volume starts at the dimension's bottom:
     * local y is the height above it (0 = bottom block).
     */
    @Override
    int originX();

    @Override
    int originZ();

    /** The terrain of the chunk and of the 16 blocks around it on every side, at full height: a chunk stage writes only
     * its own chunk, but may look at its neighbors' terrain, such as their heights.
     */
    @Override
    TerrainSampler terrain();

    /** Non-null mantle access scoped to this invocation; the host owns its lifetime. Chunk stages read it
     * only: the mantle pipeline has finished around the chunk, and writes throw. Like {@link #terrain()} it covers the
     * chunk and the 16 blocks around it, so a stage that places something against a neighbor can see what the mantle
     * holds there.
     */
    Mantle mantle();

    /** The host-owned local volume of the given value type, shared by all stages of this
     * invocation. Unsupported types throw IllegalArgumentException.
     */
    <T> Hunk<T> hunk(Class<T> type);
}
