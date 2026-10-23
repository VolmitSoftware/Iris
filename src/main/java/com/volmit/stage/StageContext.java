package com.volmit.stage;

import com.volmit.IrisFeature;
import com.volmit.config.SettingsStack;
import com.volmit.random.WorldRandom;

/** Per-invocation view handed to one stage body. Do not retain it after the body returns. */
public interface StageContext {
    /** This stage's own random stream in this invocation. It is derived from the invocation's seed
     * and this stage's id only, so installing, removing or reordering other modules and stages, or
     * how much they draw, never changes what this stage draws. A block stage has one stream per
     * placement, drawn across the blocks of its pass in block order. Do not retain it.
     */
    WorldRandom random();

    /** The output the given stage produced in this invocation. Only stages the current stage
     * transitively requires are readable; anything else throws IllegalStateException, as does
     * reading a block stage's accumulator before its block pass has finished.
     */
    <O> O output(Stage<O> stage);

    /** The stack of the settings this stage is conditioned on with {@code when(settingsType)}, in level order: the
     * ones that activated it in this invocation, so never empty. A placement has its own declarations; a chunk or
     * mantle invocation has every distinct entry of the chunk's columns. Any other type, or a stage without a
     * condition, throws IllegalStateException; read other settings through the context's {@code settings()}.
     */
    <S extends IrisFeature> SettingsStack<S> configured(Class<S> settingsType);
}
