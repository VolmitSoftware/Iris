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

package art.arcane.iris.core.studio.noise;

import art.arcane.volmlib.util.function.NoiseProvider;

import java.util.function.BooleanSupplier;

/**
 * Tile values for noise too slow to sample at every pixel: the noise is sampled on a lattice fixed in the world and
 * filled in between along a Catmull-Rom curve. A pixel's value depends only on where it is in the world, so tiles of
 * one scale meet without a seam and tiles of nearby scales share their lattice points.
 */
final class NoiseLattice {
    static final int LATTICE_PIXELS = 4;
    static final double SMALLEST_SPACING_BLOCKS = 1D;
    private static final int POINTS_BEFORE_CELL = 1;
    private static final int POINTS_AFTER_CELL = 2;
    private static final int CURVE_POINTS = 4;

    private NoiseLattice() {
    }

    /** A power of two of blocks, at least {@link #LATTICE_PIXELS} pixels wide and at least a block, so a pixel never asks closer than the world's own grain. */
    static double spacingBlocks(double blocksPerPixel) {
        double wanted = Math.max(LATTICE_PIXELS * blocksPerPixel, SMALLEST_SPACING_BLOCKS);
        double spacing = SMALLEST_SPACING_BLOCKS;
        while (spacing < wanted) {
            spacing *= 2D;
        }
        return spacing;
    }

    static float[] values(NoiseProvider noise, double originX, double originZ, double blocksPerPixel, int size, BooleanSupplier cancelled) {
        double spacing = spacingBlocks(blocksPerPixel);
        double reach = (size - 1) * blocksPerPixel;
        int firstColumn = (int) Math.floor(originX / spacing) - POINTS_BEFORE_CELL;
        int firstRow = (int) Math.floor(originZ / spacing) - POINTS_BEFORE_CELL;
        int pointsAcross = (int) Math.floor((originX + reach) / spacing) + POINTS_AFTER_CELL - firstColumn + 1;
        int pointsDown = (int) Math.floor((originZ + reach) / spacing) + POINTS_AFTER_CELL - firstRow + 1;
        double[] points = new double[pointsAcross * pointsDown];
        for (int row = 0; row < pointsDown; row++) {
            NoiseTile.requireWanted(cancelled);
            for (int column = 0; column < pointsAcross; column++) {
                points[row * pointsAcross + column] = noise.noise((firstColumn + column) * spacing, (firstRow + row) * spacing);
            }
        }

        float[] values = new float[size * size];
        double[] curveRows = new double[CURVE_POINTS];
        for (int pixelRow = 0; pixelRow < size; pixelRow++) {
            NoiseTile.requireWanted(cancelled);
            double latticeZ = (originZ + pixelRow * blocksPerPixel) / spacing;
            int cellZ = (int) Math.floor(latticeZ);
            double fractionZ = latticeZ - cellZ;
            for (int pixelColumn = 0; pixelColumn < size; pixelColumn++) {
                double latticeX = (originX + pixelColumn * blocksPerPixel) / spacing;
                int cellX = (int) Math.floor(latticeX);
                double fractionX = latticeX - cellX;
                for (int curveRow = 0; curveRow < CURVE_POINTS; curveRow++) {
                    int offset = (cellZ - firstRow - POINTS_BEFORE_CELL + curveRow) * pointsAcross
                            + cellX - firstColumn - POINTS_BEFORE_CELL;
                    curveRows[curveRow] = CatmullRom.between(points[offset], points[offset + 1], points[offset + 2], points[offset + 3], fractionX);
                }
                values[pixelRow * size + pixelColumn] = (float) CatmullRom.between(curveRows[0], curveRows[1], curveRows[2], curveRows[3], fractionZ);
            }
        }
        return values;
    }
}
