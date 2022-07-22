package com.volmit.iris.engine.feature.features;

import art.arcane.amulet.range.IntegerRange;
import art.arcane.source.NoisePlane;
import art.arcane.source.util.NoisePreset;
import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.feature.*;
import com.volmit.iris.platform.PlatformBlock;
import com.volmit.iris.util.ShortNoiseCache;
import lombok.AllArgsConstructor;
import lombok.Data;

public class FeatureTerrain extends Feature<PlatformBlock, FeatureTerrain.TerrainFeatureState> {
    private final PlatformBlock stone;
    private final NoisePlane generator;

    public FeatureTerrain(Engine engine)
    {
        super("terrain", engine);
        stone = engine.block("stone");
        this.generator = NoisePreset.NATURAL.create(1234).fit(0, 64).scale(0.2);
    }

    @Override
    public TerrainFeatureState prepare(Engine engine, FeatureSizedTarget target, FeatureStorage storage) {
        final ShortNoiseCache noise = storage.getHeightmap();

        for(int x : target.x()) {
            for(int z : target.z()) {
                noise.set(x & storage.getWidth() - 1, z & storage.getHeight() - 1, (short) generator.noise(x, z));
            }
        }

        return new TerrainFeatureState(noise);
    }

    @Override
    public void generate(Engine engine, TerrainFeatureState state, FeatureTarget<PlatformBlock> target, FeatureStorage storage) {
        for(int x : target.x()) {
            for(int z : target.z()) {
                int h = state.getNoise().get(x, z);
                for(int y : new IntegerRange(target.y().getLeftEndpoint(), Math.min(target.y().getRightEndpoint(), h))) {
                    target.getHunk().set(x, y, z, stone);
                }
            }
        }
    }

    @Data
    @AllArgsConstructor
    public static class TerrainFeatureState implements FeatureState {
        private final ShortNoiseCache noise;
    }
}
