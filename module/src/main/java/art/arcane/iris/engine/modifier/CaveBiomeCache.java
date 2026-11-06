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

package art.arcane.iris.engine.modifier;

import com.volmit.resource.ResourceId;

import java.util.Arrays;

/**
 * The cave biomes one chunk's caves have sampled so far, by block position (see {@link art.arcane.volmlib.util.math.BlockPosition#toLong}):
 * a cave wall asks for the biome around it five times, and the next wall block asks again for most of them. Open addressing on
 * longs, which spares the boxing a map of {@link Long} would do on this hot path; only biomes that exist are kept.
 */
final class CaveBiomeCache {
    private static final double LOAD_FACTOR = 0.5D;
    private static final long EMPTY_KEY = Long.MIN_VALUE;

    private long[] keys;
    private ResourceId[] values;
    private int mask;
    private int size;

    CaveBiomeCache(int expectedSize) {
        int capacity = 8;
        while (capacity * LOAD_FACTOR < expectedSize) {
            capacity <<= 1;
        }
        allocate(capacity);
    }

    /** The biome kept for {@code key}; null where none is. */
    ResourceId get(long key) {
        int index = slot(key);
        while (keys[index] != EMPTY_KEY) {
            if (keys[index] == key) {
                return values[index];
            }
            index = (index + 1) & mask;
        }
        return null;
    }

    void put(long key, ResourceId biome) {
        if ((size + 1) > keys.length * LOAD_FACTOR) {
            grow();
        }
        int index = slot(key);
        while (keys[index] != EMPTY_KEY) {
            if (keys[index] == key) {
                values[index] = biome;
                return;
            }
            index = (index + 1) & mask;
        }
        keys[index] = key;
        values[index] = biome;
        size++;
    }

    private int slot(long key) {
        long mixed = key * 0x9E3779B97F4A7C15L;
        return (int) (mixed ^ (mixed >>> 32)) & mask;
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        Arrays.fill(keys, EMPTY_KEY);
        values = new ResourceId[capacity];
        mask = capacity - 1;
        size = 0;
    }

    private void grow() {
        long[] oldKeys = keys;
        ResourceId[] oldValues = values;
        allocate(oldKeys.length << 1);
        for (int index = 0; index < oldKeys.length; index++) {
            if (oldKeys[index] != EMPTY_KEY) {
                put(oldKeys[index], oldValues[index]);
            }
        }
    }
}
