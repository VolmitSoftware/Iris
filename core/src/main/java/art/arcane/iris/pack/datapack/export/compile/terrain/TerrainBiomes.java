package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.loading.IrisData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TerrainBiomes {
    private static final String DEFAULT_GENERATOR = "default";

    private TerrainBiomes() {
    }

    static Map<String, IrisBiome> resolve(BiomeCodes codes, IrisData data) {
        Map<String, IrisBiome> biomes = new LinkedHashMap<>();
        for (String id : BandTreeCompiler.surfaceBiomeIds(codes)) {
            String loadKey = codes.loadKeyByBiomeId().get(id);
            if (loadKey == null) {
                continue;
            }
            IrisBiome biome = data.getBiomeLoader().load(loadKey, false);
            if (biome == null) {
                throw new IllegalStateException("Biome '" + loadKey + "' for exported biome '" + id + "' is missing from the pack");
            }
            biomes.put(id, biome);
        }
        return Collections.unmodifiableMap(biomes);
    }

    static IrisGenerator generator(IrisBiomeGeneratorLink link, IrisData data) {
        String key = generatorKey(link);
        IrisGenerator generator = data.getGeneratorLoader().load(key, false);
        if (generator == null) {
            generator = new IrisGenerator();
        }
        if (generator.getLoadKey() == null || generator.getLoadKey().isBlank()) {
            generator.setLoadKey(key);
        }
        return generator;
    }

    static List<IrisBiomeGeneratorLink> links(IrisBiome biome) {
        List<IrisBiomeGeneratorLink> links = biome.getGenerators();
        return links == null ? List.of() : links;
    }

    private static String generatorKey(IrisBiomeGeneratorLink link) {
        String key = link.getGenerator();
        return key == null || key.isBlank() ? DEFAULT_GENERATOR : key;
    }
}
