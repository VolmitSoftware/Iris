package com.volmit.iris.engine.feature.features;

import art.arcane.source.NoisePlane;
import art.arcane.source.util.NoisePreset;
import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.feature.*;
import com.volmit.iris.platform.PlatformBlock;
import com.volmit.iris.util.ShortNoiseCache;
import lombok.AllArgsConstructor;
import lombok.Data;

public class FeatureTerrain extends Feature<PlatformBlock, FeatureTerrain.State> {
    private final PlatformBlock stone;
    private final NoisePlane generator;

    public FeatureTerrain(Engine engine) {
        super("terrain", engine);
        stone = engine.block("stone");
        this.generator = NoisePreset.NATURAL.create(1234).fit(0, 64).scale(0.2);
    }

    @Override
    public State prepare(Engine engine, FeatureSizedTarget target, FeatureStorage storage) {
        target.forXZ((x, z) -> storage.getHeight().set(x & storage.getW() - 1, z & storage.getH() - 1, (short) generator.noise(x, z)));
        return new State(storage.getHeight());
    }

    @Override
    public void generate(Engine engine, State state, FeatureTarget<PlatformBlock> target, FeatureStorage storage) {
        target.forXZ((x, z) -> target.forYCap((y -> {
            target.getHunk().set(x, y, z, stone);
        }), state.getNoise().get(x, z)));
    }

    @Data
    @AllArgsConstructor
    public static class State implements FeatureState {
        private final ShortNoiseCache noise;
    }
}
