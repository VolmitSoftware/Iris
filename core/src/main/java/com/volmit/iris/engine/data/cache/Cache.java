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

package com.volmit.iris.engine.data.cache;

import org.bukkit.Chunk;

public interface Cache<V> {
    static long key(Chunk chunk) {
        return key(chunk.getX(), chunk.getZ());
    }

    static long key(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }

    static int keyX(long key) {
        return (int) (key >> 32);
    }

    static int keyZ(long key) {
        return (int) key;
    }

    static int to1D(int x, int y, int z, int w, int h) {
        return (z * w * h) + (y * w) + x;
    }

    static int[] to3D(int idx, int w, int h) {
        final int z = idx / (w * h);
        idx -= (z * w * h);
        final int y = idx / w;
        final int x = idx % w;
        return new int[]{x, y, z};
    }

    int getId();

    V get(int x, int z);
}
