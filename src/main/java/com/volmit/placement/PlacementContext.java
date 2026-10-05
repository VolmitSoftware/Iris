package com.volmit.placement;

import com.volmit.config.FeatureSettings;
import com.volmit.generation.InvocationContext;
import com.volmit.object.ObjectTransformer;
import com.volmit.world.TerrainSampler;
import com.volmit.world.BlockView;

/** Read-only inputs to placement hooks. Working state is carried by returned immutable requests
 * and blocks, never stored on a configured modifier. Context lifetime ends with placement.
 */
public interface PlacementContext extends InvocationContext {
    TerrainSampler terrain();
    FeatureSettings settings();
    ObjectTransformer objects();
    /** Prepared blocks before this placement. Use hook access for reads of buffered changes. */
    BlockView blocks();
}
