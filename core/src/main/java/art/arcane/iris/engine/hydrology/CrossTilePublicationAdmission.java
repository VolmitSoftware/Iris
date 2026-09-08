package art.arcane.iris.engine.hydrology;

import java.util.List;
import java.util.Objects;

record CrossTilePublicationAdmission(
        HydrologyCaveCourseFilter.Result result,
        List<HydrologyDiagnosticCandidate> diagnostics,
        boolean rejectedCourses
) {
    CrossTilePublicationAdmission {
        Objects.requireNonNull(result, "result");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
