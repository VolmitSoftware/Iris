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

import art.arcane.iris.platform.bukkit.BukkitBiome;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.IrisCustomData;
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
    private final NativeBiome[] biomes;
    private volatile NativeBiome defaultBiome;

    public LinkedTerrainChunk(World world) {
        this(Bukkit.createChunkData(world));
    }

    public LinkedTerrainChunk(ChunkData data) {
        rawChunkData = data;
        minHeight = data.getMinHeight();
        maxHeight = data.getMaxHeight();
        biomeHeight = Math.max(1, maxHeight - minHeight);
        biomes = new NativeBiome[CHUNK_SIZE * biomeHeight * CHUNK_SIZE];
    }

    @Override
    public NativeBiome getBiome(int x, int y, int z) {
        int index = biomeIndex(x, y, z);
        NativeBiome biome = biomes[index];
        if (biome != null) {
            return biome;
        }
        NativeBiome fallback = defaultBiome;
        if (fallback == null) {
            fallback = BukkitBiome.of(Biome.PLAINS);
            defaultBiome = fallback;
        }
        return fallback;
    }

    @Override
    public void setBiome(int x, int y, int z, NativeBiome bio) {
        biomes[biomeIndex(x, y, z)] = canonicalBiome(bio);
    }

    /**
     * Writes the whole vertical column in one pass, striding the flat biome array directly. Equivalent to
     * calling setBiome for every y in [minHeight, maxHeight).
     */
    public void fillBiomeColumn(int x, int z, NativeBiome bio) {
        NativeBiome handle = canonicalBiome(bio);
        int stride = CHUNK_SIZE * CHUNK_SIZE;
        int index = (z & (CHUNK_SIZE - 1)) * CHUNK_SIZE + (x & (CHUNK_SIZE - 1));

        for (int y = 0; y < biomeHeight; y++) {
            biomes[index] = handle;
            index += stride;
        }
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
    public synchronized void setBlock(int x, int y, int z, NativeBlockState state) {
        BlockData blockData = (BlockData) state.nativeHandle();
        if (blockData instanceof IrisCustomData data) {
            blockData = data.getBase();
        }
        rawChunkData.setBlock(x, y, z, blockData);
    }

    @Override
    public synchronized void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, NativeBlockState state) {
        BlockData blockData = (BlockData) state.nativeHandle();
        if (blockData instanceof IrisCustomData data) {
            blockData = data.getBase();
        }
        rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, blockData);
    }

    @Override
    public NativeBlockState getBlockData(int x, int y, int z) {
        return BukkitBlockState.of(rawChunkData.getBlockData(x, y, z));
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

    private static NativeBiome canonicalBiome(NativeBiome biome) {
        if (biome instanceof BukkitBiome) {
            return biome;
        }
        Biome handle = (Biome) biome.nativeHandle();
        return handle == null ? null : BukkitBiome.of(handle);
    }
}
