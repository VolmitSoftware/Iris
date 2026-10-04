package art.arcane.iris.engine.framework.structure;

import java.util.Objects;

public record StructureGraphDiagnostic(Code code, String message) {
    public StructureGraphDiagnostic {
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
    }

    public Severity severity() {
        return code.severity();
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public enum Code {
        MISSING_START_POOL(Severity.ERROR),
        EMPTY_START_POOL(Severity.ERROR),
        MISSING_POOL(Severity.ERROR),
        INVALID_MAX_DEPTH(Severity.ERROR),
        INVALID_MAX_SIZE(Severity.ERROR),
        INVALID_POOL_ENTRY(Severity.ERROR),
        INVALID_WEIGHT(Severity.ERROR),
        INVALID_CHANCE(Severity.ERROR),
        INVALID_THEME(Severity.ERROR),
        INVALID_PIECE_RULE(Severity.ERROR),
        UNSATISFIABLE_PIECE_RULE(Severity.ERROR),
        MISSING_REQUIRED_FALLBACK(Severity.ERROR),
        NO_REQUIRED_TERMINAL(Severity.ERROR),
        NON_PORTABLE_METADATA(Severity.ERROR),
        MISSING_PIECE(Severity.ERROR),
        MISSING_OBJECT(Severity.ERROR),
        INVALID_OBJECT_BOUNDS(Severity.ERROR),
        INVALID_CONNECTOR(Severity.ERROR),
        INVALID_CONNECTOR_METADATA(Severity.ERROR),
        INVALID_CONNECTOR_POSITION(Severity.ERROR),
        INVALID_CONNECTOR_DIRECTION(Severity.ERROR),
        INVALID_PLANAR_CELL_SIZE(Severity.ERROR),
        INVALID_PLANAR_WORKCELL(Severity.ERROR),
        INVALID_PLANAR_OBJECT_SIZE(Severity.ERROR),
        INVALID_PLANAR_CONNECTOR(Severity.ERROR),
        NON_PORTABLE_CONNECTOR(Severity.ERROR),
        MISSING_CONNECTOR_POOL(Severity.ERROR),
        FALLBACK_CYCLE(Severity.ERROR),
        NO_ENABLED_START_PIECE(Severity.ERROR),
        NO_VIABLE_START_PIECE(Severity.WARNING),
        NO_COMPATIBLE_CONNECTOR(Severity.WARNING),
        UNREACHABLE_POOL(Severity.WARNING),
        UNREACHABLE_PIECE(Severity.WARNING),
        ASSEMBLY_RUNTIME_FAILURE(Severity.ERROR),
        ASSEMBLY_PIECE_CAP_REACHED(Severity.WARNING);

        private final Severity severity;

        Code(Severity severity) {
            this.severity = severity;
        }

        public Severity severity() {
            return severity;
        }
    }
}
