package com.volmit.stage;

import java.util.function.Supplier;

/** A block stage active only where its settings are configured; bodies receive them. */
public interface ConditionalBlockStageDefinition<S, O> {
    ConditionalBlockStageDefinition<S, O> requires(Stage<?>... stages);

    ConditionalBlockStageDefinition<S, O> softAfter(StageRef... stages);

    ConditionalBlockStageDefinition<S, O> softBefore(StageRef... stages);

    void run(SettingsAction<BlockStageContext, S> action);

    void accumulate(Supplier<O> initial, SettingsBlockCollector<S, O> collector);
}
