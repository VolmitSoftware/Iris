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

package com.volmit.iris.util.stream.utility;

import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.function.Function3;
import com.volmit.iris.util.stream.BasicStream;
import com.volmit.iris.util.stream.ProceduralStream;

public class ContextInjectingStream<T> extends BasicStream<T> {
    private final Function3<ChunkContext, Integer, Integer, T> contextAccessor;

    public ContextInjectingStream(ProceduralStream<T> stream, Function3<ChunkContext, Integer, Integer, T> contextAccessor) {
        super(stream);
        this.contextAccessor = contextAccessor;
    }

    @Override
    public T get(double x, double z) {
        IrisContext context = IrisContext.get();

        if (context != null) {
            ChunkContext chunkContext = context.getChunkContext();

            if (chunkContext != null && (int) x >> 4 == chunkContext.getX() >> 4 && (int) z >> 4 == chunkContext.getZ() >> 4) {
                T t = contextAccessor.apply(chunkContext, (int) x & 15, (int) z & 15);

                if (t != null) {
                    return t;
                }
            }
        }

        return getTypedSource().get(x, z);
    }

    @Override
    public T get(double x, double y, double z) {
        return getTypedSource().get(x, y, z);
    }

    @Override
    public double toDouble(T t) {
        return getTypedSource().toDouble(t);
    }

    @Override
    public T fromDouble(double d) {
        return getTypedSource().fromDouble(d);
    }
}
