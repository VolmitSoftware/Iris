package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.ChunkStageDefinition;
import com.volmit.stage.Stage;
import com.volmit.stage.StageAction;
import com.volmit.stage.StageFunction;
import com.volmit.stage.StageRef;

/** Builder behind {@link ChunkStageDefinition}. */
final class ChunkStageDraft<O> implements ChunkStageDefinition<O> {
    private final StageDeclaration declaration;

    ChunkStageDraft(StageDeclaration declaration) {
        this.declaration = declaration;
    }

    @Override
    public ChunkStageDraft<O> requires(Stage<?>... stages) {
        declaration.require(stages);
        return this;
    }

    @Override
    public ChunkStageDraft<O> softAfter(Stage<?>... stages) {
        declaration.softAfter(stages);
        return this;
    }

    @Override
    public ChunkStageDraft<O> softAfter(StageRef... stages) {
        declaration.softAfter(stages);
        return this;
    }

    @Override
    public ChunkStageDraft<O> softBefore(Stage<?>... stages) {
        declaration.softBefore(stages);
        return this;
    }

    @Override
    public ChunkStageDraft<O> softBefore(StageRef... stages) {
        declaration.softBefore(stages);
        return this;
    }

    @Override
    public ChunkStageDraft<O> when(Class<? extends IrisFeature> settingsType) {
        declaration.condition(settingsType);
        return this;
    }

    @Override
    public void run(StageAction<ChunkStageContext> action) {
        declaration.run(action);
    }

    @Override
    public void produce(StageFunction<ChunkStageContext, O> function) {
        declaration.produce(function);
    }
}
