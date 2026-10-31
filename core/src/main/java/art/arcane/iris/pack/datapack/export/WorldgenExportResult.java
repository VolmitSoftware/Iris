package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record WorldgenExportResult(Status status, Path output, String message, List<ExportDiagnostic> diagnostics,
                                   Map<String, String> loadKeyByBiomeId, ContentTrim trim, GenerationCost cost) {
    public WorldgenExportResult {
        Objects.requireNonNull(status);
        if (status == Status.EXPORTED) {
            Objects.requireNonNull(output);
        }
        message = message == null ? "" : message;
        diagnostics = List.copyOf(diagnostics);
        loadKeyByBiomeId = Collections.unmodifiableSortedMap(new TreeMap<>(loadKeyByBiomeId));
        Objects.requireNonNull(trim);
        Objects.requireNonNull(cost);
    }

    public static WorldgenExportResult exported(Path output, List<ExportDiagnostic> diagnostics, Map<String, String> loadKeyByBiomeId,
                                                ContentTrim trim, GenerationCost cost) {
        return new WorldgenExportResult(Status.EXPORTED, output, "", diagnostics, loadKeyByBiomeId, trim, cost);
    }

    public static WorldgenExportResult rejected(Path output, String message, List<ExportDiagnostic> diagnostics) {
        return new WorldgenExportResult(Status.REJECTED, output, message, diagnostics, Map.of(), ContentTrim.NONE, GenerationCost.NONE);
    }

    public static WorldgenExportResult failed(Path output, String message, List<ExportDiagnostic> diagnostics) {
        return new WorldgenExportResult(Status.FAILED, output, message, diagnostics, Map.of(), ContentTrim.NONE, GenerationCost.NONE);
    }

    public boolean isSuccess() {
        return status == Status.EXPORTED;
    }

    public enum Status {
        EXPORTED,
        REJECTED,
        FAILED
    }
}
