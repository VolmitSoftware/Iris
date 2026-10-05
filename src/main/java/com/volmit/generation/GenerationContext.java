package com.volmit.generation;

import com.volmit.mantle.Mantle;
import com.volmit.config.FeatureSettings;
import com.volmit.world.TerrainSampler;

/** Per-invocation state. Must not be retained by a modifier after execution. */
public interface GenerationContext extends InvocationContext {
    /** World block coordinates corresponding to local volume coordinates (0, 0, 0). */
    int originX();
    int originY();
    int originZ();

    TerrainSampler terrain();
    FeatureSettings settings();

    /** Non-null mantle access scoped to this invocation; the host owns its lifetime. */
    Mantle mantle();
}
