package art.arcane.iris.generation.hydrology;

import java.util.List;

record FootprintCourseRasterKey(
        long courseId,
        RiverCourseType courseType,
        String profileKey,
        List<HydraulicSegment> segments
) {
}
