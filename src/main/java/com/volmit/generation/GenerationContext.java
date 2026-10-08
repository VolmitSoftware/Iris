package com.volmit.generation;

import com.volmit.hunk.Hunk;
import com.volmit.mantle.Mantle;
import com.volmit.config.FeatureSettings;
import com.volmit.world.TerrainSampler;

/** Per-invocation chunk generation inputs. Must not be retained after execution. */
public interface GenerationContext extends InvocationContext {
    /** World block coordinates corresponding to local volume coordinates (0, 0, 0). */
    int originX();
    int originY();
    int originZ();

    TerrainSampler terrain();
    FeatureSettings settings();

    /** Non-null mantle access scoped to this invocation; the host owns its lifetime. Chunk stages read it
     * only: the mantle pipeline has finished around the chunk, and writes throw.
     */
    Mantle mantle();

    /** The host-owned local volume of the given value type, shared by all stages of this
     * invocation. Unsupported types throw IllegalArgumentException.
     */
    <T> Hunk<T> hunk(Class<T> type);
}
