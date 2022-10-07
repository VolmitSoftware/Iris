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

import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.function.NoiseProvider3;
import com.volmit.iris.util.interpolation.InterpolationMethod3D;
import com.volmit.iris.util.interpolation.IrisInterpolation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("interpolator-3d")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Configures interpolatin in 3D")
@Data
public class IrisInterpolator3D {
    @Required
    @Desc("The interpolation method when two biomes use different heights but this same generator")
    private InterpolationMethod3D function = InterpolationMethod3D.TRILINEAR;

    @Required
    @MinNumber(1)
    @MaxNumber(8192)
    @Desc("The range checked in all dimensions. Smaller ranges yeild more detail but are not as smooth.")
    private double scale = 4;

    public double interpolate(double x, double y, double z, NoiseProvider3 provider) {
        return interpolate((int) Math.round(x), (int) Math.round(y), (int) Math.round(z), provider);
    }

    public double interpolate(int x, int y, int z, NoiseProvider3 provider) {
        return IrisInterpolation.getNoise3D(getFunction(), x, y, z, getScale(), provider);
    }
}
