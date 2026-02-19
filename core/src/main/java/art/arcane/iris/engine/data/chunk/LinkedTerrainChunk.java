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

package art.arcane.iris.engine.data.chunk;

import art.arcane.iris.util.common.data.IrisCustomData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public class LinkedTerrainChunk implements TerrainChunk {
    private static final int CHUNK_SIZE = 16;
    private final ChunkData rawChunkData;
    private final int minHeight;
    private final int maxHeight;
    private final int biomeHeight;
    private final Biome[] biomes;

    public LinkedTerrainChunk(World world) {
        this(Bukkit.createChunkData(world));
    }

    public LinkedTerrainChunk(ChunkData data) {
        rawChunkData = data;
        minHeight = data.getMinHeight();
        maxHeight = data.getMaxHeight();
        biomeHeight = Math.max(1, maxHeight - minHeight);
        biomes = new Biome[CHUNK_SIZE * biomeHeight * CHUNK_SIZE];
    }

    @Override
    public Biome getBiome(int x, int y, int z) {
        int index = biomeIndex(x, y, z);
        Biome biome = biomes[index];
        return biome == null ? Biome.PLAINS : biome;
    }

    @Override
    public void setBiome(int x, int y, int z, Biome bio) {
        biomes[biomeIndex(x, y, z)] = bio;
    }

    @Override
    public int getMinHeight() {
        return minHeight;
    }

    @Override
    public int getMaxHeight() {
        return maxHeight;
    }

    @Override
    public synchronized void setBlock(int x, int y, int z, BlockData blockData) {
        if (blockData instanceof IrisCustomData data) {
            blockData = data.getBase();
        }
        rawChunkData.setBlock(x, y, z, blockData);
    }

    @Override
    public synchronized void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData) {
        rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, blockData);
    }

    @Override
    public BlockData getBlockData(int x, int y, int z) {
        return rawChunkData.getBlockData(x, y, z);
    }

    @Override
    public ChunkData getChunkData() {
        return rawChunkData;
    }

    private int biomeIndex(int x, int y, int z) {
        int clampedX = x & (CHUNK_SIZE - 1);
        int clampedZ = z & (CHUNK_SIZE - 1);
        int clampedY = Math.max(minHeight, Math.min(maxHeight - 1, y)) - minHeight;
        return (clampedY * CHUNK_SIZE + clampedZ) * CHUNK_SIZE + clampedX;
    }
}
