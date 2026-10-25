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

import art.arcane.volmlib.util.function.NoiseProvider;

import java.util.function.DoubleUnaryOperator;

/**
 * Noise as terrain columns: each value raised to a height and coloured by the noise's colour scale.
 *
 * @param heightOf turns a noise value into a height in blocks
 */
record NoiseColumns(NoiseProvider noise, DoubleUnaryOperator heightOf, NoiseColorScale colorScale) implements TerrainColumns {
    @Override
    public void sampleRow(TerrainGrid grid, int row, float[] heights, int[] colors) {
        double worldZ = grid.originZ() + (double) row * grid.step();
        for (int column = 0; column < grid.columns(); column++) {
            double worldX = grid.originX() + (double) column * grid.step();
            double value = noise.noise(worldX, worldZ);
            int index = row * grid.columns() + column;
            heights[index] = Double.isFinite(value) ? (float) heightOf.applyAsDouble(value) : Float.NaN;
            colors[index] = Double.isFinite(value) ? colorScale.colorAt(value, worldX, worldZ) : 0;
        }
    }
}
