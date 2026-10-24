package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.mantle.MantleKey;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.PlacementStageDefinition;
import com.volmit.stage.Stage;
import com.volmit.stage.StageAction;
import com.volmit.stage.StageFunction;
import com.volmit.stage.StageRef;

/** Builder behind {@link PlacementStageDefinition}. */
final class PlacementStageDraft<O> implements PlacementStageDefinition<O> {
    private final StageDeclaration declaration;

    PlacementStageDraft(StageDeclaration declaration) {
        this.declaration = declaration;
    }

    @Override
    public PlacementStageDraft<O> requires(Stage<?>... stages) {
        declaration.require(stages);
        return this;
    }

    @Override
    public PlacementStageDraft<O> softAfter(Stage<?>... stages) {
        declaration.softAfter(stages);
        return this;
    }

    @Override
    public PlacementStageDraft<O> softAfter(StageRef... stages) {
        declaration.softAfter(stages);
        return this;
    }

    @Override
    public PlacementStageDraft<O> softBefore(Stage<?>... stages) {
        declaration.softBefore(stages);
        return this;
    }

    @Override
    public PlacementStageDraft<O> softBefore(StageRef... stages) {
        declaration.softBefore(stages);
        return this;
    }

    @Override
    public PlacementStageDraft<O> reach(int blocks) {
        declaration.reach(blocks);
        return this;
    }

    @Override
    public PlacementStageDraft<O> writesMantle(MantleKey<?>... layers) {
        declaration.writesMantle(layers);
        return this;
    }

    @Override
    public PlacementStageDraft<O> when(Class<? extends IrisFeature> settingsType) {
        declaration.condition(settingsType);
        return this;
    }

    @Override
    public void run(StageAction<PlacementStageContext> action) {
        declaration.run(action);
    }

    @Override
    public void produce(StageFunction<PlacementStageContext, O> function) {
        declaration.produce(function);
    }
}
