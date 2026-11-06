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

package art.arcane.iris.engine.mantle.components;

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
            int fieldSize,
            int padding,
            int fluidHeight,
            int[] boundaryStartY
    ) {
        if (chunkSurfaceHeights == null || chunkSurfaceHeights.length < CHUNK_AREA
                || boundaryStartY == null || boundaryStartY.length < CHUNK_AREA
                || padding < 1 || fieldSize < CHUNK_SIZE + (padding * 2)
                || fieldSurfaceHeights == null || fieldSurfaceHeights.length < fieldSize * fieldSize
                || fieldHasFluid == null || fieldHasFluid.length < fieldSize * fieldSize) {
            throw new IllegalArgumentException("Surface fluid boundary fields do not cover a padded chunk");
        }

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int fieldX = localX + padding;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int fieldZ = localZ + padding;
                int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                int boundaryY = NO_BOUNDARY;
                int surfaceY = chunkSurfaceHeights[columnIndex];
                int fieldIndex = (fieldX * fieldSize) + fieldZ;
                if (fieldHasFluid[fieldIndex] && surfaceY < fluidHeight) {
                    boundaryY = surfaceY;
                }

                boundaryY = lowerBoundary(boundaryY, fieldSurfaceHeights, fieldHasFluid,
                        ((fieldX - 1) * fieldSize) + fieldZ, fluidHeight);
                boundaryY = lowerBoundary(boundaryY, fieldSurfaceHeights, fieldHasFluid,
                        ((fieldX + 1) * fieldSize) + fieldZ, fluidHeight);
                boundaryY = lowerBoundary(boundaryY, fieldSurfaceHeights, fieldHasFluid,
                        (fieldX * fieldSize) + fieldZ - 1, fluidHeight);
                boundaryY = lowerBoundary(boundaryY, fieldSurfaceHeights, fieldHasFluid,
                        (fieldX * fieldSize) + fieldZ + 1, fluidHeight);
                boundaryStartY[columnIndex] = boundaryY;
            }
        }
    }

    static boolean protects(int[] boundaryStartY, int columnIndex, int y, int fluidHeight) {
        return boundaryStartY != null
                && columnIndex >= 0
                && columnIndex < boundaryStartY.length
                && y >= boundaryStartY[columnIndex]
                && y <= fluidHeight;
    }

    private static int lowerBoundary(
            int currentBoundaryY,
            double[] fieldSurfaceHeights,
            boolean[] fieldHasFluid,
            int fieldIndex,
            int fluidHeight
    ) {
        int neighborSurfaceY = (int) Math.round(fieldSurfaceHeights[fieldIndex]);
        if (!fieldHasFluid[fieldIndex] || neighborSurfaceY >= fluidHeight) {
            return currentBoundaryY;
        }
        return Math.min(currentBoundaryY, neighborSurfaceY + 1);
    }
}
