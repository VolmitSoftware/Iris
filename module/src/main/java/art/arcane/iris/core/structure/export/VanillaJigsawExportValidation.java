package art.arcane.iris.core.structure.export;

import java.util.List;

public record VanillaJigsawExportValidation(
        List<String> plannedResources,
        List<VanillaJigsawExportDiagnostic> diagnostics
) {
    public VanillaJigsawExportValidation {
        plannedResources = List.copyOf(plannedResources);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean isExportable() {
        for (VanillaJigsawExportDiagnostic diagnostic : diagnostics) {
            if (diagnostic.isBlocking()) {
                return false;
            }
        }
        return true;
    }
}
