package com.volmit.stage;

@FunctionalInterface
public interface SettingsBlockCollector<S, O> {
    void collect(BlockStageContext context, S settings, O accumulator);
}
