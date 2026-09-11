package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydrologySurfaceDropRaster;

import java.util.Objects;

public record SurfaceRasterContext(SurfaceBounds bounds, HydrologySurfaceDropRaster drops, SurfaceRunBoundary boundary) {
    public SurfaceRasterContext {
        Objects.requireNonNull(drops, "drops");
        Objects.requireNonNull(boundary, "boundary");
    }

    public static SurfaceRasterContext none() {
        return bounded(null);
    }

    public static SurfaceRasterContext bounded(SurfaceBounds bounds) {
        return new SurfaceRasterContext(bounds, HydrologySurfaceDropRaster.empty(), SurfaceRunBoundary.unbounded());
    }
}
