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

package art.arcane.iris.modules.carve.field;

/**
 * Where one cave may carve in one chunk, and how freely: fully inside its depth range and well under the surface,
 * narrowing to nothing towards the ends of the range, towards the rock it keeps under the surface and towards columns
 * that do not list it. Columns are indexed as {@code (localX << 4) | localZ}; heights count from the dimension's bottom.
 */
public final class CaveLimits {
    private static final int CHUNK_COLUMNS = 256;
    private static final int CELL_COLUMNS = ChunkLattice.CELLS_PER_CHUNK_AXIS * ChunkLattice.CELLS_PER_CHUNK_AXIS;
    /** A cave that keeps rock above it closes over this many blocks instead of ending in a flat ceiling. */
    private static final int CEILING_FADE_BLOCKS = 8;

    private final int lowestY;
    private final int highestY;
    private final int depthMinY;
    private final int depthMaxY;
    private final int fadeBlocks;
    private final int[] columnTopY;
    private final boolean[] columnOpensToSurface;
    private final double[] columnWeight;
    private final int[] cellTopY = new int[CELL_COLUMNS];
    private final boolean[] cellHasWeight = new boolean[CELL_COLUMNS];

    /**
     * @param depthMinY            lowest height of the cave's depth range
     * @param depthMaxY            highest height of the cave's depth range
     * @param fadeBlocks           blocks over which the cave narrows at both ends of the range
     * @param columnTopY           highest height the cave may carve in each column
     * @param columnOpensToSurface whether the cave reaches its top at full width in each column, instead of closing under it
     * @param columnWeight         how much each column lists the cave, from 0 to 1
     */
    public CaveLimits(int depthMinY, int depthMaxY, int fadeBlocks, int[] columnTopY, boolean[] columnOpensToSurface, double[] columnWeight) {
        this.depthMinY = depthMinY;
        this.depthMaxY = depthMaxY;
        this.fadeBlocks = fadeBlocks;
        this.columnTopY = columnTopY;
        this.columnOpensToSurface = columnOpensToSurface;
        this.columnWeight = columnWeight;
        java.util.Arrays.fill(cellTopY, Integer.MIN_VALUE);
        int highestTopY = Integer.MIN_VALUE;
        for (int column = 0; column < CHUNK_COLUMNS; column++) {
            if (columnWeight[column] <= 0D) {
                continue;
            }
            int cell = cellOf(column >> 4, column & 15);
            cellHasWeight[cell] = true;
            cellTopY[cell] = Math.max(cellTopY[cell], columnTopY[column]);
            highestTopY = Math.max(highestTopY, columnTopY[column]);
        }
        this.lowestY = depthMinY;
        this.highestY = Math.min(depthMaxY, highestTopY);
    }

    private static int cellOf(int localX, int localZ) {
        return ((localX / ChunkLattice.CELL_BLOCKS) * ChunkLattice.CELLS_PER_CHUNK_AXIS) + (localZ / ChunkLattice.CELL_BLOCKS);
    }

    public int lowestY() {
        return lowestY;
    }

    public int highestY() {
        return highestY;
    }

    /** Whether the cave carves nothing in this chunk. */
    public boolean carvesNothing() {
        return highestY < lowestY;
    }

    /** Whether no column of the cell lets the cave carve at or above {@code bottomY}. */
    boolean cellIsClosed(int cellX, int cellZ, int bottomY) {
        int cell = (cellX * ChunkLattice.CELLS_PER_CHUNK_AXIS) + cellZ;
        return !cellHasWeight[cell] || cellTopY[cell] < bottomY;
    }

    /** How freely the cave carves the block, from 0 (not at all) to 1 (at its full size). */
    double openness(int column, int y) {
        double weight = columnWeight[column];
        if (weight <= 0D || y < depthMinY || y > depthMaxY) {
            return 0D;
        }
        int belowTop = columnTopY[column] - y;
        if (belowTop < 0) {
            return 0D;
        }
        double openness = weight * easeIn(Math.min(y - depthMinY, depthMaxY - y), fadeBlocks);
        if (!columnOpensToSurface[column]) {
            openness *= easeIn(belowTop, CEILING_FADE_BLOCKS);
        }
        return openness;
    }

    private static double easeIn(int distance, int fadeBlocks) {
        if (fadeBlocks <= 0 || distance >= fadeBlocks) {
            return 1D;
        }
        double share = distance / (double) fadeBlocks;
        return share * share * (3D - (2D * share));
    }
}
