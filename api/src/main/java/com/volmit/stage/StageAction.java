package com.volmit.stage;

@FunctionalInterface
public interface StageAction<C> {
    void run(C context);
}
