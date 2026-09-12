package art.arcane.iris.generation.hydrology;

import java.util.Objects;

record CrossTileRejectedCourse(RiverCourse course, long winnerSourceId) {
    CrossTileRejectedCourse {
        Objects.requireNonNull(course, "course");
    }
}
