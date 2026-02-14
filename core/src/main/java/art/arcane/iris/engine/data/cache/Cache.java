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

package art.arcane.iris.engine.data.cache;

import art.arcane.volmlib.util.cache.CacheKey;
import org.bukkit.Chunk;

public interface Cache<V> {
    static long key(Chunk chunk) {
        return CacheKey.key(chunk);
    }

    static long key(int x, int z) {
        return CacheKey.key(x, z);
    }

    static int keyX(long key) {
        return CacheKey.keyX(key);
    }

    static int keyZ(long key) {
        return CacheKey.keyZ(key);
    }

    static int to1D(int x, int y, int z, int w, int h) {
        return CacheKey.to1D(x, y, z, w, h);
    }

    static int[] to3D(int idx, int w, int h) {
        return CacheKey.to3D(idx, w, h);
    }

    int getId();

    V get(int x, int z);
}
