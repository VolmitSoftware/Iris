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

package art.arcane.iris.generation.chunk;

import art.arcane.iris.spi.PlatformBiome;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.hunk.storage.StorageHunk;
import art.arcane.volmlib.util.hunk.HunkMutationSupport;

public class TerrainChunkBiomeHunkView extends StorageHunk<PlatformBiome> implements Hunk<PlatformBiome> {
    private final TerrainChunk chunk;

    public TerrainChunkBiomeHunkView(TerrainChunk chunk) {
        super(16, chunk.getMaxHeight() - chunk.getMinHeight(), 16);
        this.chunk = chunk;
    }

    /**
     * A full height single column is the shape the biome actuator writes for every column of the chunk, so
     * stride the backing biome array in one pass instead of dispatching per block. Any other region falls
     * through to the generic element wise write.
     */
    @Override
    public void set(int x1, int y1, int z1, int x2, int y2, int z2, PlatformBiome biome) {
        if (x1 == x2 && z1 == z2 && y1 == 0 && y2 == getHeight() - 1 && chunk instanceof LinkedTerrainChunk linked) {
            linked.fillBiomeColumn(x1, z1, biome);
            return;
        }

        HunkMutationSupport.setRangeInclusive(this, x1, y1, z1, x2, y2, z2, biome);
    }

    @Override
    public void setRaw(int x, int y, int z, PlatformBiome biome) {
        chunk.setBiome(x, y + chunk.getMinHeight(), z, biome);
    }

    @Override
    public PlatformBiome getRaw(int x, int y, int z) {
        return chunk.getBiome(x, y + chunk.getMinHeight(), z);
    }
}
