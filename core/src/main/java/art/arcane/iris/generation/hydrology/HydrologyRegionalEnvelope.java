package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceBounds;

import java.util.List;

record HydrologyRegionalEnvelope(long minimumX, long minimumZ, long maximumX, long maximumZ) {
    private static final HydrologyRegionalEnvelope UNBOUNDED = new HydrologyRegionalEnvelope(
            Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);

    static HydrologyRegionalEnvelope of(List<HydrologyPoint> guide, HydrologyPlannerSettings settings, int footprintRadius) {
        long minimumX = Long.MAX_VALUE;
        long minimumZ = Long.MAX_VALUE;
        long maximumX = Long.MIN_VALUE;
        long maximumZ = Long.MIN_VALUE;
        for (HydrologyPoint point : guide) {
            minimumX = Math.min(minimumX, point.x());
            minimumZ = Math.min(minimumZ, point.z());
            maximumX = Math.max(maximumX, point.x());
            maximumZ = Math.max(maximumZ, point.z());
        }
        if (guide.isEmpty()) {
            return UNBOUNDED;
        }
        int spacing = settings.routing().regional().sampleSpacing();
        double commonPadding = spacing + spacing * settings.geometry().meanders().maximumOffsetRatio()
                + 256D + 2D + Math.max(footprintRadius, (long) settings.publicationRadius() + 1L);
        double paddingX = StrictMath.ceil(commonPadding + (maximumX - minimumX + 2D * spacing) / 8D);
        double paddingZ = StrictMath.ceil(commonPadding + (maximumZ - minimumZ + 2D * spacing) / 8D);
        if (!Double.isFinite(paddingX) || !Double.isFinite(paddingZ)
                || minimumX - paddingX < Integer.MIN_VALUE || maximumX + paddingX > Integer.MAX_VALUE
                || minimumZ - paddingZ < Integer.MIN_VALUE || maximumZ + paddingZ > Integer.MAX_VALUE) {
            return UNBOUNDED;
        }
        return new HydrologyRegionalEnvelope(minimumX - (long) paddingX, minimumZ - (long) paddingZ,
                maximumX + (long) paddingX, maximumZ + (long) paddingZ);
    }

    boolean intersects(SurfaceBounds bounds) {
        return maximumX >= bounds.minimumX() && minimumX <= bounds.maximumX()
                && maximumZ >= bounds.minimumZ() && minimumZ <= bounds.maximumZ();
    }
}
