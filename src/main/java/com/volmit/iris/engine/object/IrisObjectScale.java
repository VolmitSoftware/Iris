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

package com.volmit.iris.engine.object;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("object-scale")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Scale objects")
@Data
public class IrisObjectScale {
    private static ConcurrentLinkedHashMap<IrisObject, KList<IrisObject>> cache
            = new ConcurrentLinkedHashMap.Builder<IrisObject, KList<IrisObject>>()
            .initialCapacity(64)
            .maximumWeightedCapacity(1024)
            .concurrencyLevel(32)
            .build();
    @MinNumber(1)
    @MaxNumber(32)
    @Desc("Iris Objects are scaled and cached to speed up placements. Because of this extra memory is used, so we evenly distribute variations across the defined scale range, then pick one randomly. If the differences is small, use a lower number. For more possibilities on the scale spectrum, increase this at the cost of memory.")
    private int variations = 7;
    @MinNumber(0.01)
    @MaxNumber(50)
    @Desc("The minimum scale")
    private double minimumScale = 1;
    @MinNumber(0.01)
    @MaxNumber(50)
    @Desc("The maximum height for placement (top of object)")
    private double maximumScale = 1;
    @Desc("If this object is scaled up beyond its origin size, specify a 3D interpolator")
    private IrisObjectPlacementScaleInterpolator interpolation = IrisObjectPlacementScaleInterpolator.NONE;

    public boolean shouldScale() {
        return ((minimumScale == maximumScale) && maximumScale == 1) || variations <= 0;
    }

    public int getMaxSizeFor(int indim) {
        return (int) (getMaxScale() * indim);
    }

    public double getMaxScale() {
        double mx = 0;

        for (double i = minimumScale; i < maximumScale; i += (maximumScale - minimumScale) / (double) (Math.min(variations, 32))) {
            mx = i;
        }

        return mx;
    }

    public IrisObject get(RNG rng, IrisObject origin) {
        if (shouldScale()) {
            return origin;
        }

        return cache.computeIfAbsent(origin, (k) -> {
            KList<IrisObject> c = new KList<>();
            for (double i = minimumScale; i < maximumScale; i += (maximumScale - minimumScale) / (double) (Math.min(variations, 32))) {
                c.add(origin.scaled(i, getInterpolation()));
            }

            return c;
        }).getRandom(rng);
    }

    public boolean canScaleBeyond() {
        return shouldScale() && maximumScale > 1;
    }
}
