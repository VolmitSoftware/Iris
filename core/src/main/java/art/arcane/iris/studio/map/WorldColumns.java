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

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.stream.ProceduralStream;

/**
 * The world's terrain as the engine plans it, without carving or objects: each column at its terrain height, coloured
 * by the chosen colouring.
 */
record WorldColumns(Engine engine, WorldColoring coloring) implements TerrainColumns {
    private static final int UNCOLORED = 0x000000;

    @Override
    public void sampleRow(TerrainGrid grid, int row, float[] heights, int[] colors) {
        ProceduralStream<Double> terrainHeights = engine.getComplex().getHeightStream();
        double worldZ = grid.originZ() + (double) row * grid.step();
        for (int column = 0; column < grid.columns(); column++) {
            double worldX = grid.originX() + (double) column * grid.step();
            int index = row * grid.columns() + column;
            heights[index] = (float) terrainHeights.getDouble(worldX, worldZ);
            WorldColoring.WorldSubject subject = coloring.subjectAt(engine, worldX, worldZ);
            colors[index] = subject == null ? UNCOLORED : subject.color();
        }
    }
}
