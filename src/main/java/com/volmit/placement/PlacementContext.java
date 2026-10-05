package com.volmit.placement;

import com.volmit.config.FeatureSettings;
import com.volmit.generation.InvocationContext;
import com.volmit.object.ObjectTransformer;
import com.volmit.world.TerrainSampler;
import com.volmit.world.BlockView;

/** Host-supplied, read-only inputs of one placement attempt. Working state is carried by stage
 * outputs, never stored on a module or stage body. Context lifetime ends with the placement.
 */
public interface PlacementContext extends InvocationContext {
    TerrainSampler terrain();
    FeatureSettings settings();
    ObjectTransformer objects();
    /** Prepared blocks before this placement. Use stage access for reads of buffered changes. */
    BlockView blocks();
}
