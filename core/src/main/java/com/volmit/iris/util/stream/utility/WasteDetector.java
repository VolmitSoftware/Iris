/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.stream.utility;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.stream.BasicStream;
import com.volmit.iris.util.stream.ProceduralStream;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WasteDetector<T> extends BasicStream<T> {
    public static final boolean checking = false;
    private static final KMap<String, Integer> allAccesses = new KMap<>();
    private static final KMap<String, List<Throwable>> allThrows = new KMap<>();
    private final AtomicInteger accesses;
    private final String name;

    public WasteDetector(ProceduralStream<T> stream, String name) {
        super(stream);
        this.name = name;
        accesses = new AtomicInteger(0);
    }

    public static void printAll() {
        if (checking) {
            Iris.warn("=========================================================");
            for (String i : allAccesses.sortKNumber().reverse()) {
                Iris.warn(i + ": " + allAccesses.get(i) + " Time(s)");
            }
            Iris.warn("=========================================================");
            for (String i : allAccesses.sortKNumber().reverse()) {
                Iris.warn("======== " + i + " ========");
                for (Throwable j : allThrows.get(i)) {
                    j.printStackTrace();
                }
                Iris.warn("---------------------------------------------------------");
            }
            Iris.warn("=========================================================");
        }
    }

    @Override
    public T get(double x, double z) {
        if (checking) {
            if (x == 7 && z == 7) {
                // AHHHAAA!
                allAccesses.compute(name, (k, v) -> v == null ? 1 : v + 1);
                try {
                    throw new RuntimeException();
                } catch (RuntimeException e) {
                    allThrows.computeIfAbsent(name, (k) -> new KList<>()).add(e);
                }
            }

        }
        return getTypedSource().get(x, z);
    }

    @Override
    public T get(double x, double y, double z) {
        return getTypedSource().get(x, y, z);
    }

    @Override
    public double toDouble(T t) {
        return getTypedSource().toDouble(t);
    }

    @Override
    public T fromDouble(double d) {
        return getTypedSource().fromDouble(d);
    }
}
