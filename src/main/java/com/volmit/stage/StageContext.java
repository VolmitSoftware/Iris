package com.volmit.stage;

import java.util.random.RandomGenerator;

/** Per-invocation view handed to one stage body. Do not retain it after the body returns. */
public interface StageContext {
    /** This stage's own random stream in this invocation. It is derived from the invocation's seed
     * and this stage's id only, so installing, removing or reordering other modules and stages, or
     * how much they draw, never changes what this stage draws. A block stage has one stream per
     * placement, drawn across the blocks of its pass in block order. Do not retain it.
     */
    RandomGenerator random();

    /** The output the given stage produced in this invocation. Only stages the current stage
     * transitively requires are readable; anything else throws IllegalStateException, as does
     * reading a block stage's accumulator before its block pass has finished.
     */
    <O> O output(Stage<O> stage);
}
