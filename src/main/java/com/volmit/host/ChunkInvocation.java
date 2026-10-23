package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import com.volmit.config.SettingsStack;
import com.volmit.generation.GenerationContext;
import com.volmit.hunk.Hunk;
import com.volmit.mantle.Mantle;
import com.volmit.random.WorldRandom;
import com.volmit.world.PackWorld;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.Stage;
import com.volmit.world.TerrainSampler;
import java.util.Objects;

/** The context every stage of one chunk invocation receives. The mantle is read-only. */
final class ChunkInvocation implements ChunkStageContext {
    private final StageInvocation stages;
    private final GenerationContext inputs;

    ChunkInvocation(StageInvocation stages, GenerationContext inputs) {
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
        return new ReadOnlyMantle(inputs.mantle());
    }

    @Override
    public <T> Hunk<T> hunk(Class<T> type) {
        return inputs.hunk(type);
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
