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

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.CaveResult;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.stream.ProceduralStream;
import org.bukkit.block.data.BlockData;

import java.util.UUID;
import java.util.function.Function;

public interface Interpolated<T> {
    Interpolated<BlockData> BLOCK_DATA = of((t) -> 0D, (t) -> null);
    Interpolated<KList<CaveResult>> CAVE_RESULTS = of((t) -> 0D, (t) -> null);
    Interpolated<RNG> RNG = of((t) -> 0D, (t) -> null);
    Interpolated<Double> DOUBLE = of((t) -> t, (t) -> t);
    Interpolated<Double[]> DOUBLE_ARRAY = of((t) -> 0D, (t) -> new Double[2]);
    Interpolated<Boolean> BOOLEAN = of((t) -> 0D, (t) -> false);
    Interpolated<Integer> INT = of(Double::valueOf, Double::intValue);
    Interpolated<Long> LONG = of(Double::valueOf, Double::longValue);
    Interpolated<UUID> UUID = of((i) -> Double.longBitsToDouble(i.getMostSignificantBits()), (i) -> new UUID(Double.doubleToLongBits(i), i.longValue()));

    static <T> Interpolated<T> of(Function<T, Double> a, Function<Double, T> b) {
        return new Interpolated<>() {
            @Override
            public double toDouble(T t) {
                return a.apply(t);
            }

            @Override
            public T fromDouble(double d) {
                return b.apply(d);
            }
        };
    }

    double toDouble(T t);

    T fromDouble(double d);

    default InterpolatorFactory<T> interpolate() {
        if (this instanceof ProceduralStream) {
            return new InterpolatorFactory<>((ProceduralStream<T>) this);
        }

        Iris.warn("Cannot interpolate " + this.getClass().getCanonicalName() + "!");
        return null;
    }
}
