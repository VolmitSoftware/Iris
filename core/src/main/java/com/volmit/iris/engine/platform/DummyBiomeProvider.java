package com.volmit.iris.engine.platform;

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.util.collection.KList;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DummyBiomeProvider extends BiomeProvider {
    private final List<Biome> ALL = INMS.get().getBiomes();

    @NotNull
    @Override
    public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        return Biome.PLAINS;
    }

    @NotNull
    @Override
    public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return ALL;
    }
}
