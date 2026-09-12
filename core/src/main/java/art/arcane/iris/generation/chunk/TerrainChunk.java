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
import art.arcane.iris.spi.PlatformBlockState;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public interface TerrainChunk {
    static TerrainChunk create(World world) {
        return new LinkedTerrainChunk(world);
    }

    static TerrainChunk create(ChunkData raw) {
        return new LinkedTerrainChunk(raw);
    }

    PlatformBiome getBiome(int x, int y, int z);

    void setBiome(int x, int y, int z, PlatformBiome bio);
    int getMinHeight();
    int getMaxHeight();
    void setBlock(int x, int y, int z, PlatformBlockState blockData);
    void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, PlatformBlockState blockData);
    PlatformBlockState getBlockData(int x, int y, int z);
    ChunkData getChunkData();
}
