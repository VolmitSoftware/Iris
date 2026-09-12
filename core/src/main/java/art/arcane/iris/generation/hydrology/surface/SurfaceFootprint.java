package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;

import java.util.List;
import java.util.Objects;

public record SurfaceFootprint(List<SurfaceLayerColumn> columns, int uncontainedWetCells,
                               HydrologyCandidateRejection rejection, int rejectionDetail, long bankExcavation) {
    public SurfaceFootprint {
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
    }

    public static SurfaceFootprint empty() {
        return new SurfaceFootprint(List.of(), 0, null, 0, 0L);
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    public boolean accepted() {
        return rejection == null;
    }
}
