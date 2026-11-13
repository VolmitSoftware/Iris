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

import java.util.Arrays;

/**
 * Where one cave may carve in one chunk, and how freely: fully inside its depth range and well under the surface,
 * narrowing to nothing towards the ends of the range, towards the rock it keeps under the surface and towards columns
 * that do not list it. Columns are indexed as {@code (localX << 4) | localZ}; heights count from the dimension's bottom.
 */
final class CaveLimits {
    static final int CHUNK_COLUMNS = 256;
    private static final int CELL_COLUMNS = ChunkLattice.CELLS_PER_CHUNK_AXIS * ChunkLattice.CELLS_PER_CHUNK_AXIS;

    private final int[] depthMinYs;
    private final int[] depthMaxYs;
    private final double[] fadeBlocks;
    private final double[] ceilingFadeBlocks;
    private final int[] topY;
    private final boolean[] opensToSurface;
    private final double[] weight;
    /** Whether depth and fades are the same in every column, as they are for plain numbers; then they are read once. */
    private final boolean sameRangeInEveryColumn;
    private final int depthMinY;
    private final int depthMaxY;
    private final double fade;
    private final double ceilingFade;
    private final int lowestY;
    private final int highestY;
    private final int[] cellTopY = new int[CELL_COLUMNS];
    private final boolean[] cellHasWeight = new boolean[CELL_COLUMNS];

    /**
     * What the cave allows in each column of the chunk.
     *
     * @param depthMinY         lowest height of the cave's depth range
     * @param depthMaxY         highest height of the cave's depth range
     * @param fadeBlocks        blocks over which the cave narrows at both ends of the range
     * @param topY              highest height the cave may carve
     * @param opensToSurface    whether the cave reaches its top at full width, instead of closing under it
     * @param ceilingFadeBlocks blocks over which a cave that does not open to the surface closes under its top
     * @param weight            how much the column lists the cave, from 0 to 1
     */
    record Columns(int[] depthMinY, int[] depthMaxY, double[] fadeBlocks, int[] topY, boolean[] opensToSurface,
                   double[] ceilingFadeBlocks, double[] weight) {
        static Columns of(double[] weight) {
            return new Columns(new int[CHUNK_COLUMNS], new int[CHUNK_COLUMNS], new double[CHUNK_COLUMNS], new int[CHUNK_COLUMNS],
                    new boolean[CHUNK_COLUMNS], new double[CHUNK_COLUMNS], weight);
        }
    }

    CaveLimits(Columns columns) {
        this.depthMinYs = columns.depthMinY();
        this.depthMaxYs = columns.depthMaxY();
        this.fadeBlocks = columns.fadeBlocks();
        this.ceilingFadeBlocks = columns.ceilingFadeBlocks();
        this.topY = columns.topY();
        this.opensToSurface = columns.opensToSurface();
        this.weight = columns.weight();
        this.sameRangeInEveryColumn = isTheSameInEveryColumn(depthMinYs) && isTheSameInEveryColumn(depthMaxYs)
                && isTheSameInEveryColumn(fadeBlocks) && isTheSameInEveryColumn(ceilingFadeBlocks);
        this.depthMinY = depthMinYs[0];
        this.depthMaxY = depthMaxYs[0];
        this.fade = fadeBlocks[0];
        this.ceilingFade = ceilingFadeBlocks[0];
        Arrays.fill(cellTopY, Integer.MIN_VALUE);
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int column = 0; column < CHUNK_COLUMNS; column++) {
            if (columns.weight()[column] <= 0D) {
                continue;
            }
            int cell = cellOf(column >> 4, column & 15);
            cellHasWeight[cell] = true;
            cellTopY[cell] = Math.max(cellTopY[cell], topY[column]);
            lowest = Math.min(lowest, depthMinYs[column]);
            highest = Math.max(highest, Math.min(depthMaxYs[column], topY[column]));
        }
        this.lowestY = lowest;
        this.highestY = highest;
    }

    private static int cellOf(int localX, int localZ) {
        return ((localX / ChunkLattice.CELL_BLOCKS) * ChunkLattice.CELLS_PER_CHUNK_AXIS) + (localZ / ChunkLattice.CELL_BLOCKS);
    }

    int lowestY() {
        return lowestY;
    }

    int highestY() {
        return highestY;
    }

    /** Whether the cave carves nothing in this chunk. */
    boolean carvesNothing() {
        return highestY < lowestY;
    }

    /** Whether no column of the cell lets the cave carve at or above {@code bottomY}. */
    boolean cellIsClosed(int cellX, int cellZ, int bottomY) {
        int cell = (cellX * ChunkLattice.CELLS_PER_CHUNK_AXIS) + cellZ;
        return !cellHasWeight[cell] || cellTopY[cell] < bottomY;
    }

    /** How freely the cave carves the block, from 0 (not at all) to 1 (at its full size). */
    double openness(int column, int y) {
        if (sameRangeInEveryColumn) {
            return openness(column, y, depthMinY, depthMaxY, fade, ceilingFade);
        }
        return openness(column, y, depthMinYs[column], depthMaxYs[column], fadeBlocks[column], ceilingFadeBlocks[column]);
    }

    private double openness(int column, int y, int columnDepthMinY, int columnDepthMaxY, double columnFade, double columnCeilingFade) {
        double columnWeight = weight[column];
        if (columnWeight <= 0D || y < columnDepthMinY || y > columnDepthMaxY) {
            return 0D;
        }
        int belowTop = topY[column] - y;
        if (belowTop < 0) {
            return 0D;
        }
        double openness = columnWeight * easeIn(Math.min(y - columnDepthMinY, columnDepthMaxY - y), columnFade);
        if (!opensToSurface[column]) {
            openness *= easeIn(belowTop, columnCeilingFade);
        }
        return openness;
    }

    private static boolean isTheSameInEveryColumn(int[] values) {
        for (int column = 1; column < values.length; column++) {
            if (values[column] != values[0]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTheSameInEveryColumn(double[] values) {
        for (int column = 1; column < values.length; column++) {
            if (values[column] != values[0]) {
                return false;
            }
        }
        return true;
    }

    private static double easeIn(int distance, double fadeBlocks) {
        if (fadeBlocks <= 0D || distance >= fadeBlocks) {
            return 1D;
        }
        double share = distance / fadeBlocks;
        return share * share * (3D - (2D * share));
    }
}
