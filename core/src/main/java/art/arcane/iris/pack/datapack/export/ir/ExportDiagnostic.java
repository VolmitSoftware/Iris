package art.arcane.iris.pack.datapack.export.ir;

import java.util.Objects;

public record ExportDiagnostic(Severity severity, ExportLimit limit, String resource, String message) {
    public ExportDiagnostic {
        Objects.requireNonNull(severity);
        Objects.requireNonNull(limit);
        resource = resource == null ? "" : resource;
        Objects.requireNonNull(message);
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
