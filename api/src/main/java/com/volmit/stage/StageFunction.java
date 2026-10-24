package com.volmit.stage;

@FunctionalInterface
public interface StageFunction<C, O> {
    O run(C context);
}
