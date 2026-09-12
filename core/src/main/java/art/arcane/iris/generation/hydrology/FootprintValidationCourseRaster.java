package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceFootprint;

import java.util.List;

record FootprintValidationCourseRaster(
        long courseId,
        List<HydrologyColumnSample> columns,
        SurfaceFootprint surface
) {
}
