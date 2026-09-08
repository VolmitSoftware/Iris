package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.surface.SurfaceFootprint;

import java.util.List;

record FootprintValidationCourseRaster(
        long courseId,
        List<HydrologyColumnSample> columns,
        SurfaceFootprint surface
) {
}
