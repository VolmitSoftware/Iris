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

package com.volmit.iris.engine.data.chunk;

import com.volmit.iris.core.nms.BiomeBaseInjector;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public interface TerrainChunk extends BiomeGrid, ChunkData {
    static TerrainChunk create(World world) {
        return new LinkedTerrainChunk(world);
    }

    static TerrainChunk create(World world, BiomeGrid grid) {
        return new LinkedTerrainChunk(world, grid);
    }

    static TerrainChunk createUnsafe(World world, BiomeGrid grid) {
        LinkedTerrainChunk ltc = new LinkedTerrainChunk(world, grid);
        ltc.setUnsafe(true);
        return ltc;
    }

    static TerrainChunk create(ChunkData raw, BiomeGrid grid) {
        return new LinkedTerrainChunk(grid, raw);
    }

    BiomeBaseInjector getBiomeBaseInjector();

    /**
     * Get biome at x, z within chunk being generated
     *
     * @param x - 0-15
     * @param z - 0-15
     * @return Biome value
     * @deprecated biomes are now 3-dimensional
     */

    @Deprecated
    Biome getBiome(int x, int z);

    /**
     * Get biome at x, z within chunk being generated
     *
     * @param x - 0-15
     * @param y - 0-255
     * @param z - 0-15
     * @return Biome value
     */
    Biome getBiome(int x, int y, int z);

    /**
     * Set biome at x, z within chunk being generated
     *
     * @param x   - 0-15
     * @param z   - 0-15
     * @param bio - Biome value
     * @deprecated biomes are now 3-dimensional
     */
    @Deprecated
    void setBiome(int x, int z, Biome bio);

    /**
     * Set biome at x, z within chunk being generated
     *
     * @param x   - 0-15
     * @param y   - 0-255
     * @param z   - 0-15
     * @param bio - Biome value
     */
    void setBiome(int x, int y, int z, Biome bio);

    /**
     * Get the maximum height for the chunk.
     * <p>
     * Setting blocks at or above this height will do nothing.
     *
     * @return the maximum height
     */
    int getMaxHeight();

    /**
     * Set the block at x,y,z in the chunk data to material.
     * <p>
     * Setting blocks outside the chunk's bounds does nothing.
     *
     * @param x         the x location in the chunk from 0-15 inclusive
     * @param y         the y location in the chunk from 0 (inclusive) - maxHeight
     *                  (exclusive)
     * @param z         the z location in the chunk from 0-15 inclusive
     * @param blockData the type to set the block to
     */
    void setBlock(int x, int y, int z, BlockData blockData);

    /**
     * Get the type and data of the block at x, y, z.
     * <p>
     * Getting blocks outside the chunk's bounds returns air.
     *
     * @param x the x location in the chunk from 0-15 inclusive
     * @param y the y location in the chunk from 0 (inclusive) - maxHeight
     *          (exclusive)
     * @param z the z location in the chunk from 0-15 inclusive
     * @return the data of the block or the BlockData for air if x, y or z are
     * outside the chunk's bounds
     */
    BlockData getBlockData(int x, int y, int z);

    ChunkData getRaw();

    void setRaw(ChunkData data);

    void inject(BiomeGrid biome);
}
