package com.volmit.iris.engine.feature.features;

import art.arcane.source.NoisePlane;
import art.arcane.source.interpolator.CubicInterpolator;
import art.arcane.source.interpolator.Interpolator;
import art.arcane.source.interpolator.LinearInterpolator;
import art.arcane.source.interpolator.StarcastInterpolator;
import art.arcane.source.noise.provider.MirroredCacheProvider;
import art.arcane.source.util.NoisePreset;
import art.arcane.spatial.hunk.Hunk;
import art.arcane.spatial.hunk.storage.ArrayHunk;
import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.feature.*;
import com.volmit.iris.platform.block.PlatformBlock;
import lombok.AllArgsConstructor;
import lombok.Data;

public class FeatureTerrain extends Feature<PlatformBlock, FeatureTerrain.State> {
    private final PlatformBlock stone;
    private final NoisePlane generator;
    private final NoisePlane generator2;

    public FeatureTerrain(Engine engine) {
        super("terrain", engine);
        setOptimize(true);
        stone = engine.block("stone");
        this.generator = NoisePreset.NATURAL.create(1234);
        this.generator2 = NoisePreset.NATURAL.create(6664).fit(0, 1).scale(0.1);
    }

    @Override
    public State prepare(Engine engine, FeatureSizedTarget target, FeatureStorage storage) {
        Hunk<Double> snc = new ArrayHunk<>(target.getWidth(), target.getHeight(), target.getDepth());
        short n;
        int fdx,fdz;
        for(int x : target.x()) {
            fdx = Math.floorMod(x, target.getWidth());
            for(int z : target.z()) {
                fdz = Math.floorMod(z, target.getDepth());
                n = (short) generator.noise(x, z);
                for(int y = 0; y < n; y++) {
                    if(generator2.noise(x,y,z) > 0.5) {
                        snc.set(fdx, y, fdz, 1D);
                    }
                }
            }
        }

        return new State(snc);
    }

    @Override
    public void generate(Engine engine, State state, FeatureTarget<PlatformBlock> target, FeatureStorage storage) {
        int y;
        for(int x : target.localX()) {
            for(int z : target.localZ()) {
                for(int i = 0; i < target.getHeight(); i++) {
                    Double v = state.getNoise().get(x, i, z);
                    v = v == null ? 0f : v;
                    if(v >= 0.5) {
                        target.getHunk().set(x,i, z, stone);
                    }
                }
            }
        }
    }

    @Data
    @AllArgsConstructor
    public static class State implements FeatureState {
        private final Hunk<Double> noise;
    }
}
