package com.volmit.tooling;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/**
 * What a registered jigsaw structure says about itself.
 *
 * @param startPool   the template pool the structure starts from
 * @param maxDepth    how many pieces deep the structure grows
 * @param maxDistance how far from its centre the structure may reach, in blocks; the horizontal reach when the platform
 *                    keeps a horizontal and a vertical one
 * @param gameVersion the platform's own version string, which tools record as where the structure came from
 * @param shuffleSeed what the platform seeds its shuffle of the connectors of this structure's pool elements with, so
 *                    that reading the same structure twice lists them in the same order
 */
public record JigsawStructureInfo(ResourceId startPool, int maxDepth, int maxDistance, String gameVersion,
                                  long shuffleSeed) {
    public JigsawStructureInfo {
        Objects.requireNonNull(startPool, "startPool");
        Objects.requireNonNull(gameVersion, "gameVersion");
    }
}
