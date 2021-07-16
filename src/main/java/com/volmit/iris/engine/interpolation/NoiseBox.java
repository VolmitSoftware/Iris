/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.interpolation;

import com.volmit.iris.util.function.NoiseProvider;

public class NoiseBox {
    private final double[] noise;
    private final int w;

    public NoiseBox(int offsetX, int offsetZ, int w, int d, NoiseProvider provider, InterpolationMethod method, int rad)
    {
        final double[] realNoise;
        this.w = w;
        int wrad = w/rad;
        int drad = d/rad;
        noise = new double[w*d];
        realNoise = new double[(wrad+1)*(drad+1)];

        for(int i = 0; i <= wrad; i++)
        {
            for(int j = 0; j <= drad; j++)
            {
                realNoise[(j * wrad) + i] = provider.noise(i, j);
            }
        }

        NoiseProvider p = (x, z) -> realNoise[(int) ((z * wrad) + x)];

        for(int i = 0; i < w; i++)
        {
            for(int j = 0; j < d; j++)
            {
                noise[(j * w) + i] = IrisInterpolation.getNoise(method, offsetX + i, offsetZ + j, rad, p);
            }
        }
    }

    public double get(int x, int z)
    {
        return noise[(z * w) + x];
    }
}
