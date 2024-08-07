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

package com.volmit.iris.engine.object.matter;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisStyledRange;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode()
@Accessors(chain = true)
@Desc("Represents a matter translator")
public class IrisMatterTranslate {
    @Desc("For varied coordinate shifts use ranges not the literal coordinate")
    private IrisStyledRange rangeX = null;
    @Desc("For varied coordinate shifts use ranges not the literal coordinate")
    private IrisStyledRange rangeY = null;
    @Desc("For varied coordinate shifts use ranges not the literal coordinate")
    private IrisStyledRange rangeZ = null;
    @Desc("Define an absolute shift instead of varied.")
    private int x = 0;
    @Desc("Define an absolute shift instead of varied.")
    private int y = 0;
    @Desc("Define an absolute shift instead of varied.")
    private int z = 0;

    public int xOffset(IrisData data, RNG rng, int rx, int rz) {
        if (rangeX != null) {
            return (int) Math.round(rangeX.get(rng, rx, rz, data));
        }

        return x;
    }

    public int yOffset(IrisData data, RNG rng, int rx, int rz) {
        if (rangeY != null) {
            return (int) Math.round(rangeY.get(rng, rx, rz, data));
        }

        return y;
    }

    public int zOffset(IrisData data, RNG rng, int rx, int rz) {
        if (rangeZ != null) {
            return (int) Math.round(rangeZ.get(rng, rx, rz, data));
        }

        return z;
    }
}
