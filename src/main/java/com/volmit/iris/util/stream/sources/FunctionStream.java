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

package com.volmit.iris.util.stream.sources;

import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.function.Function3;
import com.volmit.iris.util.stream.BasicStream;
import com.volmit.iris.util.stream.interpolation.Interpolated;

public class FunctionStream<T> extends BasicStream<T> {
    private final Function2<Double, Double, T> f2;
    private final Function3<Double, Double, Double, T> f3;
    private final Interpolated<T> helper;

    public FunctionStream(Function2<Double, Double, T> f2, Function3<Double, Double, Double, T> f3, Interpolated<T> helper) {
        super();
        this.f2 = f2;
        this.f3 = f3;
        this.helper = helper;
    }

    @Override
    public double toDouble(T t) {
        return helper.toDouble(t);
    }

    @Override
    public T fromDouble(double d) {
        return helper.fromDouble(d);
    }

    @Override
    public T get(double x, double z) {
        return f2.apply(x, z);
    }

    @Override
    public T get(double x, double y, double z) {
        return f3.apply(x, y, z);
    }
}
