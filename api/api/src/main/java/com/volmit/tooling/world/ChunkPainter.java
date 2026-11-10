package com.volmit.tooling.world;

/**
 * Where the chunks of a tool world come from: the host asks it for every chunk it generates, and for the ones
 * {@link ToolWorld#repaint} names. Nothing else shapes a tool world's terrain.
 *
 * <p>The host calls {@code paint} from its generation threads, several at once, and again for a chunk whenever it
 * repaints it, so a painter holds no per-call state and reads what it paints from state that is safe to read from any
 * thread. It must return quickly and must not call back into the {@link ToolWorld} that is waiting for it. A painter
 * that throws leaves its chunk empty and the failure in the host's log.
 */
@FunctionalInterface
public interface ChunkPainter {
    /** Paints chunk {@code chunkX}, {@code chunkZ} onto {@code canvas}. */
    void paint(int chunkX, int chunkZ, ChunkCanvas canvas);
}
