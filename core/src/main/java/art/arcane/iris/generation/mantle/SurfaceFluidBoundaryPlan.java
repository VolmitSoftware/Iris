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

package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;

final class SurfaceFluidBoundaryPlan {
    static final int NO_BOUNDARY = Integer.MAX_VALUE;
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;

    private SurfaceFluidBoundaryPlan() {
    }

    static void fill(
            int[] chunkSurfaceHeights,
            double[] fieldSurfaceHeights,
            boolean[] fieldHasFluid,
            double[] fieldFluidHeights,
            int fieldSize,
            int padding,
            long[] boundaries
    ) {
        if (chunkSurfaceHeights == null || chunkSurfaceHeights.length < CHUNK_AREA
                || boundaries == null || boundaries.length < CHUNK_AREA
                || padding < 1 || fieldSize < CHUNK_SIZE + (padding * 2)
                || fieldSurfaceHeights == null || fieldSurfaceHeights.length < fieldSize * fieldSize
                || fieldHasFluid == null || fieldHasFluid.length < fieldSize * fieldSize
                || fieldFluidHeights == null || fieldFluidHeights.length < fieldSize * fieldSize) {
            throw new IllegalArgumentException("Surface fluid boundary fields do not cover a padded chunk");
        }

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int fieldX = localX + padding;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int fieldZ = localZ + padding;
                int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                int boundaryY = NO_BOUNDARY;
                int boundaryEndY = Integer.MIN_VALUE;
                int surfaceY = chunkSurfaceHeights[columnIndex];
                int fieldIndex = (fieldX * fieldSize) + fieldZ;
                int fluidHeight = roundedHeight(fieldFluidHeights[fieldIndex]);
                if (fieldHasFluid[fieldIndex] && surfaceY < fluidHeight) {
                    boundaryY = surfaceY;
                    boundaryEndY = fluidHeight;
                }

                long boundary = expandBoundary(boundaryY, boundaryEndY, fieldSurfaceHeights,
                        fieldHasFluid, fieldFluidHeights, ((fieldX - 1) * fieldSize) + fieldZ);
                boundary = expandBoundary(startY(boundary), endY(boundary), fieldSurfaceHeights,
                        fieldHasFluid, fieldFluidHeights, ((fieldX + 1) * fieldSize) + fieldZ);
                boundary = expandBoundary(startY(boundary), endY(boundary), fieldSurfaceHeights,
                        fieldHasFluid, fieldFluidHeights, (fieldX * fieldSize) + fieldZ - 1);
                boundaries[columnIndex] = expandBoundary(startY(boundary), endY(boundary), fieldSurfaceHeights,
                        fieldHasFluid, fieldFluidHeights, (fieldX * fieldSize) + fieldZ + 1);
            }
        }
    }

    static boolean protects(long[] boundaries, int columnIndex, int y) {
        if (boundaries == null || columnIndex < 0 || columnIndex >= boundaries.length) {
            return false;
        }
        long boundary = boundaries[columnIndex];
        return y >= startY(boundary) && y <= endY(boundary);
    }

    static int startY(long boundary) {
        return (int) (boundary >> 32);
    }

    static int endY(long boundary) {
        return (int) boundary;
    }

    private static long expandBoundary(
            int currentBoundaryY,
            int currentBoundaryEndY,
            double[] fieldSurfaceHeights,
            boolean[] fieldHasFluid,
            double[] fieldFluidHeights,
            int fieldIndex
    ) {
        int fluidHeight = roundedHeight(fieldFluidHeights[fieldIndex]);
        int neighborSurfaceY = (int) Math.round(fieldSurfaceHeights[fieldIndex]);
        if (!fieldHasFluid[fieldIndex] || neighborSurfaceY >= fluidHeight) {
            return boundary(currentBoundaryY, currentBoundaryEndY);
        }
        return boundary(
                Math.min(currentBoundaryY, neighborSurfaceY + 1),
                Math.max(currentBoundaryEndY, fluidHeight)
        );
    }

    private static int roundedHeight(double height) {
        return Double.isFinite(height) ? (int) Math.round(height) : Integer.MIN_VALUE;
    }

    static long boundary(int startY, int endY) {
        return ((long) startY << 32) | (endY & 0xffffffffL);
    }
}
