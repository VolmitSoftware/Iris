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

package art.arcane.iris.util.project.hunk.view;

import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.hunk.storage.StorageHunk;
import org.bukkit.block.Biome;

public class TerrainChunkBiomeHunkView extends StorageHunk<Biome> implements Hunk<Biome> {
    private final TerrainChunk chunk;

    public TerrainChunkBiomeHunkView(TerrainChunk chunk) {
        super(16, chunk.getMaxHeight() - chunk.getMinHeight(), 16);
        this.chunk = chunk;
    }

    @Override
    public void setRaw(int x, int y, int z, Biome biome) {
        chunk.setBiome(x, y + chunk.getMinHeight(), z, biome);
    }

    @Override
    public Biome getRaw(int x, int y, int z) {
        return chunk.getBiome(x, y + chunk.getMinHeight(), z);
    }
}
