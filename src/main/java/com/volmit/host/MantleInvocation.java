package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import com.volmit.config.SettingsStack;
import com.volmit.generation.MantleGenerationContext;
import com.volmit.mantle.Mantle;
import com.volmit.random.WorldRandom;
import com.volmit.world.PackWorld;
import com.volmit.stage.MantleStageContext;
import com.volmit.stage.Stage;
import com.volmit.world.TerrainSampler;
import java.util.Objects;

/** The context every stage of one mantle invocation receives. */
final class MantleInvocation implements MantleStageContext {
    private final StageInvocation stages;
    private final MantleGenerationContext inputs;

    MantleInvocation(StageInvocation stages, MantleGenerationContext inputs) {
        this.stages = stages;
        this.inputs = Objects.requireNonNull(inputs, "inputs");
    }

    StageInvocation stages() {
        return stages;
    }

    @Override
    public <O> O output(Stage<O> stage) {
        return stages.output(stage);
    }

    @Override
    public <S extends IrisFeature> SettingsStack<S> configured(Class<S> settingsType) {
        return stages.configured(settingsType);
    }

    @Override
    public int originX() {
        return inputs.originX();
    }

    @Override
    public int originZ() {
        return inputs.originZ();
    }

    @Override
    public TerrainSampler terrain() {
        return inputs.terrain();
    }

    @Override
    public FeatureSettings settings() {
        return inputs.settings();
    }

    @Override
    public Mantle mantle() {
        return inputs.mantle();
    }

    @Override
    public PackWorld world() {
        return stages.world(inputs.world());
    }

    @Override
    public WorldRandom random() {
        return stages.random();
    }
}
