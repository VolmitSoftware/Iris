package art.arcane.iris.generation.hydrology;

import java.util.Objects;

record CourseEdgeKey(RiverCourseType type, long edgeId) {
    CourseEdgeKey {
        Objects.requireNonNull(type, "type");
    }
}
