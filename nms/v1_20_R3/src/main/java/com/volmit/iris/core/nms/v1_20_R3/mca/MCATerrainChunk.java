package com.volmit.iris.core.nms.v1_20_R3.mca;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.BiomeBaseInjector;
import com.volmit.iris.engine.data.chunk.TerrainChunk;
import com.volmit.iris.util.data.IrisBlockData;
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
        return (x, y, z, biomeBase) -> chunk.setBiome(x, y, z, (Holder<net.minecraft.world.level.biome.Biome>) biomeBase);
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
        if (y > getMaxHeight() || y < getMinHeight()) {
            return;
        }

        if (blockData == null) {
            Iris.error("NULL BD");
        }
        if (blockData instanceof IrisBlockData data)
            blockData = data.getBase();
        if (!(blockData instanceof CraftBlockData craftBlockData))
            throw new IllegalArgumentException("Expected CraftBlockData, got " + blockData.getClass().getSimpleName() + " instead");
        chunk.setBlockState(new BlockPos(x & 15, y, z & 15), craftBlockData.getState(), false);
    }

    private BlockState getBlockState(int x, int y, int z) {
        if (y > getMaxHeight()) {
            y = getMaxHeight();
        }

        if (y < getMinHeight()) {
            y = getMinHeight();
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
