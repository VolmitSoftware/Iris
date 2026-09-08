package art.arcane.iris.engine.hydrology;

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
