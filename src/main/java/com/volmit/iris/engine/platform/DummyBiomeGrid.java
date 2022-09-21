package com.volmit.iris.engine.platform;

import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

public class DummyBiomeGrid implements ChunkGenerator.BiomeGrid {
    @NotNull
    @Override
    public Biome getBiome(int x, int z) {
        return null;
    }

    @NotNull
    @Override
    public Biome getBiome(int x, int y, int z) {
        return null;
    }

    @Override
    public void setBiome(int x, int z, @NotNull Biome bio) {

    }

    @Override
    public void setBiome(int x, int y, int z, @NotNull Biome bio) {

    }
}
