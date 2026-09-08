package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCandidate;

import java.util.Objects;

record CaveSurfaceComposition(
        HydrologyCaveCandidate candidate,
        boolean accepted
) {
    static CaveSurfaceComposition accepted(HydrologyCaveCandidate candidate) {
        return new CaveSurfaceComposition(Objects.requireNonNull(candidate), true);
    }

    static CaveSurfaceComposition rejected() {
        return new CaveSurfaceComposition(null, false);
    }
}
