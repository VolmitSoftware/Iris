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

import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import com.volmit.mantle.CarvedVoid;

import java.util.Arrays;

final class CarveWallBuffer {
    private static final int EMPTY_KEY = -1;
    private static final double LOAD_FACTOR = 0.75D;

    private int[] keys;
    private CarvedVoid[] values;
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
        values = new CarvedVoid[capacity];
        mask = capacity - 1;
        resizeAt = Math.max(1, (int) (capacity * LOAD_FACTOR));
    }

    void put(int x, int y, int z, CarvedVoid value) {
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

    CarvedVoid get(int x, int y, int z) {
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

            CarvedVoid carvedVoid = values[index];
            if (carvedVoid != null) {
                consumer.accept(unpackX(key), unpackY(key), unpackZ(key), carvedVoid);
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
        CarvedVoid[] oldValues = values;
        int nextCapacity = oldKeys.length << 1;
        keys = new int[nextCapacity];
        Arrays.fill(keys, EMPTY_KEY);
        values = new CarvedVoid[nextCapacity];
        mask = nextCapacity - 1;
        resizeAt = Math.max(1, (int) (nextCapacity * LOAD_FACTOR));
        size = 0;

        for (int index = 0; index < oldKeys.length; index++) {
            int key = oldKeys[index];
            CarvedVoid value = oldValues[index];
            if (key != EMPTY_KEY && value != null) {
                reinsert(key, value);
            }
        }
    }

    private void reinsert(int key, CarvedVoid value) {
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
        void accept(int x, int y, int z, CarvedVoid carvedVoid);
    }
}
