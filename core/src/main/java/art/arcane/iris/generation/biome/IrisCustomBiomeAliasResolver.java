package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.terrain.IrisDimension;

import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@FunctionalInterface
public interface IrisCustomBiomeAliasResolver {
    Collection<String> aliases(
            IrisDimension dimension,
            IrisBiome biome,
            IrisBiomeCustom customBiome,
            String physicalResourceKey
    );

    default String physicalResourceKey(
            IrisDimension dimension,
            IrisBiome biome,
            IrisBiomeCustom customBiome,
            String currentPhysicalResourceKey
    ) throws IOException {
        return currentPhysicalResourceKey;
    }

    default String generatedSource(
            String registryKey,
            String resourceKey,
            String currentSource,
            IDataFixer fixer
    ) throws IOException {
        return currentSource;
    }

    static IrisCustomBiomeAliasResolver none() {
        return (dimension, biome, customBiome, physicalResourceKey) -> List.of();
    }
}
