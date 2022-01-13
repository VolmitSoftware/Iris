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
import com.volmit.iris.util.noise.FastNoiseDouble.FractalType;

public class FractalFBMSimplexNoise implements NoiseGenerator, OctaveNoise {
    private final FastNoiseDouble n;

    public FractalFBMSimplexNoise(long seed) {
        this.n = new FastNoiseDouble(new RNG(seed).lmax());
        n.setFractalOctaves(1);
        n.setFractalType(FractalType.FBM);
    }

    public double f(double v) {
        return (v / 2D) + 0.5D;
    }

    @Override
    public double noise(double x) {
        return f(n.GetSimplexFractal(x, 0d));
    }

    @Override
    public double noise(double x, double z) {
        return f(n.GetSimplexFractal(x, z));
    }

    @Override
    public double noise(double x, double y, double z) {
        return f(n.GetSimplexFractal(x, y, z));
    }

    @Override
    public void setOctaves(int o) {
        n.setFractalOctaves(o);
    }
}
