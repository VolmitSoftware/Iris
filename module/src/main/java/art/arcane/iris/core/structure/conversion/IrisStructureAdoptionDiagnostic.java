package art.arcane.iris.core.structure.conversion;

import java.util.Objects;

public record IrisStructureAdoptionDiagnostic(
        Severity severity,
        Code code,
        String resource,
        String detail,
        String recommendation
) implements Comparable<IrisStructureAdoptionDiagnostic> {
    public IrisStructureAdoptionDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        resource = normalize(resource);
        detail = requireText(detail, "detail");
        recommendation = normalize(recommendation);
    }

    public String summary() {
        String location = resource.isEmpty() ? "" : " [" + resource + "]";
        String action = recommendation.isEmpty() ? "" : " " + recommendation;
        return severity + " " + code + location + ": " + detail + action;
    }

    public boolean blocking() {
        return severity == Severity.ERROR;
    }

    @Override
    public int compareTo(IrisStructureAdoptionDiagnostic other) {
        int severityComparison = severity.compareTo(other.severity);
        if (severityComparison != 0) {
            return severityComparison;
        }
        int codeComparison = code.compareTo(other.code);
        if (codeComparison != 0) {
            return codeComparison;
        }
        int resourceComparison = resource.compareTo(other.resource);
        if (resourceComparison != 0) {
            return resourceComparison;
        }
        return detail.compareTo(other.detail);
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Adoption diagnostic " + name + " cannot be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public enum Code {
        SOURCE_GRAPH_INVALID,
        SOURCE_ALREADY_OWNED,
        SOURCE_RESOURCE_UNSAFE,
        SOURCE_RESOURCE_LIMIT,
        SOURCE_RESOURCE_CHANGED,
        TARGET_NAMESPACE_UNSUPPORTED,
        TARGET_RESOURCE_EXISTS,
        TARGET_RESOURCE_UNSAFE,
        TARGET_MAPPING_COLLISION,
        TARGET_REQUIRED_FOR_CLONE,
        MANAGED_INPUT_REQUIRES_CLONE,
        SHARED_DEPENDENCY,
        EXCLUSIVITY_UNPROVEN,
        IN_PLACE_AVAILABLE,
        CLONE_SELECTED,
        PLAN_BLOCKED,
        PLAN_EXPIRED,
        PLAN_UNKNOWN,
        PLAN_STALE,
        TRANSACTION_FAILED,
        APPLIED
    }
}
