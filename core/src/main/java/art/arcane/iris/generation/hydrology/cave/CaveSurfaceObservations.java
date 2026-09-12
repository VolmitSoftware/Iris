package art.arcane.iris.generation.hydrology.cave;

import art.arcane.iris.generation.hydrology.HydrologyObservedPlannedSurface;

import java.util.ArrayList;
import java.util.List;

record CaveSurfaceObservations(
        List<HydrologyObservedPlannedSurface.Observation> observations
) {
    static CaveSurfaceObservations empty() {
        return new CaveSurfaceObservations(List.of());
    }

    static CaveSurfaceObservations capture(
            CaveViewObservations viewObservations,
            HydrologyObservedPlannedSurface plannedSurface
    ) {
        ArrayList<HydrologyObservedPlannedSurface.Observation> observations = new ArrayList<>();
        for (int index = 0; index < viewObservations.size(); index++) {
            CavePosition position = viewObservations.positions()[index];
            observations.addAll(plannedSurface.observationsAt(position.x(), position.z()));
        }
        return new CaveSurfaceObservations(List.copyOf(observations));
    }

    boolean matches(HydrologyObservedPlannedSurface plannedSurface) {
        for (HydrologyObservedPlannedSurface.Observation observation : observations) {
            if (!observation.matches(plannedSurface)) {
                return false;
            }
        }
        return true;
    }
}
