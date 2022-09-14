package com.volmit.iris.engine.platform;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DummyBiomeProvider extends BiomeProvider {
    private final List<Biome> ALL = new KList<>(Biome.values()).qdel(Biome.CUSTOM);

    @NotNull
    @Override
    public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        if(x == 10000 && z == 10000) {
            try
            {
                Iris.error("Im biome provider, who am i?");
                Iris.error(getClass().getCanonicalName());
                throw new RuntimeException("WHATS GOING ON");
            }

            catch(Throwable e)
            {
                e.printStackTrace();
            }
        }

        return Biome.PLAINS;
    }

    @NotNull
    @Override
    public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return ALL;
    }
}
