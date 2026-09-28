package art.arcane.iris.generation.hydrology;

import java.util.List;
import java.util.Objects;

record HydrologyOwnerDraft(
        HydrologyTileKey key,
        HydrologyCaveCourseFilter.Result result,
        List<HydrologyDiagnosticCandidate> diagnostics,
        HydrologyFootprintCompiler footprintCompiler
) {
    HydrologyOwnerDraft {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(result, "result");
        result = new HydrologyCaveCourseFilter.Result(List.copyOf(result.nodes()), List.copyOf(result.edges()),
                List.copyOf(result.outlets()), List.copyOf(result.courses()), List.copyOf(result.cavePlans()));
        diagnostics = List.copyOf(diagnostics);
    }

    HydrologyOwnerDraft withoutFootprintCompiler() {
        if (footprintCompiler == null) {
            return this;
        }
        return new HydrologyOwnerDraft(key, result, diagnostics, null);
    }
}
