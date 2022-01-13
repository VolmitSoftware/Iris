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

import com.volmit.iris.util.math.RNG;

public class CellularNoise implements NoiseGenerator {
    private final FastNoise n;

    public CellularNoise(long seed) {
        this.n = new FastNoise(new RNG(seed).imax());
        n.SetNoiseType(FastNoise.NoiseType.Cellular);
        n.SetCellularReturnType(FastNoise.CellularReturnType.CellValue);
        n.SetCellularDistanceFunction(FastNoise.CellularDistanceFunction.Natural);
    }

    @Override
    public double noise(double x) {
        return (n.GetCellular((float) x, 0) / 2D) + 0.5D;
    }

    @Override
    public double noise(double x, double z) {
        return (n.GetCellular((float) x, (float) z) / 2D) + 0.5D;
    }

    @Override
    public double noise(double x, double y, double z) {
        return (n.GetCellular((float) x, (float) y, (float) z) / 2D) + 0.5D;
    }
}
