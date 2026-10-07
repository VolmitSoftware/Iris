package art.arcane.iris.generation.hydrology.cave;

import java.util.Objects;

public record HydrologyCavePlannerSettings(
        int maxHorizontalRadius,
        int maxDepth,
        int maxFloodVolume,
        int grottoHorizontalRadius,
        int grottoVerticalRadius,
        int dryHeadroom,
        HydrologyCaveFluidPolicy existingFluidPolicy,
        int maxClosedComponentHorizontalRadius,
        int maxClosedComponentDepth
) {
    public static final int MAXIMUM_PLANNED_MUTATIONS = 262_144;

    public HydrologyCavePlannerSettings {
        Objects.requireNonNull(existingFluidPolicy);
        if (maxHorizontalRadius < 1) {
            throw new IllegalArgumentException("maxHorizontalRadius must be positive");
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxFloodVolume < 1) {
            throw new IllegalArgumentException("maxFloodVolume must be positive");
        }
        if (grottoHorizontalRadius < 1) {
            throw new IllegalArgumentException("grottoHorizontalRadius must be positive");
        }
        if (grottoVerticalRadius < 1) {
            throw new IllegalArgumentException("grottoVerticalRadius must be positive");
        }
        if (dryHeadroom < 0) {
            throw new IllegalArgumentException("dryHeadroom cannot be negative");
        }
        if (maxClosedComponentHorizontalRadius < 1) {
            throw new IllegalArgumentException("maxClosedComponentHorizontalRadius must be positive");
        }
        if (maxClosedComponentDepth < 1) {
            throw new IllegalArgumentException("maxClosedComponentDepth must be positive");
        }
    }
}
