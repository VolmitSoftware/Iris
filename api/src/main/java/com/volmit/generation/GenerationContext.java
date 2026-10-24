package com.volmit.generation;

import com.volmit.block.BlockState;
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

    /** Non-null mantle access scoped to this invocation; the host owns its lifetime. Like {@link #terrain()} it covers
     * the chunk and the 16 blocks around it, so a stage that places something against a neighbor can see what the mantle
     * holds there. A chunk stage writes module layers inside its own chunk's columns only, such as markers it derives
     * from the chunk's finished blocks: the mantle pipeline has finished around the chunk, and a neighbor may already
     * be generated, so writes outside the chunk throw UnsupportedOperationException, as do writes to
     * {@link Mantle#blocks()} and {@link Mantle#edit()}.
     */
    Mantle mantle();

    /** The chunk's blocks, which the stages of this invocation build one after the other: a volume of 16 by the
     * dimension's height by 16, local to the chunk, owned by the host and shared by every stage of the invocation.
     * Empty space is air.
     */
    Hunk<BlockState> blocks();
}
