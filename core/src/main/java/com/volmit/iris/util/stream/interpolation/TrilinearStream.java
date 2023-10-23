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

public class TrilinearStream<T> extends BasicStream<T> implements Interpolator<T> {
    private final int rx;
    private final int ry;
    private final int rz;

    public TrilinearStream(ProceduralStream<T> stream, int rx, int ry, int rz) {
        super(stream);
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
    }

    public T interpolate(double x, double y, double z) {
        int fx = (int) Math.floor(x / rx);
        int fy = (int) Math.floor(y / ry);
        int fz = (int) Math.floor(z / rz);
        int x1 = Math.round(fx * rx);
        int y1 = Math.round(fy * ry);
        int z1 = Math.round(fz * rz);
        int x2 = Math.round((fx + 1) * rx);
        int y2 = Math.round((fy + 1) * ry);
        int z2 = Math.round((fz + 1) * rz);
        double px = IrisInterpolation.rangeScale(0, 1, x1, x2, x);
        double py = IrisInterpolation.rangeScale(0, 1, y1, y2, y);
        double pz = IrisInterpolation.rangeScale(0, 1, z1, z2, z);

        //@builder
        return getTypedSource().fromDouble(IrisInterpolation.trilerp(
                getTypedSource().getDouble(x1, y1, z1),
                getTypedSource().getDouble(x2, y1, z1),
                getTypedSource().getDouble(x1, y1, z2),
                getTypedSource().getDouble(x2, y1, z2),
                getTypedSource().getDouble(x1, y2, z1),
                getTypedSource().getDouble(x2, y2, z1),
                getTypedSource().getDouble(x1, y2, z2),
                getTypedSource().getDouble(x2, y2, z2),
                px, pz, py));
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
        return interpolate(x, 0, z);
    }

    @Override
    public T get(double x, double y, double z) {
        return interpolate(x, y, z);
    }
}
