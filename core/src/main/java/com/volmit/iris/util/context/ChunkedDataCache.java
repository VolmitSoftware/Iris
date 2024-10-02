/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.context;

import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.stream.ProceduralStream;
import lombok.Data;

@Data
public class ChunkedDataCache<T> {
    private final int x;
    private final int z;
    private final KSet<T> uniques;
    private final Object[] data;
    private final boolean cache;
    private final ProceduralStream<T> stream;

    @BlockCoordinates
    public ChunkedDataCache(BurstExecutor burst, ProceduralStream<T> stream, int x, int z) {
        this(burst, stream, x, z, true);
    }

    @BlockCoordinates
    public ChunkedDataCache(BurstExecutor burst, ProceduralStream<T> stream, int x, int z, boolean cache) {
        this.stream = stream;
        this.cache = cache;
        this.x = x;
        this.z = z;
        this.uniques = cache ? new KSet<>() : null;
        if (cache) {
            data = new Object[256];
            int i, j;

            for (i = 0; i < 16; i++) {
                int finalI = i;
                for (j = 0; j < 16; j++) {
                    int finalJ = j;
                    burst.queue(() -> {
                        T t = stream.get(x + finalI, z + finalJ);
                        data[(finalJ * 16) + finalI] = t;
                        uniques.add(t);
                    });
                }
            }
        } else {
            data = new Object[0];
        }
    }

    @SuppressWarnings("unchecked")
    @BlockCoordinates
    public T get(int x, int z) {
        if (!cache) {
            return stream.get(this.x + x, this.z + z);
        }

        T t = (T) data[(z * 16) + x];
        return t == null ? stream.get(this.x + x, this.z + z) : t;
    }
}
