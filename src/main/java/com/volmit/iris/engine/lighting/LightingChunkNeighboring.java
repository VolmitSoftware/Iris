/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.lighting;

/**
 * Keeps track of the 4 x/z neighbors of chunks
 */
public class LightingChunkNeighboring {
    public final LightingChunk[] values = new LightingChunk[4];

    /**
     * Generates a key ranging 0 - 3 for fixed x/z combinations<br>
     * - Bit 1 is set to contain which of the two is not 1<br>
     * - Bit 2 is set to contain whether x/z is 1 or -1<br><br>
     * <p/>
     * This system requires that the x/z pairs are one the following:<br>
     * (0, 1) | (0, -1) | (1, 0) | (-1, 0)
     *
     * @param x value
     * @param z value
     * @return key
     */
    private static final int getIndexByChunk(int x, int z) {
        return (x & 1) | ((x + z + 1) & 0x2);
    }

    /**
     * Gets whether all 4 chunk neighbors are accessible
     *
     * @return True if all neighbors are accessible
     */
    public boolean hasAll() {
        for (int i = 0; i < 4; i++) {
            if (values[i] == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the neighbor representing the given relative chunk
     *
     * @return neighbor
     */
    public LightingChunk get(int deltaChunkX, int deltaChunkZ) {
        return values[getIndexByChunk(deltaChunkX, deltaChunkZ)];
    }

    /**
     * Gets a relative neighboring chunk, and then a vertical cube in that chunk, if possible.
     *
     * @param cy Cube absolute y-coordinate
     * @return cube, null if the chunk or cube is not available
     */
    public LightingCube getCube(int deltaChunkX, int deltaChunkZ, int cy) {
        LightingChunk chunk = get(deltaChunkX, deltaChunkZ);
        return (chunk == null) ? null : chunk.sections.get(cy);
    }

    /**
     * Sets the neighbor representing the given relative chunk
     *
     * @param neighbor to set to
     */
    public void set(int deltaChunkX, int deltaChunkZ, LightingChunk neighbor) {
        values[getIndexByChunk(deltaChunkX, deltaChunkZ)] = neighbor;
    }
}
