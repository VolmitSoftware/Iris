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

package com.volmit.iris.util.noise;

import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;

public class VascularNoise implements NoiseGenerator {
    private final FastNoiseDouble n;

    public VascularNoise(long seed) {
        this.n = new FastNoiseDouble(new RNG(seed).lmax());
        n.setNoiseType(FastNoiseDouble.NoiseType.Cellular);
        n.setCellularReturnType(FastNoiseDouble.CellularReturnType.Distance2Sub);
        n.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
    }

    private double filter(double noise) {
        return M.clip((noise / 2D) + 0.5D, 0D, 1D);
    }

    @Override
    public double noise(double x) {
        return filter(n.GetCellular(x, 0));
    }

    @Override
    public double noise(double x, double z) {
        return filter(n.GetCellular(x, z));
    }

    @Override
    public double noise(double x, double y, double z) {
        return filter(n.GetCellular(x, y, z));
    }
}
