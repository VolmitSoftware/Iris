package art.arcane.iris.generation.hydrology;

import java.util.List;

record SourceRoutingContext(
        HydrologySampledGrid grid,
        HydrologyRoutingPlan surfaceRouting,
        HydrologyRoutingPlan undergroundRouting,
        List<HydrologyDiagnosticCandidate> diagnostics
) {
    HydrologyRoutingPlan routing(boolean surface) {
        return surface ? surfaceRouting : undergroundRouting;
    }
}
