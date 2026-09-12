package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyPoint;

public record SurfaceRunBoundary(Plane start, Plane end) {
    public static SurfaceRunBoundary unbounded() {
        return new SurfaceRunBoundary(null, null);
    }

    public boolean allows(int x, int z) {
        return (start == null || start.projection(x, z) >= 0L)
                && (end == null || end.projection(x, z) <= 0L);
    }

    public record Plane(int x, int z, int flowX, int flowZ, double radius) {
        public static Plane between(HydrologyPoint origin, HydrologyPoint upstream, HydrologyPoint downstream, double radius) {
            return new Plane(origin.x(), origin.z(), downstream.x() - upstream.x(), downstream.z() - upstream.z(), radius);
        }

        private long projection(int pointX, int pointZ) {
            long dx = (long) pointX - x;
            long dz = (long) pointZ - z;
            return (double) dx * dx + (double) dz * dz > radius * radius ? 0L : dx * flowX + dz * flowZ;
        }
    }
}
