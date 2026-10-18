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


package com.volmit.iris.core.gui;

import java.util.Random;

// A copy of Minecraft's Biome.BIOME_INFO_NOISE, the 2D simplex noise swamps use to pick between their two grass colours.
// Minecraft seeds it through a LegacyRandomSource, which draws the same numbers as java.util.Random.
final class BiomeInfoNoise {
    private static final long SEED = 2345L;
    private static final int PERMUTATION_SIZE = 256;
    private static final int GRADIENT_COUNT = 12;
    private static final double SKEW = 0.5D * (Math.sqrt(3D) - 1D);
    private static final double UNSKEW = (3D - Math.sqrt(3D)) / 6D;
    private static final double CORNER_RADIUS = 0.5D;
    private static final double OUTPUT_SCALE = 70D;
    private static final int[][] GRADIENTS = {
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
            {1, 0}, {-1, 0}, {1, 0}, {-1, 0},
            {0, 1}, {0, -1}, {0, 1}, {0, -1}
    };
    private static final int[] PERMUTATION = createPermutation();

    private BiomeInfoNoise() {
    }

    static float at(double x, double z) {
        double skewOffset = (x + z) * SKEW;
        int cellX = (int) Math.floor(x + skewOffset);
        int cellZ = (int) Math.floor(z + skewOffset);
        double unskewOffset = (cellX + cellZ) * UNSKEW;
        double x0 = x - (cellX - unskewOffset);
        double z0 = z - (cellZ - unskewOffset);
        int stepX = x0 > z0 ? 1 : 0;
        int stepZ = 1 - stepX;
        double x1 = x0 - stepX + UNSKEW;
        double z1 = z0 - stepZ + UNSKEW;
        double x2 = x0 - 1D + 2D * UNSKEW;
        double z2 = z0 - 1D + 2D * UNSKEW;
        int wrappedX = cellX & 0xFF;
        int wrappedZ = cellZ & 0xFF;
        int gradient0 = permute(wrappedX + permute(wrappedZ)) % GRADIENT_COUNT;
        int gradient1 = permute(wrappedX + stepX + permute(wrappedZ + stepZ)) % GRADIENT_COUNT;
        int gradient2 = permute(wrappedX + 1 + permute(wrappedZ + 1)) % GRADIENT_COUNT;
        return (float) (OUTPUT_SCALE * (corner(gradient0, x0, z0) + corner(gradient1, x1, z1) + corner(gradient2, x2, z2)));
    }

    private static double corner(int gradient, double x, double z) {
        double falloff = CORNER_RADIUS - x * x - z * z;
        if (falloff < 0D) {
            return 0D;
        }
        falloff *= falloff;
        return falloff * falloff * (GRADIENTS[gradient][0] * x + GRADIENTS[gradient][1] * z);
    }

    private static int permute(int index) {
        return PERMUTATION[index & 0xFF];
    }

    // Minecraft draws three offsets first and discards them for this noise, so they are drawn here too to stay in step.
    private static int[] createPermutation() {
        Random random = new Random(SEED);
        for (int discardedOffset = 0; discardedOffset < 3; discardedOffset++) {
            random.nextDouble();
        }
        int[] permutation = new int[PERMUTATION_SIZE];
        for (int i = 0; i < PERMUTATION_SIZE; i++) {
            permutation[i] = i;
        }
        for (int i = 0; i < PERMUTATION_SIZE; i++) {
            int swap = i + random.nextInt(PERMUTATION_SIZE - i);
            int previous = permutation[i];
            permutation[i] = permutation[swap];
            permutation[swap] = previous;
        }
        return permutation;
    }
}
