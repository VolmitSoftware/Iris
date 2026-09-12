package art.arcane.iris.platform.generation;

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DummyBiomeProvider extends BiomeProvider {
    private final List<Biome> ALL = resolveAll();

    private static List<Biome> resolveAll() {
        List<PlatformBiome> platformBiomes = IrisPlatforms.get().biomeWriter().allBiomes();
        List<Biome> biomes = new ArrayList<>(platformBiomes.size());
        for (PlatformBiome biome : platformBiomes) {
            biomes.add((Biome) biome.nativeHandle());
        }
        return biomes;
    }

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
