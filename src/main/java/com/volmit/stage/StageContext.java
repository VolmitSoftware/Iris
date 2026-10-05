package com.volmit.stage;

/** Per-invocation view handed to one stage body. Do not retain it after the body returns. */
public interface StageContext {
    /** The output the given stage produced in this invocation. Only stages the current stage
     * transitively requires are readable; anything else throws IllegalStateException, as does
     * reading a block stage's accumulator before its block pass has finished.
     */
    <O> O output(Stage<O> stage);
}
