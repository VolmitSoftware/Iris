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

public class RoundingDoubleStream extends BasicStream<Double> {
    private final ProceduralStream<?> stream;

    public RoundingDoubleStream(ProceduralStream<?> stream) {
        super();
        this.stream = stream;
    }

    @Override
    public double toDouble(Double t) {
        return t;
    }

    @Override
    public Double fromDouble(double d) {
        return (double) Math.round(d);
    }

    private double round(double v) {
        return Math.round(v);
    }

    @Override
    public Double get(double x, double z) {
        return round(stream.getDouble(x, z));
    }

    @Override
    public Double get(double x, double y, double z) {
        return round(stream.getDouble(x, y, z));
    }

}
