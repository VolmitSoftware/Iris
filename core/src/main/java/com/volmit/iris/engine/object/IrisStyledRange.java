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

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.stream.ProceduralStream;
import com.volmit.iris.util.stream.interpolation.Interpolated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("style-range")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a range styled with a custom generator")
@Data
public class IrisStyledRange {
    @Desc("The minimum value")
    private double min = 16;

    @Desc("The maximum value")
    private double max = 32;

    @Desc("The style to pick the range")
    private IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.STATIC);

    public double get(RNG rng, double x, double z, IrisData data) {
        if (min == max) {
            return min;
        }

        if (style.isFlat()) {
            return M.lerp(min, max, 0.5);
        }

        return style.create(rng, data).fitDouble(min, max, x, z);
    }

    public ProceduralStream<Double> stream(RNG rng, IrisData data) {
        return ProceduralStream.of((x, z) -> get(rng, x, z, data), Interpolated.DOUBLE);
    }

    public boolean isFlat() {
        return getMax() == getMin() || style.isFlat();
    }

    public int getMid() {
        return (int) ((getMax() + getMin()) / 2);
    }
}
