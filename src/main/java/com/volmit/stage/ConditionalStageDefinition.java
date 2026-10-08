package com.volmit.stage;

import com.volmit.IrisFeature;
import com.volmit.mantle.MantleKey;

/** An invocation stage active only where its settings are configured; bodies receive every configured entry. */
public interface ConditionalStageDefinition<C extends StageContext, S extends IrisFeature, O> {
    ConditionalStageDefinition<C, S, O> requires(Stage<?>... stages);

    ConditionalStageDefinition<C, S, O> softAfter(StageRef... stages);

    ConditionalStageDefinition<C, S, O> softBefore(StageRef... stages);

    ConditionalStageDefinition<C, S, O> reach(int blocks);

    ConditionalStageDefinition<C, S, O> reach(PackReach reach);

    ConditionalStageDefinition<C, S, O> writesMantle(MantleKey<?>... layers);

    void run(SettingsAction<C, S> action);

    void produce(SettingsFunction<C, S, O> function);
}
