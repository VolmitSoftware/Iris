package com.volmit.generation;

import com.volmit.IrisFeature;

/** Connects a feature's decoded options to its executable modifier. */
@FunctionalInterface
public interface ModifierFactory<F extends IrisFeature, T> {
    GenerationModifier<T> create(F feature);
}
