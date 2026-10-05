package com.volmit.stage;

@FunctionalInterface
public interface SettingsAction<C, S> {
    void run(C context, S settings);
}
