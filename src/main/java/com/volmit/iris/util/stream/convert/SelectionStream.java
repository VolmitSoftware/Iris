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

package com.volmit.iris.util.stream.convert;

import com.volmit.iris.util.stream.BasicStream;
import com.volmit.iris.util.stream.ProceduralStream;

import java.util.List;

public class SelectionStream<T> extends BasicStream<T> {
    private final ProceduralStream<Integer> stream;
    private final T[] options;

    public SelectionStream(ProceduralStream<?> stream, T[] options) {
        super();
        this.stream = stream.fit(0, options.length - 1).round();
        this.options = options;
    }

    @SuppressWarnings("unchecked")
    public SelectionStream(ProceduralStream<?> stream, List<T> options) {
        this(stream, (T[]) options.toArray());
    }

    @Override
    public double toDouble(T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T fromDouble(double d) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T get(double x, double z) {
        if (options.length == 0) {
            return null;
        }

        return options[stream.get(x, z)];
    }

    @Override
    public T get(double x, double y, double z) {
        if (options.length == 0) {
            return null;
        }

        return options[stream.get(x, y, z)];
    }

}
