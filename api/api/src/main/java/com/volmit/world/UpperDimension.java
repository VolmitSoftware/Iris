package com.volmit.world;

import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import java.util.Optional;

/** The dimension stacked above the one being generated, whose ground hangs from the top of this one's volume. It may be
 * the dimension itself ({@link #isThisDimension()}). Its own biomes and settings decide what its ground is made of, which
 * is why a module that fills it in reads them here and not from the sampler and settings of the dimension it generates.
 * Valid for the invocation, like the sampler it comes from.
 */
public interface UpperDimension {
    /** Whether the dimension above is this very dimension, stacked on itself. */
    boolean isThisDimension();

    /** The id its pack gives the dimension's key, such as {@code overworld:overworld}: what its noise seeds derive from. */
    ResourceId id();

    /** The settings of the upper dimension: its dimension, and the region and surface biome it has at the column. */
    FeatureSettings settings();

    /** The resources the upper dimension reads: its blocks and its noise. */
    PackResources resources();

    /** The block an image map of the upper dimension gives its ground at the column; empty where it gives none. */
    Optional<BlockState> surfaceBlock(int worldX, int worldZ);
}
