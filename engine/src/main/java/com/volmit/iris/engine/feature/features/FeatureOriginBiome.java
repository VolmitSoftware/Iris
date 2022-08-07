package com.volmit.iris.engine.feature.features;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.dimension.IrisBiome;
import com.volmit.iris.engine.feature.*;
import com.volmit.iris.platform.PlatformBiome;
import com.volmit.iris.util.NoiseCache;
import lombok.AllArgsConstructor;
import lombok.Data;

public class FeatureOriginBiome extends Feature<PlatformBiome, FeatureOriginBiome.State> {
    public FeatureOriginBiome(Engine engine) {
        super("biome-origin", engine);
    }

    @Override
    public State prepare(Engine engine, FeatureSizedTarget target, FeatureStorage storage) {
        final NoiseCache<IrisBiome> noise = storage.getBiome();

        for(int x : target.x()) {
            for(int z : target.z()) {
                noise.set(x & storage.getW() - 1, z & storage.getH() - 1, null);
            }
        }

        return new State(noise);
    }

    @Override
    public void generate(Engine engine, State state, FeatureTarget<PlatformBiome> target, FeatureStorage storage) {
        for(int x : target.x()) {
            for(int z : target.z()) {
                IrisBiome b = state.getBiomes().get(x, z);
                target.getHunk().set(x, 0, z, b.toPlatformBiome());
            }
        }
    }

    @Data
    @AllArgsConstructor
    public static class State implements FeatureState {
        private final NoiseCache<IrisBiome> biomes;
    }
}
