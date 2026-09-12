package art.arcane.iris.generation.hydrology;

import java.util.Objects;

public record HydrologyDiagnosticCandidate(
        long id,
        HydrologyCandidateKind kind,
        HydrologyFeatureType projectedType,
        HydrologyPoint point,
        HydrologyCandidateRejection rejection,
        int detail
) {
    public HydrologyDiagnosticCandidate {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(projectedType, "projectedType");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(rejection, "rejection");
    }
}
