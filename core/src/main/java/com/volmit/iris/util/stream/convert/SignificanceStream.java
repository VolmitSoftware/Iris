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

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.stream.ArraySignificance;
import com.volmit.iris.util.stream.BasicStream;
import com.volmit.iris.util.stream.ProceduralStream;
import com.volmit.iris.util.stream.Significance;

public class SignificanceStream<K extends Significance<T>, T> extends BasicStream<K> {
    private final ProceduralStream<T> stream;
    private final double radius;
    private final int checks;

    public SignificanceStream(ProceduralStream<T> stream, double radius, int checks) {
        super();
        this.stream = stream;
        this.radius = radius;
        this.checks = checks;
    }

    @Override
    public double toDouble(K t) {
        return 0;
    }

    @Override
    public K fromDouble(double d) {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public K get(double x, double z) {
        KList<T> ke = new KList<>(8);
        KList<Double> va = new KList<>(8);

        double m = (360d / checks);
        double v = 0;

        for (int i = 0; i < 360; i += m) {
            double sin = Math.sin(Math.toRadians(i));
            double cos = Math.cos(Math.toRadians(i));
            double cx = x + ((radius * cos) - (radius * sin));
            double cz = z + ((radius * sin) + (radius * cos));
            T t = stream.get(cx, cz);

            if (ke.addIfMissing(t)) {
                va.add(1D);
                v++;
            } else {
                int ind = ke.indexOf(t);
                va.set(ind, va.get(ind) + 1D);
            }
        }

        for (int i = 0; i < va.size(); i++) {
            va.set(i, va.get(i) / v);
        }

        return (K) new ArraySignificance<>(ke, va);
    }

    @Override
    public K get(double x, double y, double z) {
        return get(x, z);
    }
}
