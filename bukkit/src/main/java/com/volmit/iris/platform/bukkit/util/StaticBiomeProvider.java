package com.volmit.iris.platform.bukkit.util;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.Arrays;
import java.util.List;

public class StaticBiomeProvider extends BiomeProvider {
    private static final List<Biome> ALL_BIOMES = Arrays.stream(Biome.values()).without((i) -> i.equals(Biome.CUSTOM)).toList().unmodifiable();
    private final Biome defaultBiome;

    public StaticBiomeProvider(Biome defaultBiome) {
        this.defaultBiome = defaultBiome;
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return defaultBiome;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return ALL_BIOMES;
    }
}
