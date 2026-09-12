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

package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class IrisCarveScratch {
    final CarveColumnMask[] columnMasks = new CarveColumnMask[256];
    final CarveColumnMask[] boundaryMasks = new CarveColumnMask[256];
    final int[] surfaceHeights = new int[256];
    final CarveWallBuffer walls = new CarveWallBuffer(512);
    final Map<String, IrisBiome> customBiomeCache = new HashMap<>();
    int[] upperSurfaceHeights;
    boolean customCaveBiomePresent;

    IrisCarveScratch() {
        for (int index = 0; index < columnMasks.length; index++) {
            columnMasks[index] = new CarveColumnMask();
            boundaryMasks[index] = new CarveColumnMask();
        }
    }

    int[] getOrCreateUpperSurfaceHeights() {
        if (upperSurfaceHeights == null) {
            upperSurfaceHeights = new int[256];
        }
        return upperSurfaceHeights;
    }

    void reset() {
        for (int index = 0; index < columnMasks.length; index++) {
            columnMasks[index].clear();
            boundaryMasks[index].clear();
        }
        walls.clear();
        customBiomeCache.clear();
        customCaveBiomePresent = false;
    }
}

final class CarveColumnMask {
    private long[] words = new long[8];
    private int maxWord = -1;

    void add(int y) {
        if (y < 0) {
            return;
        }

        int wordIndex = y >> 6;
        if (wordIndex >= words.length) {
            words = Arrays.copyOf(words, Math.max(words.length << 1, wordIndex + 1));
        }

        words[wordIndex] |= 1L << (y & 63);
        if (wordIndex > maxWord) {
            maxWord = wordIndex;
        }
    }

    int nextSetBit(int fromBit) {
        if (maxWord < 0) {
            return -1;
        }

        int startBit = Math.max(0, fromBit);
        int wordIndex = startBit >> 6;
        if (wordIndex > maxWord) {
            return -1;
        }

        long word = words[wordIndex] & (-1L << (startBit & 63));
        while (true) {
            if (word != 0L) {
                return (wordIndex << 6) + Long.numberOfTrailingZeros(word);
            }

            wordIndex++;
            if (wordIndex > maxWord) {
                return -1;
            }
            word = words[wordIndex];
        }
    }

    boolean isEmpty() {
        return maxWord < 0;
    }

    boolean contains(int y) {
        if (y < 0) {
            return false;
        }

        int wordIndex = y >> 6;
        if (wordIndex > maxWord) {
            return false;
        }

        return (words[wordIndex] & (1L << (y & 63))) != 0L;
    }

    void clear() {
        if (maxWord < 0) {
            return;
        }

        for (int index = 0; index <= maxWord; index++) {
            words[index] = 0L;
        }
        maxWord = -1;
    }
}

final class CarveWallBuffer {
    private static final int EMPTY_KEY = -1;
    private static final double LOAD_FACTOR = 0.75D;

    private int[] keys;
    private MatterCavern[] values;
    private int mask;
    private int resizeAt;
    private int size;

    CarveWallBuffer(int expectedSize) {
        int capacity = 1;
        int minimumCapacity = Math.max(8, expectedSize);
        while (capacity < minimumCapacity) {
            capacity <<= 1;
        }

        keys = new int[capacity];
        Arrays.fill(keys, EMPTY_KEY);
        values = new MatterCavern[capacity];
        mask = capacity - 1;
        resizeAt = Math.max(1, (int) (capacity * LOAD_FACTOR));
    }

    void put(int x, int y, int z, MatterCavern value) {
        int key = pack(x, y, z);
        int index = mix(key) & mask;

        while (true) {
            int existingKey = keys[index];
            if (existingKey == EMPTY_KEY) {
                keys[index] = key;
                values[index] = value;
                size++;
                if (size >= resizeAt) {
                    resize();
                }
                return;
            }

            if (existingKey == key) {
                values[index] = value;
                return;
            }

            index = (index + 1) & mask;
        }
    }

    MatterCavern get(int x, int y, int z) {
        int key = pack(x, y, z);
        int index = mix(key) & mask;
        while (true) {
            int existingKey = keys[index];
            if (existingKey == EMPTY_KEY) {
                return null;
            }
            if (existingKey == key) {
                return values[index];
            }
            index = (index + 1) & mask;
        }
    }

    void forEach(Consumer consumer) {
        for (int index = 0; index < keys.length; index++) {
            int key = keys[index];
            if (key == EMPTY_KEY) {
                continue;
            }

            MatterCavern cavern = values[index];
            if (cavern != null) {
                consumer.accept(unpackX(key), unpackY(key), unpackZ(key), cavern);
            }
        }
    }

    void clear() {
        Arrays.fill(keys, EMPTY_KEY);
        Arrays.fill(values, null);
        size = 0;
    }

    private void resize() {
        int[] oldKeys = keys;
        MatterCavern[] oldValues = values;
        int nextCapacity = oldKeys.length << 1;
        keys = new int[nextCapacity];
        Arrays.fill(keys, EMPTY_KEY);
        values = new MatterCavern[nextCapacity];
        mask = nextCapacity - 1;
        resizeAt = Math.max(1, (int) (nextCapacity * LOAD_FACTOR));
        size = 0;

        for (int index = 0; index < oldKeys.length; index++) {
            int key = oldKeys[index];
            MatterCavern value = oldValues[index];
            if (key != EMPTY_KEY && value != null) {
                reinsert(key, value);
            }
        }
    }

    private void reinsert(int key, MatterCavern value) {
        int index = mix(key) & mask;
        while (keys[index] != EMPTY_KEY) {
            index = (index + 1) & mask;
        }

        keys[index] = key;
        values[index] = value;
        size++;
    }

    private int pack(int x, int y, int z) {
        return (y << 8) | PowerOfTwoCoordinates.packLocal16(x & 15, z & 15);
    }

    private int unpackX(int key) {
        return PowerOfTwoCoordinates.unpackLocal16X(key & 255);
    }

    private int unpackY(int key) {
        return key >> 8;
    }

    private int unpackZ(int key) {
        return PowerOfTwoCoordinates.unpackLocal16Z(key);
    }

    private int mix(int value) {
        int mixed = value * 0x9E3779B9;
        return mixed ^ (mixed >>> 16);
    }

    @FunctionalInterface
    interface Consumer {
        void accept(int x, int y, int z, MatterCavern cavern);
    }
}
