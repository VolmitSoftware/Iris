package com.volmit.stage;

import com.volmit.IrisFeature;
import com.volmit.mantle.MantleKey;

import java.util.function.Supplier;

/** A block stage active only where its settings are configured; bodies receive every configured entry. */
public interface ConditionalBlockStageDefinition<S extends IrisFeature, O> {
    ConditionalBlockStageDefinition<S, O> requires(Stage<?>... stages);

    ConditionalBlockStageDefinition<S, O> softAfter(StageRef... stages);

    ConditionalBlockStageDefinition<S, O> softBefore(StageRef... stages);

    ConditionalBlockStageDefinition<S, O> reach(int blocks);

    ConditionalBlockStageDefinition<S, O> writesMantle(MantleKey<?>... layers);

    void run(SettingsAction<BlockStageContext, S> action);

    void accumulate(Supplier<O> initial, SettingsBlockCollector<S, O> collector);
}
