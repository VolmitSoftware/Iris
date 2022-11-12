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

package com.volmit.iris.util.stream.interpolation;

import com.volmit.iris.util.interpolation.IrisInterpolation;
import com.volmit.iris.util.stream.BasicStream;
import com.volmit.iris.util.stream.ProceduralStream;

public class BicubicStream<T> extends BasicStream<T> implements Interpolator<T> {
    private final int rx;
    private final int ry;

    public BicubicStream(ProceduralStream<T> stream, int rx, int ry) {
        super(stream);
        this.rx = rx;
        this.ry = ry;
    }

    public T interpolate(double x, double y) {
        int fx = (int) Math.floor(x / rx);
        int fz = (int) Math.floor(y / ry);
        int x0 = Math.round((fx - 1) * rx);
        int z0 = Math.round((fz - 1) * ry);
        int x1 = Math.round(fx * rx);
        int z1 = Math.round(fz * ry);
        int x2 = Math.round((fx + 1) * rx);
        int z2 = Math.round((fz + 1) * ry);
        int x3 = Math.round((fx + 2) * rx);
        int z3 = Math.round((fz + 2) * ry);
        double px = IrisInterpolation.rangeScale(0, 1, x1, x2, x);
        double pz = IrisInterpolation.rangeScale(0, 1, z1, z2, y);

        //@builder
        return getTypedSource().fromDouble(IrisInterpolation.bicubic(
                getTypedSource().getDouble(x0, z0),
                getTypedSource().getDouble(x0, z1),
                getTypedSource().getDouble(x0, z2),
                getTypedSource().getDouble(x0, z3),
                getTypedSource().getDouble(x1, z0),
                getTypedSource().getDouble(x1, z1),
                getTypedSource().getDouble(x1, z2),
                getTypedSource().getDouble(x1, z3),
                getTypedSource().getDouble(x2, z0),
                getTypedSource().getDouble(x2, z1),
                getTypedSource().getDouble(x2, z2),
                getTypedSource().getDouble(x2, z3),
                getTypedSource().getDouble(x3, z0),
                getTypedSource().getDouble(x3, z1),
                getTypedSource().getDouble(x3, z2),
                getTypedSource().getDouble(x3, z3),
                px, pz));
        //@done
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
        return interpolate(x, z);
    }

    @Override
    public T get(double x, double y, double z) {
        return interpolate(x, z);
    }
}
