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

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.BiomeBaseInjector;
import com.volmit.iris.util.nbt.mca.Chunk;
import com.volmit.iris.util.nbt.mca.NBTWorld;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;

@Builder
@AllArgsConstructor
public class MCATerrainChunk implements TerrainChunk {
    private final NBTWorld writer;
    private final BiomeBaseInjector injector;
    private final int ox;
    private final int oz;
    private final int minHeight;
    private final int maxHeight;
    private final Chunk mcaChunk;

    @Override
    public BiomeBaseInjector getBiomeBaseInjector() {
        return injector;
    }

    @Override
    public Biome getBiome(int x, int z) {
        return Biome.THE_VOID;
    }

    @Override
    public Biome getBiome(int x, int y, int z) {
        return Biome.THE_VOID;
    }

    @Override
    public void setBiome(int x, int z, Biome bio) {
        setBiome(ox + x, 0, oz + z, bio);
    }

    @Override
    public void setBiome(int x, int y, int z, Biome bio) {
        mcaChunk.setBiomeAt((ox + x) & 15, y, (oz + z) & 15, writer.getBiomeId(bio));
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
    public void setBlock(int x, int y, int z, BlockData blockData) {
        int xx = (x + ox) & 15;
        int zz = (z + oz) & 15;

        if (y > getMaxHeight() || y < getMinHeight()) {
            return;
        }

        if (blockData == null) {
            Iris.error("NULL BD");
        }

        mcaChunk.setBlockStateAt(xx, y, zz, NBTWorld.getCompound(blockData), false);
    }

    @Override
    public org.bukkit.block.data.BlockData getBlockData(int x, int y, int z) {
        if (y > getMaxHeight()) {
            y = getMaxHeight();
        }

        if (y < getMinHeight()) {
            y = getMinHeight();
        }

        return NBTWorld.getBlockData(mcaChunk.getBlockStateAt((x + ox) & 15, y, (z + oz) & 15));
    }

    @Override
    public ChunkGenerator.ChunkData getRaw() {
        return null;
    }

    @Override
    public void setRaw(ChunkGenerator.ChunkData data) {

    }

    @Override
    public void inject(ChunkGenerator.BiomeGrid biome) {

    }

    @Override
    public void setBlock(int x, int y, int z, Material material) {

    }

    @Override
    public void setBlock(int x, int y, int z, MaterialData material) {

    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material) {

    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material) {

    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData) {

    }


    @Override
    public Material getType(int x, int y, int z) {
        return null;
    }


    @Override
    public MaterialData getTypeAndData(int x, int y, int z) {
        return null;
    }

    @Override
    public byte getData(int x, int y, int z) {
        return 0;
    }
}
