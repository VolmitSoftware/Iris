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

public class FittedStream<T> extends BasicStream<T> implements ProceduralStream<T> {
    private final double min;
    private final double max;
    private final double inMin;
    private final double inMax;

    public FittedStream(ProceduralStream<T> stream, double inMin, double inMax, double min, double max) {
        super(stream);
        this.inMin = inMin;
        this.inMax = inMax;
        this.min = min;
        this.max = max;
    }

    public FittedStream(ProceduralStream<T> stream, double min, double max) {
        this(stream, 0, 1, min, max);
    }

    @Override
    public double toDouble(T t) {
        return getTypedSource().toDouble(t);
    }

    @Override
    public T fromDouble(double d) {
        return getTypedSource().fromDouble(d);
    }

    private double dlerp(double v) {
        return min + ((max - min) * ((v - inMin) / (inMax - inMin)));
    }

    @Override
    public T get(double x, double z) {
        return fromDouble(dlerp(getTypedSource().getDouble(x, z)));
    }

    @Override
    public T get(double x, double y, double z) {
        return fromDouble(dlerp(getTypedSource().getDouble(x, y, z)));
    }

}
