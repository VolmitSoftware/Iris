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

package com.volmit.iris.util.stream;

import com.volmit.iris.util.collection.KList;

public class ArraySignificance<T> implements Significance<T> {
    private final KList<T> types;
    private final KList<Double> significance;
    private final T significant;

    public ArraySignificance(KList<T> types, KList<Double> significance, T significant) {
        this.types = types;
        this.significance = significance;
        this.significant = significant;
    }

    public ArraySignificance(KList<T> types, KList<Double> significance) {
        this.types = types;
        this.significance = significance;
        double s = 0;
        int v = 0;
        for (int i = 0; i < significance.size(); i++) {
            if (significance.get(i) > s) {
                s = significance.get(i);
                v = i;
            }
        }

        significant = types.get(v);
    }

    @Override
    public KList<T> getFactorTypes() {
        return types;
    }

    @Override
    public double getSignificance(T t) {
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).equals(t)) {
                return significance.get(i);
            }
        }

        return 0;
    }

    @Override
    public T getMostSignificantType() {
        return significant;
    }
}
