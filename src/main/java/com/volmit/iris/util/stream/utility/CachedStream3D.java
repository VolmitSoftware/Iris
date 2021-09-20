/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.stream.utility;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.stream.BasicStream;
import com.volmit.iris.util.stream.ProceduralStream;

public class CachedStream3D<T> extends BasicStream<T> implements ProceduralStream<T> {
    private final ProceduralStream<T> stream;
    private final ConcurrentLinkedHashMap<BlockPosition, T> cache;

    public CachedStream3D(ProceduralStream<T> stream, int size) {
        super();
        this.stream = stream;
        cache = new ConcurrentLinkedHashMap.Builder<BlockPosition, T>()
                .initialCapacity(size)
                .maximumWeightedCapacity(size)
                .concurrencyLevel(32)
                .build();
    }

    @Override
    public double toDouble(T t) {
        return stream.toDouble(t);
    }

    @Override
    public T fromDouble(double d) {
        return stream.fromDouble(d);
    }

    @Override
    public T get(double x, double z) {
        return cache.computeIfAbsent(new BlockPosition((int) x, -1, (int) z), (k) -> stream.get((int) x, (int) z));
    }

    @Override
    public T get(double x, double y, double z) {
        return cache.computeIfAbsent(new BlockPosition((int) x, (int) y, (int) z), (k) -> stream.get((int) x, (int) y, (int) z));
    }
}
