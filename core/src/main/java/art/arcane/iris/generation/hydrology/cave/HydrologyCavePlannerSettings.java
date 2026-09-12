package art.arcane.iris.generation.hydrology.cave;

import java.util.Objects;

public record HydrologyCavePlannerSettings(
        int maxHorizontalRadius,
        int maxDepth,
        int maxFloodVolume,
        int maxThroatLength,
        int throatRadius,
        int grottoHorizontalRadius,
        int grottoVerticalRadius,
        int dryHeadroom,
        HydrologyCaveFluidPolicy existingFluidPolicy,
        HydrologyCaveGrottoShape grottoShape,
        int maxClosedComponentHorizontalRadius,
        int maxClosedComponentDepth
) {
    public static final int MAXIMUM_PLANNED_MUTATIONS = 262_144;

    public HydrologyCavePlannerSettings(
            int maxHorizontalRadius,
            int maxDepth,
            int maxFloodVolume,
            int maxThroatLength,
            int throatRadius,
            int grottoHorizontalRadius,
            int grottoVerticalRadius,
            int dryHeadroom,
            HydrologyCaveFluidPolicy existingFluidPolicy,
            HydrologyCaveGrottoShape grottoShape
    ) {
        this(
                maxHorizontalRadius,
                maxDepth,
                maxFloodVolume,
                maxThroatLength,
                throatRadius,
                grottoHorizontalRadius,
                grottoVerticalRadius,
                dryHeadroom,
                existingFluidPolicy,
                grottoShape,
                maxHorizontalRadius,
                maxDepth
        );
    }

    public HydrologyCavePlannerSettings(
            int maxHorizontalRadius,
            int maxDepth,
            int maxFloodVolume,
            int maxThroatLength,
            int grottoHorizontalRadius,
            int grottoVerticalRadius,
            HydrologyCaveFluidPolicy existingFluidPolicy
    ) {
        this(
                maxHorizontalRadius,
                maxDepth,
                maxFloodVolume,
                maxThroatLength,
                1,
                grottoHorizontalRadius,
                grottoVerticalRadius,
                0,
                existingFluidPolicy,
                HydrologyCaveGrottoShape.ELLIPSOID
        );
    }

    public HydrologyCavePlannerSettings {
        Objects.requireNonNull(existingFluidPolicy);
        Objects.requireNonNull(grottoShape);
        if (maxHorizontalRadius < 1) {
            throw new IllegalArgumentException("maxHorizontalRadius must be positive");
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxFloodVolume < 1) {
            throw new IllegalArgumentException("maxFloodVolume must be positive");
        }
        if (maxThroatLength < 1) {
            throw new IllegalArgumentException("maxThroatLength must be positive");
        }
        if (throatRadius < 1) {
            throw new IllegalArgumentException("throatRadius must be positive");
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
