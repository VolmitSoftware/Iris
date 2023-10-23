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
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("shaped-style")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("This represents a generator with a min and max height")
@Data
public class IrisShapedGeneratorStyle {
    @Required
    @Desc("The generator id")
    private IrisGeneratorStyle generator = new IrisGeneratorStyle(NoiseStyle.IRIS);

    @Required
    @MinNumber(-2032) // TODO: WARNING HEIGHT
    @MaxNumber(2032) // TODO: WARNING HEIGHT
    @Desc("The min block value")
    private int min = 0;

    @Required
    @MinNumber(-2032) // TODO: WARNING HEIGHT
    @MaxNumber(2032) // TODO: WARNING HEIGHT
    @Desc("The max block value")
    private int max = 0;

    public IrisShapedGeneratorStyle(NoiseStyle style, int min, int max) {
        this(style);
        this.min = min;
        this.max = max;
    }

    public IrisShapedGeneratorStyle(NoiseStyle style) {
        this.generator = new IrisGeneratorStyle(style);
    }

    public double get(RNG rng, IrisData data, double... dim) {
        return generator.create(rng, data).fitDouble(min, max, dim);
    }

    public boolean isFlat() {
        return min == max || getGenerator().isFlat();
    }

    public int getMid() {
        return (getMax() + getMin()) / 2;
    }
}
