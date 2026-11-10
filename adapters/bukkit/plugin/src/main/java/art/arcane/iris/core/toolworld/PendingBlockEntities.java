/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.toolworld;

import art.arcane.iris.core.runtime.StudioPainterSlots.PaintedBlockEntity;
import art.arcane.iris.core.runtime.StudioPainterSlots.PaintedChunk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The block entities a painter asked for in chunks that are generated but not loaded yet. A chunk is painted on a
 * generation thread and its block entities can only be written once it is live, so they wait here for its load.
 */
final class PendingBlockEntities {
    private static final int INT_BITS = 32;

    private final Map<Long, List<PaintedBlockEntity>> byChunk = new ConcurrentHashMap<>();

    /** Keeps what a paint of a chunk asked for; a later paint of the same chunk replaces it. */
    void add(PaintedChunk painted) {
        byChunk.put(chunkKey(painted.chunkX(), painted.chunkZ()), painted.blockEntities());
    }

    /** Hands over what waits for the chunk, once; empty when nothing does. */
    List<PaintedBlockEntity> take(int chunkX, int chunkZ) {
        List<PaintedBlockEntity> waiting = byChunk.remove(chunkKey(chunkX, chunkZ));
        return waiting == null ? List.of() : waiting;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << INT_BITS) | (chunkZ & 0xFFFFFFFFL);
    }
}
