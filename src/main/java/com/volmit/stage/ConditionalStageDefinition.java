package com.volmit.stage;

/** An invocation stage active only where its settings are configured; bodies receive them. */
public interface ConditionalStageDefinition<C extends StageContext, S, O> {
    ConditionalStageDefinition<C, S, O> requires(Stage<?>... stages);

    ConditionalStageDefinition<C, S, O> softAfter(StageRef... stages);

    ConditionalStageDefinition<C, S, O> softBefore(StageRef... stages);

    void run(SettingsAction<C, S> action);

    void produce(SettingsFunction<C, S, O> function);
}
