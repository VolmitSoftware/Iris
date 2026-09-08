package art.arcane.iris.engine.hydrology.cave;

import art.arcane.iris.engine.hydrology.HydrologyObservedPlannedSurface;

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
            int resolvedHeight = plannedSurface.resolve(
                    observation.x(),
                    observation.z(),
                    observation.naturalHeight()
            );
            if (resolvedHeight != observation.resolvedHeight()) {
                return false;
            }
        }
        return true;
    }
}
