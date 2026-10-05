package com.volmit.stage;

@FunctionalInterface
public interface SettingsFunction<C, S, O> {
    O run(C context, S settings);
}
