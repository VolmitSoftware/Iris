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

package com.volmit.iris.util;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.BiomeBaseInjector;
import com.volmit.iris.core.nms.INMS;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("deprecation")
public class LinkedTerrainChunk implements TerrainChunk {
    private final Biome[] biome2D;
    private final IrisBiomeStorage biome3D;
    private ChunkData rawChunkData;
    private final BiomeGrid storage;

    public LinkedTerrainChunk(int maxHeight) {
        this(null, maxHeight);
    }

    public LinkedTerrainChunk(BiomeGrid storage, ChunkData data) {
        this.storage = storage;
        rawChunkData = data;
        biome2D = storage != null ? null : Iris.biome3d ? null : new Biome[256];
        biome3D = storage != null ? null : Iris.biome3d ? new IrisBiomeStorage() : null;
    }

    public LinkedTerrainChunk(BiomeGrid storage, int maxHeight) {
        this.storage = storage;
        rawChunkData = createChunkData(maxHeight);
        biome2D = storage != null ? null : Iris.biome3d ? null : new Biome[256];
        biome3D = storage != null ? null : Iris.biome3d ? new IrisBiomeStorage() : null;
    }

    private ChunkData createChunkData(int maxHeight) {
        try {
            return Bukkit.createChunkData(new HeightedFakeWorld(maxHeight));
        } catch (Throwable e) {Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public BiomeBaseInjector getBiomeBaseInjector() {
        return (x, y, z, bb) -> INMS.get().forceBiomeInto(x, y, z, bb, storage);
    }

    @NotNull
    @Override
    public Biome getBiome(int x, int z) {
        if (storage != null) {
            return storage.getBiome(x, z);
        }

        if (biome2D != null) {
            return biome2D[(z << 4) | x];
        }

        return biome3D.getBiome(x, 0, z);
    }

    @NotNull
    @Override
    public Biome getBiome(int x, int y, int z) {
        if (storage != null) {
            return storage.getBiome(x, y, z);
        }

        if (biome2D != null) {
            return biome2D[(z << 4) | x];
        }

        return biome3D.getBiome(x, y, z);
    }

    @Override
    public void setBiome(int x, int z, Biome bio) {
        if (storage != null) {
            storage.setBiome(x, z, bio);
            return;
        }

        if (biome2D != null) {
            biome2D[(z << 4) | x] = bio;
            return;
        }

        biome3D.setBiome(x, 0, z, bio);
    }

    public BiomeGrid getRawBiome() {
        return storage;
    }

    @Override
    public void setBiome(int x, int y, int z, Biome bio) {
        if (storage != null) {
            storage.setBiome(x, y, z, bio);
            return;
        }

        if (biome2D != null) {
            biome2D[(z << 4) | x] = bio;
            return;
        }

        biome3D.setBiome(x, y, z, bio);
    }

    @Override
    public int getMinHeight() {
        return rawChunkData.getMinHeight();
    }

    @Override
    public int getMaxHeight() {
        return rawChunkData.getMaxHeight();
    }

    @Override
    public void setBlock(int x, int y, int z, BlockData blockData) {
        rawChunkData.setBlock(x, y, z, blockData);
    }

    @NotNull
    @Override
    public BlockData getBlockData(int x, int y, int z) {
        return rawChunkData.getBlockData(x, y, z);
    }

    @Deprecated
    @Override
    public void setBlock(int x, int y, int z, @NotNull Material material) {
        rawChunkData.setBlock(x, y, z, material);
    }

    @Deprecated
    @Override
    public void setBlock(int x, int y, int z, @NotNull MaterialData material) {
        rawChunkData.setBlock(x, y, z, material);
    }

    @Deprecated
    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull Material material) {
        rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material);
    }

    @Deprecated
    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull MaterialData material) {
        rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material);
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull BlockData blockData) {
        rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, blockData);
    }

    @NotNull
    @Deprecated
    @Override
    public Material getType(int x, int y, int z) {
        return rawChunkData.getType(x, y, z);
    }

    @NotNull
    @Deprecated
    @Override
    public MaterialData getTypeAndData(int x, int y, int z) {
        return rawChunkData.getTypeAndData(x, y, z);
    }

    @Deprecated
    @Override
    public byte getData(int x, int y, int z) {
        return rawChunkData.getData(x, y, z);
    }

    @Override
    public ChunkData getRaw() {
        return rawChunkData;
    }

    @Override
    public void setRaw(ChunkData data) {
        rawChunkData = data;
    }

    @Override
    public void inject(BiomeGrid biome) {
        if (biome2D != null) {
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    biome.setBiome(i, j, getBiome(i, j));
                }
            }
        } else if (biome3D != null) {
            biome3D.inject(biome);
        }
    }
}
