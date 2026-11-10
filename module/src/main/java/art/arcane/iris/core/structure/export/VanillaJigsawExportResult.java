package art.arcane.iris.core.structure.export;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record VanillaJigsawExportResult(
        Status status,
        Path output,
        List<String> resources,
        List<VanillaJigsawExportDiagnostic> diagnostics
) {
    public VanillaJigsawExportResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(output);
        resources = List.copyOf(resources);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean isSuccess() {
        return status == Status.EXPORTED;
    }

    public boolean hasBlockingDiagnostics() {
        for (VanillaJigsawExportDiagnostic diagnostic : diagnostics) {
            if (diagnostic.isBlocking()) {
                return true;
            }
        }
        return false;
    }

    public enum Status {
        EXPORTED,
        REJECTED,
        FAILED
    }
}
