package com.volmit.geometry;

import java.util.Objects;

/** A chunk column, by chunk coordinates: block X and Z divided by 16, rounded down. */
public record ChunkPos(int x, int z) {
    private static final int CHUNK_SHIFT = 4;

    /** The chunk that contains {@code block}. */
    public static ChunkPos containing(BlockPosition block) {
        Objects.requireNonNull(block, "block");
        return new ChunkPos(block.x() >> CHUNK_SHIFT, block.z() >> CHUNK_SHIFT);
    }
}
