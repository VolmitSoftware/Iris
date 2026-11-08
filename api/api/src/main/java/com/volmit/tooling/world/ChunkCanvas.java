package com.volmit.tooling.world;

import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;

/**
 * The chunk a {@link ChunkPainter} paints: blocks only, with the host's own block-entity handling behind it. A canvas
 * starts empty (air) and is valid only while {@link ChunkPainter#paint} runs; the host never lets a painter keep it.
 *
 * <p>X and Z are local to the chunk, 0 to 15. Y is the world's own Y, from {@link #minY()} up to but not including
 * {@link #maxY()}, as {@link ToolWorld#minY()} and {@link ToolWorld#maxY()} report it. A coordinate outside these
 * ranges throws {@link IllegalArgumentException}.
 */
public interface ChunkCanvas {
    /** The lowest Y a block can have in this world. */
    int minY();

    /** The first Y above the world; the highest block has Y {@code maxY() - 1}. */
    int maxY();

    /** Puts {@code state} at the position, replacing what the painter put there before. */
    void set(int x, int y, int z, BlockState state);

    /**
     * Puts {@code state} at the position with {@code data} as its block entity, such as the contents of a chest or the
     * lines of a sign. The host applies the entity once the chunk is live; a state that has no block entity on the
     * platform ignores the data. Data in a format the host does not read fails with {@link IllegalArgumentException}.
     */
    void set(int x, int y, int z, BlockState state, BlockEntityData data);
}
