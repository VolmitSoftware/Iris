package com.volmit.world;

import java.util.Objects;
import java.util.OptionalInt;

/** Pre-modification terrain sample in world coordinates.
 * surfaceY is the top solid surface height, not the first air block. fluidY is the top fluid
 * block when present. Slope is nonnegative height change per horizontal block.
 * This is the host's terrain field, not a scan of current edited blocks.
 */
public record TerrainSample(double surfaceY, OptionalInt fluidY, double slope) {
    public TerrainSample {
        Objects.requireNonNull(fluidY, "fluidY");
        if (!Double.isFinite(surfaceY) || !Double.isFinite(slope) || slope < 0) {
            throw new IllegalArgumentException("Invalid terrain sample");
        }
    }
}
