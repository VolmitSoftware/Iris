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

import com.volmit.iris.util.function.Function3;
import com.volmit.iris.util.stream.BasicStream;
import com.volmit.iris.util.stream.ProceduralStream;

public class AwareConversionStream2D<T, V> extends BasicStream<V> {
    private final ProceduralStream<T> stream;
    private final Function3<T, Double, Double, V> converter;

    public AwareConversionStream2D(ProceduralStream<T> stream, Function3<T, Double, Double, V> converter) {
        super(null);
        this.stream = stream;
        this.converter = converter;
    }

    @Override
    public double toDouble(V t) {
        if (t instanceof Double) {
            return (Double) t;
        }

        return 0;
    }

    @Override
    public V fromDouble(double d) {
        return null;
    }

    @Override
    public ProceduralStream<?> getSource() {
        return stream;
    }

    @Override
    public V get(double x, double z) {
        return converter.apply(stream.get(x, z), x, z);
    }

    @Override
    public V get(double x, double y, double z) {
        return converter.apply(stream.get(x, y, z), x, z);
    }
}
