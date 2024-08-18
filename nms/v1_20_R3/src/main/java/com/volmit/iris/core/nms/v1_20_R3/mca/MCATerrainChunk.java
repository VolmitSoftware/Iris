/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.nms.v1_20_R3.mca;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.BiomeBaseInjector;
import com.volmit.iris.engine.data.chunk.TerrainChunk;
import com.volmit.iris.util.data.IrisCustomData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_20_R3.block.CraftBiome;
import org.bukkit.craftbukkit.v1_20_R3.block.data.CraftBlockData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.material.MaterialData;
import org.jetbrains.annotations.NotNull;

public record MCATerrainChunk(ChunkAccess chunk) implements TerrainChunk {

    @Override
    public BiomeBaseInjector getBiomeBaseInjector() {
        return null;
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
        setBiome(x, 0, z, bio);
    }

    @Override
    public void setBiome(int x, int y, int z, Biome bio) {
        if (y > getMaxHeight()) return;
        chunk.setBiome(x & 15, y, z & 15, CraftBiome.bukkitToMinecraftHolder(bio));
    }

    private LevelHeightAccessor height() {
        return chunk;
    }

    @Override
    public int getMinHeight() {
        return height().getMinBuildHeight();
    }

    @Override
    public int getMaxHeight() {
        return height().getMaxBuildHeight();
    }

    @Override
    public void setBlock(int x, int y, int z, BlockData blockData) {
        if (blockData == null) {
            Iris.error("NULL BD");
        }
        if (blockData instanceof IrisCustomData data)
            blockData = data.getBase();
        if (!(blockData instanceof CraftBlockData craftBlockData))
            throw new IllegalArgumentException("Expected CraftBlockData, got " + blockData.getClass().getSimpleName() + " instead");
        chunk.setBlockState(new BlockPos(x & 15, y, z & 15), craftBlockData.getState(), false);
    }

    private BlockState getBlockState(int x, int y, int z) {
        y += getMinHeight();
        if (y > getMaxHeight()) {
            y = getMaxHeight();
        }

        return chunk.getBlockState(new BlockPos(x & 15, y, z & 15));
    }

    @NotNull
    @Override
    public org.bukkit.block.data.BlockData getBlockData(int x, int y, int z) {
        return CraftBlockData.fromData(getBlockState(x, y, z));
    }

    @Override
    public ChunkGenerator.ChunkData getRaw() {
        return null;
    }

    @Override
    public void setRaw(ChunkGenerator.ChunkData data) {

    }

    @Override
    @Deprecated
    public void inject(ChunkGenerator.BiomeGrid biome) {

    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull Material material) {

    }

    @Override
    @Deprecated
    public void setBlock(int x, int y, int z, @NotNull MaterialData material) {

    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull Material material) {

    }

    @Override
    @Deprecated
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull MaterialData material) {

    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull BlockData blockData) {

    }


    @NotNull
    @Override
    public Material getType(int x, int y, int z) {
        return getBlockData(x, y, z).getMaterial();
    }

    @NotNull
    @Override
    public MaterialData getTypeAndData(int x, int y, int z) {
        return getBlockData(x, y, z).createBlockState().getData();
    }

    @Override
    public byte getData(int x, int y, int z) {
        return getTypeAndData(x, y, z).getData();
    }
}
