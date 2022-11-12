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

package com.volmit.iris.util.data;

import com.volmit.iris.util.function.Function2;

import java.util.concurrent.atomic.AtomicReferenceArray;

public class ChunkCache<T> {
    private final AtomicReferenceArray<T> cache;

    public ChunkCache() {
        cache = new AtomicReferenceArray<>(256);
    }

    public T compute(int x, int z, Function2<Integer, Integer, T> function) {
        T t = get(x & 15, z & 15);

        if (t == null) {
            t = function.apply(x, z);
            set(x & 15, z & 15, t);
        }

        return t;
    }

    private void set(int x, int z, T t) {
        cache.set(x * 16 + z, t);
    }

    private T get(int x, int z) {
        return cache.get(x * 16 + z);
    }
}
