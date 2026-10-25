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

import static org.lwjgl.opengl.GL12.*;

/**
 * Terrain's column colours on the graphics card, one texel per column, so faces running over many columns still colour
 * each one. Only for the GPU thread.
 */
final class GpuColors {
    private static final int BYTES_PER_COLUMN = 4;

    private final int texture = glGenTextures();
    private final long bytes;

    private GpuColors(NoiseTerrain terrain) {
        this.bytes = (long) terrain.columns() * terrain.rows() * BYTES_PER_COLUMN;
    }

    /**
     * @param largestSide the card's GL_MAX_TEXTURE_SIZE
     * @return the colours, or null when the terrain has more columns or rows than the card's textures can hold
     */
    static GpuColors upload(NoiseTerrain terrain, int largestSide) {
        if (terrain.columns() > largestSide || terrain.rows() > largestSide) {
            return null;
        }
        GpuColors uploaded = new GpuColors(terrain);
        glBindTexture(GL_TEXTURE_2D, uploaded.texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, terrain.columns(), terrain.rows(), 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, terrain.colors());
        return uploaded;
    }

    long bytes() {
        return bytes;
    }

    void bind() {
        glBindTexture(GL_TEXTURE_2D, texture);
    }

    void delete() {
        glDeleteTextures(texture);
    }
}
