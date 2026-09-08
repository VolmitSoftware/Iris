package art.arcane.iris.engine.hydrology;

import java.util.List;

record HydrologyOwnerDraft(
        HydrologyTileKey key,
        HydrologyCaveCourseFilter.Result result,
        List<HydrologyDiagnosticCandidate> diagnostics,
        HydrologyFootprintCompiler footprintCompiler
) {
    HydrologyOwnerDraft withoutFootprintCompiler() {
        if (footprintCompiler == null) {
            return this;
        }
        return new HydrologyOwnerDraft(key, result, diagnostics, null);
    }
}
