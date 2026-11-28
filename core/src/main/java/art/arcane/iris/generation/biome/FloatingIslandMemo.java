/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.biome;

import java.util.HashMap;

/**
 * One worker's floating island samples for the chunk it is generating. A runtime scope keeps one per worker thread,
 * so the memo never outlives the engine that produced its samples.
 */
public final class FloatingIslandMemo {
    private static final int CHUNK_COLUMN_COUNT = 256;

    private final HashMap<Long, MemoizedSample> samples = new HashMap<>(CHUNK_COLUMN_COUNT);
    private MemoContext context;

    public void clear() {
        samples.clear();
        context = null;
    }

    /** The memoized sample for this column and parent, which may itself be null; null when not sampled yet. */
    MemoizedSample find(IrisBiome parent, long column) {
        MemoizedSample cached = samples.get(column);
        return cached != null && cached.parent == parent ? cached : null;
    }

    void remember(IrisBiome parent, long column, FloatingIslandSample sample) {
        samples.put(column, new MemoizedSample(parent, sample));
    }

    void enterChunk(int chunkX, int chunkZ, int height, long seed, FloatingIslandBoundarySampler boundarySampler) {
        if (context != null && context.matches(chunkX, chunkZ, height, seed, boundarySampler)) {
            return;
        }
        samples.clear();
        context = new MemoContext(chunkX, chunkZ, height, seed, boundarySampler);
    }

    private record MemoContext(int chunkX, int chunkZ, int height, long seed,
                               FloatingIslandBoundarySampler boundarySampler) {
        private boolean matches(int chunkX, int chunkZ, int height, long seed,
                                FloatingIslandBoundarySampler boundarySampler) {
            return this.chunkX == chunkX && this.chunkZ == chunkZ && this.height == height && this.seed == seed
                    && this.boundarySampler == boundarySampler;
        }
    }

    record MemoizedSample(IrisBiome parent, FloatingIslandSample sample) {
    }
}
