package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;

import java.util.List;
import java.util.Objects;

public record HydrologyTerrainSample(
        int naturalHeight,
        double slope,
        boolean ocean,
        boolean caveAvailable,
        int caveFloorY,
        int caveFluidY,
        boolean transitAllowed,
        boolean outletAllowed,
        boolean surfaceSourceAllowed,
        boolean surfaceSourceRequired,
        boolean undergroundSourceAllowed,
        boolean undergroundSourceRequired,
        double routingCost,
        double surfaceSourceWeight,
        double undergroundSourceWeight,
        double widthMultiplier,
        double depthMultiplier,
        double incisionMultiplier,
        double routingMultiplier,
        double bankMultiplier,
        String parentBiomeKey,
        String surfaceBiomeKey,
        String mouthBiomeKey,
        String shoreBiomeKey,
        String bankBiomeKey,
        String floodedCaveBiomeKey,
        List<String> preferredProfileKeys,
        List<String> surfacePoolKeys,
        double shoreBiomeWidth,
        String confinesKey,
        double shoreWidth,
        boolean erosion,
        SurfaceRiverPolicy surfacePolicy
) {
    /**
     * {@code shoreBiomeWidth} is the width in blocks of the shore biome band beside a surface river at this
     * column; {@code NaN} means the column has no policy value and the geometric shore width applies.
     * {@code confinesKey} names the region or biome a river passing this column must stay inside, or is
     * null where rivers may flow anywhere. {@code shoreWidth} is the width in blocks of the level shore
     * bench beside the water at this column, {@code NaN} where no policy set one and the dimension's bank
     * shore width applies; {@code erosion} is whether the ground beyond that bench is eroded into a valley here.
     */
    public HydrologyTerrainSample {
        surfacePolicy = Objects.requireNonNull(surfacePolicy);
        requireFiniteNonNegative(slope, "slope");
        requireFiniteNonNegative(routingCost, "routingCost");
        requireFiniteNonNegative(surfaceSourceWeight, "surfaceSourceWeight");
        requireFiniteNonNegative(undergroundSourceWeight, "undergroundSourceWeight");
        requireFinitePositive(widthMultiplier, "widthMultiplier");
        requireFinitePositive(depthMultiplier, "depthMultiplier");
        requireFiniteNonNegative(incisionMultiplier, "incisionMultiplier");
        requireFiniteNonNegative(routingMultiplier, "routingMultiplier");
        requireFinitePositive(bankMultiplier, "bankMultiplier");
        if (caveAvailable && (caveFluidY <= caveFloorY || caveFluidY >= naturalHeight)) {
            throw new IllegalArgumentException("Available cave fluid must be above its floor and below natural terrain.");
        }
        parentBiomeKey = normalizeKey(parentBiomeKey, "parent");
        surfaceBiomeKey = normalizeKey(surfaceBiomeKey, parentBiomeKey);
        mouthBiomeKey = normalizeKey(mouthBiomeKey, surfaceBiomeKey);
        shoreBiomeKey = normalizeKey(shoreBiomeKey, parentBiomeKey);
        bankBiomeKey = normalizeKey(bankBiomeKey, parentBiomeKey);
        floodedCaveBiomeKey = normalizeKey(floodedCaveBiomeKey, surfaceBiomeKey);
        preferredProfileKeys = normalizeProfiles(preferredProfileKeys);
        surfacePoolKeys = surfacePoolKeys == null ? List.of() : List.copyOf(surfacePoolKeys);
        if (!Double.isNaN(shoreBiomeWidth) && (!Double.isFinite(shoreBiomeWidth) || shoreBiomeWidth < 0D)) {
            throw new IllegalArgumentException("shoreBiomeWidth must be NaN, or finite and non-negative.");
        }
        if (!Double.isNaN(shoreWidth) && (!Double.isFinite(shoreWidth) || shoreWidth < 0D)) {
            throw new IllegalArgumentException("shoreWidth must be NaN, or finite and non-negative.");
        }
        confinesKey = confinesKey == null || confinesKey.isBlank() ? null : confinesKey;
    }

    /** The shore biome band width at this column, or {@code fallback} when no policy set one. */
    public double shoreBiomeWidth(double fallback) {
        return Double.isNaN(shoreBiomeWidth) ? fallback : shoreBiomeWidth;
    }

    /** The level shore bench width at this column, or {@code fallback} when no policy set one. */
    public double shoreWidth(double fallback) {
        return Double.isNaN(shoreWidth) ? fallback : shoreWidth;
    }

    /** Whether a river at {@code upstream} may drain into this column without leaving its confines. */
    public boolean drainsInto(HydrologyTerrainSample downstream) {
        return confinesKey == null || confinesKey.equals(downstream.confinesKey());
    }

    public static HydrologyTerrainSample openLand(int naturalHeight, double slope, String parentBiomeKey) {
        return new HydrologyTerrainSample(
                naturalHeight,
                slope,
                false,
                false,
                naturalHeight - 32,
                naturalHeight - 30,
                true,
                true,
                true,
                false,
                false,
                false,
                0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                1D,
                1D,
                parentBiomeKey,
                parentBiomeKey,
                parentBiomeKey,
                parentBiomeKey,
                parentBiomeKey,
                parentBiomeKey,
                List.of("default"),
                List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    public static HydrologyTerrainSample ocean(int naturalHeight, String parentBiomeKey) {
        return new HydrologyTerrainSample(
                naturalHeight,
                0D,
                true,
                false,
                naturalHeight - 32,
                naturalHeight - 30,
                false,
                false,
                false,
                false,
                false,
                false,
                0D,
                0D,
                0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                parentBiomeKey,
                parentBiomeKey,
                parentBiomeKey,
                parentBiomeKey,
                parentBiomeKey,
                parentBiomeKey,
                List.of("default"),
                List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    public HydrologyTerrainSample withSurfacePoolKeys(List<String> replacementPoolKeys) {
        return new HydrologyTerrainSample(
                naturalHeight,
                slope,
                ocean,
                caveAvailable,
                caveFloorY,
                caveFluidY,
                transitAllowed,
                outletAllowed,
                surfaceSourceAllowed,
                surfaceSourceRequired,
                undergroundSourceAllowed,
                undergroundSourceRequired,
                routingCost,
                surfaceSourceWeight,
                undergroundSourceWeight,
                widthMultiplier,
                depthMultiplier,
                incisionMultiplier,
                routingMultiplier,
                bankMultiplier,
                parentBiomeKey,
                surfaceBiomeKey,
                mouthBiomeKey,
                shoreBiomeKey,
                bankBiomeKey,
                floodedCaveBiomeKey,
                preferredProfileKeys,
                replacementPoolKeys,
                shoreBiomeWidth,
                confinesKey,
                shoreWidth,
                erosion,
                surfacePolicy
        );
    }

    public HydrologyTerrainSample withSlope(double replacementSlope) {
        return new HydrologyTerrainSample(
                naturalHeight,
                replacementSlope,
                ocean,
                caveAvailable,
                caveFloorY,
                caveFluidY,
                transitAllowed,
                outletAllowed,
                surfaceSourceAllowed,
                surfaceSourceRequired,
                undergroundSourceAllowed,
                undergroundSourceRequired,
                routingCost,
                surfaceSourceWeight,
                undergroundSourceWeight,
                widthMultiplier,
                depthMultiplier,
                incisionMultiplier,
                routingMultiplier,
                bankMultiplier,
                parentBiomeKey,
                surfaceBiomeKey,
                mouthBiomeKey,
                shoreBiomeKey,
                bankBiomeKey,
                floodedCaveBiomeKey,
                preferredProfileKeys,
                surfacePoolKeys,
                shoreBiomeWidth,
                confinesKey,
                shoreWidth,
                erosion,
                surfacePolicy
        );
    }

    public HydrologyTerrainSample withShoreWidth(double replacementShoreWidth) {
        return new HydrologyTerrainSample(
                naturalHeight,
                slope,
                ocean,
                caveAvailable,
                caveFloorY,
                caveFluidY,
                transitAllowed,
                outletAllowed,
                surfaceSourceAllowed,
                surfaceSourceRequired,
                undergroundSourceAllowed,
                undergroundSourceRequired,
                routingCost,
                surfaceSourceWeight,
                undergroundSourceWeight,
                widthMultiplier,
                depthMultiplier,
                incisionMultiplier,
                routingMultiplier,
                bankMultiplier,
                parentBiomeKey,
                surfaceBiomeKey,
                mouthBiomeKey,
                shoreBiomeKey,
                bankBiomeKey,
                floodedCaveBiomeKey,
                preferredProfileKeys,
                surfacePoolKeys,
                shoreBiomeWidth,
                confinesKey,
                replacementShoreWidth,
                erosion,
                surfacePolicy
        );
    }

    public HydrologyTerrainSample withErosion(boolean replacementErosion) {
        return new HydrologyTerrainSample(
                naturalHeight,
                slope,
                ocean,
                caveAvailable,
                caveFloorY,
                caveFluidY,
                transitAllowed,
                outletAllowed,
                surfaceSourceAllowed,
                surfaceSourceRequired,
                undergroundSourceAllowed,
                undergroundSourceRequired,
                routingCost,
                surfaceSourceWeight,
                undergroundSourceWeight,
                widthMultiplier,
                depthMultiplier,
                incisionMultiplier,
                routingMultiplier,
                bankMultiplier,
                parentBiomeKey,
                surfaceBiomeKey,
                mouthBiomeKey,
                shoreBiomeKey,
                bankBiomeKey,
                floodedCaveBiomeKey,
                preferredProfileKeys,
                surfacePoolKeys,
                shoreBiomeWidth,
                confinesKey,
                shoreWidth,
                replacementErosion,
                surfacePolicy
        );
    }

    public HydrologyTerrainSample withSurfacePolicy(SurfaceRiverPolicy replacementPolicy) {
        return new HydrologyTerrainSample(
                naturalHeight,
                slope,
                ocean,
                caveAvailable,
                caveFloorY,
                caveFluidY,
                transitAllowed,
                outletAllowed,
                surfaceSourceAllowed,
                surfaceSourceRequired,
                undergroundSourceAllowed,
                undergroundSourceRequired,
                routingCost,
                surfaceSourceWeight,
                undergroundSourceWeight,
                widthMultiplier,
                depthMultiplier,
                incisionMultiplier,
                routingMultiplier,
                bankMultiplier,
                parentBiomeKey,
                surfaceBiomeKey,
                mouthBiomeKey,
                shoreBiomeKey,
                bankBiomeKey,
                floodedCaveBiomeKey,
                preferredProfileKeys,
                surfacePoolKeys,
                shoreBiomeWidth,
                confinesKey,
                shoreWidth,
                erosion,
                replacementPolicy
        );
    }

    private static List<String> normalizeProfiles(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of("default");
        }
        if (profilesNormalized(values)) {
            return List.copyOf(values);
        }
        List<String> normalized = values.stream()
                .filter((String value) -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        return normalized.isEmpty() ? List.of("default") : normalized;
    }

    private static boolean profilesNormalized(List<String> values) {
        String previous = null;
        for (String value : values) {
            if (value == null
                    || value.isBlank()
                    || !value.equals(value.trim())
                    || previous != null && previous.compareTo(value) >= 0) {
                return false;
            }
            previous = value;
        }
        return true;
    }

    private static String normalizeKey(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0D) {
            throw new IllegalArgumentException(name + " must be finite and positive.");
        }
    }
}
