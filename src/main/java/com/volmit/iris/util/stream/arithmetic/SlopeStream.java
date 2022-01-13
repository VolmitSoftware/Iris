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

package com.volmit.iris.util.stream.arithmetic;

import com.volmit.iris.util.stream.BasicStream;
import com.volmit.iris.util.stream.ProceduralStream;

public class SlopeStream<T> extends BasicStream<T> {
    private final int range;

    public SlopeStream(ProceduralStream<T> stream, int range) {
        super(stream);
        this.range = range;
    }

    @Override
    public double toDouble(T t) {
        return getTypedSource().toDouble(t);
    }

    @Override
    public T fromDouble(double d) {
        return getTypedSource().fromDouble(d);
    }

    @Override
    public T get(double x, double z) {
        double height = getTypedSource().getDouble(x, z);
        double dx = getTypedSource().getDouble(x + range, z) - height;
        double dy = getTypedSource().getDouble(x, z + range) - height;

        return fromDouble(Math.sqrt(dx * dx + dy * dy));
    }

    @Override
    public T get(double x, double y, double z) {
        double height = getTypedSource().getDouble(x, y, z);
        double dx = getTypedSource().getDouble(x + range, y, z) - height;
        double dy = getTypedSource().getDouble(x, y + range, z) - height;
        double dz = getTypedSource().getDouble(x, y, z + range) - height;

        return fromDouble(Math.cbrt((dx * dx) + (dy * dy) + (dz * dz)));
    }

}
