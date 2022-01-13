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

public class RoundingStream extends BasicStream<Integer> {
    private final ProceduralStream<?> stream;

    public RoundingStream(ProceduralStream<?> stream) {
        super();
        this.stream = stream;
    }

    @Override
    public double toDouble(Integer t) {
        return t.doubleValue();
    }

    @Override
    public Integer fromDouble(double d) {
        return (int) Math.round(d);
    }

    private int round(double v) {
        return (int) Math.round(v);
    }

    @Override
    public Integer get(double x, double z) {
        return round(stream.getDouble(x, z));
    }

    @Override
    public Integer get(double x, double y, double z) {
        return round(stream.getDouble(x, y, z));
    }
}
