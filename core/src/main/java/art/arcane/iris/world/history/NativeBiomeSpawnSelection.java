package art.arcane.iris.world.history;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.runtime.Engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record NativeBiomeSpawnSelection(Mode mode, String derivativeKey) {
    private static final NativeBiomeSpawnSelection CURRENT = new NativeBiomeSpawnSelection(Mode.CURRENT, "");
    private static final NativeBiomeSpawnSelection NONE = new NativeBiomeSpawnSelection(Mode.NONE, "");
    private static final NativeBiomeSpawnSelection LOADING = new NativeBiomeSpawnSelection(Mode.LOADING, "");

    public NativeBiomeSpawnSelection {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(derivativeKey, "derivativeKey");
        if (mode == Mode.RETAINED) {
            derivativeKey = ChunkGenerationSemantics.requireResourceKey(derivativeKey);
        } else if (!derivativeKey.isEmpty()) {
            throw new IllegalArgumentException("Only retained native spawn selections have a derivative key");
        }
    }

    public static NativeBiomeSpawnSelection at(Engine engine, int blockX, int worldY, int blockZ, String physicalBiomeKey) {
        if (!(engine instanceof IrisEngine irisEngine) || irisEngine.hasGenerationRuntimeScope()) {
            return CURRENT;
        }
        GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter().orElse(null);
        if (router == null) {
            return CURRENT;
        }
        try {
            return router.biomes().nativeSpawnSelection(blockX, worldY, blockZ, physicalBiomeKey);
        } catch (SavedBiomeUnavailableException unavailable) {
            return unavailable.isLoading() ? LOADING : NONE;
        }
    }

    static Map<String, String> retainedDerivatives(IrisData data) {
        Map<String, String> derivatives = new LinkedHashMap<>();
        for (String key : data.getDimensionLoader().getPossibleKeys()) {
            IrisDimension dimension = data.getDimensionLoader().load(key);
            if (dimension == null) {
                continue;
            }
            for (IrisBiome biome : dimension.getReachableBiomes(() -> data)) {
                if (biome == null || !biome.isCustom()) {
                    continue;
                }
                for (IrisBiomeCustom custom : biome.getCustomDerivitives()) {
                    derivatives.putIfAbsent(data.customBiomeResourceKey(dimension, custom), biome.getVanillaDerivativeKey());
                }
            }
        }
        return Map.copyOf(derivatives);
    }

    public enum Mode {
        CURRENT,
        RETAINED,
        NONE,
        LOADING
    }
}
