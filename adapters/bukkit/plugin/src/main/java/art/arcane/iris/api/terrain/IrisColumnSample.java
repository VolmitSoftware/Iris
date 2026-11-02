package art.arcane.iris.api.terrain;

import java.util.Objects;

public record IrisColumnSample(
        int blockX,
        int blockZ,
        int surfaceHeight,
        int naturalHeight,
        IrisSurfaceKind surfaceKind,
        String biomeKey,
        IrisRiverState riverState,
        double riverDistance,
        int riverFlow,
        int riverWaterSurfaceY,
        boolean provisional
) {
    public static final int UNAVAILABLE_HEIGHT = Integer.MIN_VALUE;
    public static final double UNAVAILABLE_RIVER_DISTANCE = Double.NaN;
    public static final int UNAVAILABLE_RIVER_FLOW = -1;

    public IrisColumnSample {
        Objects.requireNonNull(surfaceKind, "surfaceKind");
        Objects.requireNonNull(riverState, "riverState");
        biomeKey = biomeKey == null || biomeKey.isBlank() ? null : biomeKey;
        if (!Double.isNaN(riverDistance) && (!Double.isFinite(riverDistance) || riverDistance < 0D)) {
            throw new IllegalArgumentException("riverDistance must be non-negative, finite, or unavailable");
        }
        if (riverFlow < UNAVAILABLE_RIVER_FLOW) {
            throw new IllegalArgumentException("riverFlow must be non-negative or unavailable");
        }
    }

    public boolean hasSurfaceHeight() {
        return surfaceHeight != UNAVAILABLE_HEIGHT;
    }

    public boolean hasNaturalHeight() {
        return naturalHeight != UNAVAILABLE_HEIGHT;
    }

    public boolean hasSurfaceKind() {
        return surfaceKind != IrisSurfaceKind.UNKNOWN;
    }

    public boolean hasBiomeKey() {
        return biomeKey != null;
    }

    public boolean hasRiverState() {
        return riverState != IrisRiverState.NONE;
    }

    public boolean hasRiverDistance() {
        return !Double.isNaN(riverDistance);
    }

    public boolean hasRiverFlow() {
        return riverFlow != UNAVAILABLE_RIVER_FLOW;
    }

    public boolean hasRiverWaterSurfaceY() {
        return riverWaterSurfaceY != UNAVAILABLE_HEIGHT;
    }
}
