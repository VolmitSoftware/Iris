package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.mantle.MantleKey;
import com.volmit.stage.MantleStageContext;
import com.volmit.stage.MantleStageDefinition;
import com.volmit.stage.PackReach;
import com.volmit.stage.Stage;
import com.volmit.stage.StageAction;
import com.volmit.stage.StageRef;

/** Builder behind {@link MantleStageDefinition}. */
final class MantleStageDraft implements MantleStageDefinition {
    private final StageDeclaration declaration;

    MantleStageDraft(StageDeclaration declaration) {
        this.declaration = declaration;
    }

    @Override
    public MantleStageDraft requires(Stage<?>... stages) {
        declaration.require(stages);
        return this;
    }

    @Override
    public MantleStageDraft softAfter(Stage<?>... stages) {
        declaration.softAfter(stages);
        return this;
    }

    @Override
    public MantleStageDraft softAfter(StageRef... stages) {
        declaration.softAfter(stages);
        return this;
    }

    @Override
    public MantleStageDraft softBefore(Stage<?>... stages) {
        declaration.softBefore(stages);
        return this;
    }

    @Override
    public MantleStageDraft softBefore(StageRef... stages) {
        declaration.softBefore(stages);
        return this;
    }

    @Override
    public MantleStageDraft reach(int blocks) {
        declaration.reach(blocks);
        return this;
    }

    @Override
    public MantleStageDraft reach(PackReach reach) {
        declaration.reach(reach);
        return this;
    }

    @Override
    public MantleStageDraft writesMantle(MantleKey<?>... layers) {
        declaration.writesMantle(layers);
        return this;
    }

    @Override
    public MantleStageDraft when(Class<? extends IrisFeature> settingsType) {
        declaration.condition(settingsType);
        return this;
    }

    @Override
    public void run(StageAction<MantleStageContext> action) {
        declaration.run(action);
    }
}
