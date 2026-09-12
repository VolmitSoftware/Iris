package art.arcane.iris.generation.hydrology;

import java.util.List;
import java.util.Objects;

record CrossTileResolution(
        HydrologyOwnerDraft draft,
        List<CrossTileRejectedCourse> observedRejections,
        int iterations,
        int ownerCount,
        long resolutionNanos
) {
    CrossTileResolution {
        Objects.requireNonNull(draft, "draft");
        observedRejections = List.copyOf(Objects.requireNonNull(observedRejections, "observedRejections"));
    }
}
