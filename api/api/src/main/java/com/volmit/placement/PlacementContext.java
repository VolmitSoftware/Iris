package com.volmit.placement;

import com.volmit.config.FeatureSettings;
import com.volmit.generation.InvocationContext;
import com.volmit.world.TerrainSampler;

import java.util.Optional;

/** Host-supplied, read-only inputs of one placement attempt. Working state is carried by stage
 * outputs, never stored on a module or stage body. Context lifetime ends with the placement.
 */
public interface PlacementContext extends InvocationContext {
    TerrainSampler terrain();
    FeatureSettings settings();
    /**
     * The world the placement is made in, for the questions about a column the stages ask. Its blocks are the ones before
     * this placement; stages write through their access, never into it.
     */
    PlacementTerrain placementTerrain();
    /**
     * What the module that asked for this placement handed its stages ({@link PlacementRun#input}), when it is a
     * {@code type}; empty otherwise, such as for a placement the host itself started.
     */
    <T> Optional<T> input(Class<T> type);
}
