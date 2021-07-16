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

import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.hunk.storage.ArrayHunk;
import com.volmit.iris.util.function.NoiseProvider;
import com.volmit.iris.util.function.NoiseProvider3;

public class NoiseCube {
    private final double[] noise;
    private final int w;
    private final int h;

    public NoiseCube(int offsetX, int offsetY, int offsetZ, int w, int h, int d, NoiseProvider3 provider, InterpolationMethod3D method, int rad)
    {
        final double[] realNoise;
        this.w = w;
        this.h = h;
        int wrad = w/rad;
        int hrad = h/rad;
        int drad = d/rad;
        noise = new double[w*h*d];
        realNoise = new double[(wrad+1)*(hrad+1)*(drad+1)];

        for(int i = 0; i <= wrad; i++)
        {
            for(int j = 0; j <= hrad; j++)
            {
                for(int k = 0; k <= drad; k++)
                {
                    realNoise[(k * w * h) + (j * w) + i] = provider.noise(i, j, k);
                }
            }
        }

        NoiseProvider3 p = (x, y, z) -> realNoise[(int) ((z * w * h) + (y * w) + x)];

        for(int i = 0; i < w; i++)
        {
            for(int j = 0; j < h; j++)
            {
                for(int k = 0; k < d; k++)
                {
                    noise[(k * w * h) + (j * w) + i] = IrisInterpolation.getNoise3D(method, offsetX + i, offsetY + j,offsetZ + k, rad, p);
                }
            }
        }
    }

    public double get(int x, int y, int z)
    {
        return noise[(z * w * h) + (y * w) + x];
    }
}
