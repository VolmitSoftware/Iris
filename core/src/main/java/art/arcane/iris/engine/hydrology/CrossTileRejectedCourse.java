package art.arcane.iris.engine.hydrology;

import java.util.Objects;

record CrossTileRejectedCourse(RiverCourse course, long winnerSourceId) {
    CrossTileRejectedCourse {
        Objects.requireNonNull(course, "course");
    }
}
