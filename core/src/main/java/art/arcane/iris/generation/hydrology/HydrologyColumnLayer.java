package art.arcane.iris.generation.hydrology;

import java.util.Objects;

public record HydrologyColumnLayer(
        HydrologyFeatureRef feature,
        int bedY,
        int fluidHeadY,
        int ceilingY,
        boolean channel,
        boolean shore,
        boolean grading,
        boolean connectedFluid,
        boolean fallingFluid,
        boolean receivingPool,
        boolean terrainOwned,
        boolean fluidOwned,
        boolean oceanApron,
        String profileKey,
        String surfaceBiomeKey,
        String mouthBiomeKey,
        String shoreBiomeKey,
        String bankBiomeKey,
        String floodedCaveBiomeKey
) {
    public HydrologyColumnLayer {
        Objects.requireNonNull(feature, "feature");
        profileKey = requireKey(profileKey, "profileKey");
        surfaceBiomeKey = requireKey(surfaceBiomeKey, "surfaceBiomeKey");
        mouthBiomeKey = requireKey(mouthBiomeKey, "mouthBiomeKey");
        shoreBiomeKey = requireKey(shoreBiomeKey, "shoreBiomeKey");
        bankBiomeKey = requireKey(bankBiomeKey, "bankBiomeKey");
        floodedCaveBiomeKey = requireKey(floodedCaveBiomeKey, "floodedCaveBiomeKey");
        if (channel && bedY > fluidHeadY) {
            throw new IllegalArgumentException("A channel bed cannot be above its fluid head.");
        }
        if (ceilingY < fluidHeadY) {
            throw new IllegalArgumentException("A hydrology carve ceiling cannot be below its fluid head.");
        }
        if (!feature.type().isUnderground() && !feature.type().isDeepFluid() && ceilingY != fluidHeadY) {
            throw new IllegalArgumentException("Surface hydrology ceilings must equal the fluid head.");
        }
        if (fluidOwned && !channel) {
            throw new IllegalArgumentException("Owned fluid requires a channel footprint.");
        }
        if (fallingFluid && !connectedFluid) {
            throw new IllegalArgumentException("Falling fluid must be connected.");
        }
        if (receivingPool && !connectedFluid) {
            throw new IllegalArgumentException("A receiving pool must be connected.");
        }
        if (oceanApron && (terrainOwned || fluidOwned || grading || shore)) {
            throw new IllegalArgumentException("An ocean apron cannot own river writes or grading.");
        }
    }

    public String biomeKey() {
        if (grading && !channel && !shore) {
            return bankBiomeKey;
        }
        if (shore) {
            return shoreBiomeKey;
        }
        if (feature.type() == HydrologyFeatureType.MOUTH || feature.type() == HydrologyFeatureType.COASTAL_GROTTO) {
            return mouthBiomeKey;
        }
        if (feature.type().isUnderground() || feature.type().isDeepFluid()) {
            return floodedCaveBiomeKey;
        }
        return surfaceBiomeKey;
    }

    /** Every owned channel water column is published so steps between stations receive fluid updates. */
    public boolean publishesSurfaceFluid() {
        return feature.type().isSurface()
                && !oceanApron
                && channel
                && fluidOwned
                && (terrainOwned || fallingFluid)
                && fluidHeadY > bedY;
    }

    private static String requireKey(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value.trim();
    }
}
