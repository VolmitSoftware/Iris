package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologySurfaceDropRaster;

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
