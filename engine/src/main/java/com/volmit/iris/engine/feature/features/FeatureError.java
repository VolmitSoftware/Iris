package com.volmit.iris.engine.feature.features;

import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.feature.Feature;
import com.volmit.iris.engine.feature.FeatureSizedTarget;
import com.volmit.iris.engine.feature.FeatureState;
import com.volmit.iris.engine.feature.FeatureStorage;
import com.volmit.iris.engine.feature.FeatureTarget;
import com.volmit.iris.platform.block.PlatformBlock;
import lombok.AllArgsConstructor;
import lombok.Data;

public class FeatureError extends Feature<PlatformBlock, FeatureError.State> {
    private static final State DEFAULT_STATE = new State();
    private final PlatformBlock ERROR_BLOCK;

    public FeatureError(Engine engine) {
        super("error", engine);
        setOptimize(false);
        ERROR_BLOCK = engine.block("red_sandstone");
    }

    @Override
    public State prepare(Engine engine, FeatureSizedTarget target, FeatureStorage storage) {
        return DEFAULT_STATE;
    }

    @Override
    public void generate(Engine engine, State state, FeatureTarget<PlatformBlock> target, FeatureStorage storage) {
        target.forXZ((x, z) -> target.forYCap((y -> target.getHunk().set(x, y, z, ERROR_BLOCK)), 1));
    }

    @Data
    @AllArgsConstructor
    public static class State implements FeatureState {

    }
}
