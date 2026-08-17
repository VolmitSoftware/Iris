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

/**
 * Perplex noise blends Perlin and Simplex noise by multiplying their outputs together.
 * Both components are normalized to [0,1] so the product stays in [0,1].
 */
public class PerplexNoise implements NoiseGenerator {
    private final NoiseGenerator perlin;
    private final SimplexNoise simplex;

    public PerplexNoise(long seed) {
        PerlinNoise p = new PerlinNoise(new RNG(seed).lmax());
        p.hermite();
        this.perlin = p;
        this.simplex = new SimplexNoise(new RNG(seed).lmax());
    }

    @Override
    public double noise(double x) {
        return perlin.noise(x) * simplex.noise(x);
    }

    @Override
    public double noise(double x, double z) {
        return perlin.noise(x, z) * simplex.noise(x, z);
    }

    @Override
    public double noise(double x, double y, double z) {
        return perlin.noise(x, y, z) * simplex.noise(x, y, z);
    }
}
