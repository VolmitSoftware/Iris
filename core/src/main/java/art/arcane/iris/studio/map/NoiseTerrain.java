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

package art.arcane.iris.studio.map;

import java.util.Arrays;

/**
 * A stretch of noise or world as terrain at Minecraft scale: one height in blocks and one colour per column, each column
 * {@code step} blocks wide and deep. Columns without a valid value are left out.
 */
record NoiseTerrain(int originX, int originZ, int columns, int rows, int step, float[] heights, int[] colors,
                    double lowest, double highest) {
    /**
     * Samples every column of the grid.
     */
    static NoiseTerrain sample(TerrainSampling sampling, TerrainGrid grid) {
        float[] heights = new float[grid.columns() * grid.rows()];
        int[] colors = new int[heights.length];
        for (int row = 0; row < grid.rows(); row++) {
            sampling.columns().sampleRow(grid, row, heights, colors);
        }
        return of(grid, heights, colors);
    }

    static NoiseTerrain empty(TerrainGrid grid) {
        float[] heights = new float[grid.columns() * grid.rows()];
        Arrays.fill(heights, Float.NaN);
        return of(grid, heights, new int[heights.length]);
    }

    private static NoiseTerrain of(TerrainGrid grid, float[] heights, int[] colors) {
        double lowest = Double.NaN;
        double highest = Double.NaN;
        for (float height : heights) {
            if (Float.isNaN(height)) {
                continue;
            }
            lowest = Double.isNaN(lowest) ? height : Math.min(lowest, height);
            highest = Double.isNaN(highest) ? height : Math.max(highest, height);
        }
        return new NoiseTerrain(grid.originX(), grid.originZ(), grid.columns(), grid.rows(), grid.step(), heights, colors,
                lowest, highest);
    }

    boolean hasHeight(int column, int row) {
        return column >= 0 && row >= 0 && column < columns && row < rows && !Float.isNaN(heights[row * columns + column]);
    }

    float height(int column, int row) {
        return heights[row * columns + column];
    }

    int color(int column, int row) {
        return colors[row * columns + column];
    }

    // The world position of a column's or row's first block.
    double worldX(double column) {
        return originX + column * step;
    }

    double worldZ(double row) {
        return originZ + row * step;
    }

    int sizeX() {
        return columns * step;
    }

    int sizeZ() {
        return rows * step;
    }

    boolean isEmpty() {
        return Double.isNaN(lowest);
    }

    // A block below the lowest point, where sides on the terrain's edge end.
    int baseY() {
        return (int) Math.floor(lowest) - 1;
    }
}
