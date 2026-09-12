package art.arcane.iris.generation.hydrology.policy;

import java.util.Objects;

public record SurfaceRiverPolicy(
        String areaKey,
        Double sourceDensity,
        Integer sourceSpacing,
        Integer tributaries,
        Integer inlandOutlets,
        Integer coastalOutlets,
        Integer minimumCourseLength,
        Integer maximumIncision
) {
    public static final SurfaceRiverPolicy INHERIT = new SurfaceRiverPolicy("", null, null, null, null, null, null, null);

    public SurfaceRiverPolicy {
        areaKey = Objects.requireNonNull(areaKey);
        if (sourceDensity != null && (!Double.isFinite(sourceDensity) || sourceDensity < 0D || sourceDensity > 64D)) {
            throw new IllegalArgumentException("surfaceSourceDensity must be finite and between 0 and 64.");
        }
        requireRange(sourceSpacing, 0, 8192, "surfaceSourceSpacing");
        requireRange(tributaries, 0, 4, "surfaceTributaries");
        requireRange(inlandOutlets, 0, 256, "surfaceInlandOutlets");
        requireRange(coastalOutlets, 0, 64, "surfaceCoastalOutlets");
        requireRange(minimumCourseLength, 16, 4096, "surfaceMinimumCourseLength");
        requireRange(maximumIncision, 1, 32, "surfaceMaximumIncision");
    }

    public boolean overridden() {
        return sourceDensity != null || sourceSpacing != null || tributaries != null
                || inlandOutlets != null || coastalOutlets != null;
    }

    public boolean configured() {
        return overridden() || minimumCourseLength != null || maximumIncision != null;
    }

    public Budget budget() {
        return new Budget(areaKey, sourceDensity, sourceSpacing, tributaries, inlandOutlets, coastalOutlets);
    }

    public int minimumCourseLength(int fallback) {
        return minimumCourseLength == null ? fallback : minimumCourseLength;
    }

    public int maximumIncision(int fallback) {
        return maximumIncision == null ? fallback : maximumIncision;
    }

    public double sourceDensity(double fallback) {
        return sourceDensity == null ? fallback : sourceDensity;
    }

    public int sourceSpacing(int fallback) {
        return sourceSpacing == null ? fallback : sourceSpacing;
    }

    public int tributaries(int fallback) {
        return tributaries == null ? fallback : tributaries;
    }

    public int inlandOutlets(int fallback) {
        return inlandOutlets == null ? fallback : inlandOutlets;
    }

    public int coastalOutlets(int fallback) {
        return coastalOutlets == null ? fallback : coastalOutlets;
    }

    private static void requireRange(Integer value, int minimum, int maximum, String name) {
        if (value != null && (value < minimum || value > maximum)) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ".");
        }
    }

    public record Budget(String areaKey, Double sourceDensity, Integer sourceSpacing, Integer tributaries,
                         Integer inlandOutlets, Integer coastalOutlets) {
    }
}
