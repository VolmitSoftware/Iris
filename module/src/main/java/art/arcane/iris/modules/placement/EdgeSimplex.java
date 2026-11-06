/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modules.placement;

import art.arcane.volmlib.util.math.RNG;

/**
 * Single-octave simplex noise over a plane that swings between -1 and 1, the rolling wave of a terrain bend's edge. It
 * is a copy of the two-dimensional simplex of Iris's {@code SimplexNoise} and draws exactly what that draws for the same
 * seed, so worlds bent before keep their edges; Iris pins the two against each other in a test.
 */
public final class EdgeSimplex {
    private static final double FREQUENCY = 0.01;
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final long X_PRIME = 1619;
    private static final long Y_PRIME = 31337;
    private static final long HASH_MULTIPLIER = 60493;
    private static final double SCALE = 50;
    private static final double[] GRADIENTS = {-1D, -1D, 1D, -1D, -1D, 1D, 1D, 1D, 0D, -1D, -1D, 0D, 0D, 1D, 1D, 0D};

    private final long seed;

    public EdgeSimplex(long seed) {
        this.seed = new RNG(seed).lmax();
    }

    public double signed(double x, double z) {
        return simplex(x * FREQUENCY, z * FREQUENCY);
    }

    private double simplex(double x, double y) {
        double skew = (x + y) * F2;
        long i = floor(x + skew);
        long j = floor(y + skew);
        double unskew = (i + j) * G2;
        double x0 = x - (i - unskew);
        double y0 = y - (j - unskew);
        long i1 = x0 > y0 ? 1 : 0;
        long j1 = x0 > y0 ? 0 : 1;
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1 + (2 * G2);
        double y2 = y0 - 1 + (2 * G2);
        return SCALE * (corner(i, j, x0, y0) + corner(i + i1, j + j1, x1, y1) + corner(i + 1, j + 1, x2, y2));
    }

    private double corner(long cellX, long cellY, double x, double y) {
        double falloff = 0.5 - x * x - y * y;
        if (falloff < 0) {
            return 0;
        }
        falloff *= falloff;
        return falloff * falloff * gradient(cellX, cellY, x, y);
    }

    private double gradient(long cellX, long cellY, double x, double y) {
        long hash = seed;
        hash ^= X_PRIME * cellX;
        hash ^= Y_PRIME * cellY;
        hash = hash * hash * hash * HASH_MULTIPLIER;
        hash = (hash >> 13) ^ hash;
        int index = ((int) hash & 7) << 1;
        return (x * GRADIENTS[index]) + (y * GRADIENTS[index + 1]);
    }

    private static long floor(double value) {
        return value >= 0 ? (long) value : (long) value - 1;
    }
}
