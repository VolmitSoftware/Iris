package art.arcane.iris.core.structure.conversion;

import art.arcane.iris.core.structure.authoring.StructureHash;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureTransactionReadSet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public record IrisStructureAdoptionPlan(
        UUID planId,
        Instant createdAt,
        Instant expiresAt,
        IrisStructureAdoptionRequest request,
        StructureKey targetStructure,
        IrisStructureAdoptionDisposition disposition,
        List<IrisStructureAdoptionDiagnostic> diagnostics,
        Map<String, String> sourceResourceHashes,
        Map<String, String> sourceToTargetPaths,
        StructureTransactionReadSet readSet,
        long totalSourceBytes,
        String sourceClosureHash,
        String planHash
) {
    public IrisStructureAdoptionPlan {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(targetStructure, "targetStructure");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = sortedDiagnostics(diagnostics);
        sourceResourceHashes = immutableMap(sourceResourceHashes);
        sourceToTargetPaths = immutableMap(sourceToTargetPaths);
        readSet = Objects.requireNonNull(readSet, "readSet");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Adoption plan expiry must be after creation");
        }
        if (totalSourceBytes < 0L) {
            throw new IllegalArgumentException("Adoption plan source bytes cannot be negative");
        }
        requireHash(sourceClosureHash, "source closure");
        requireHash(planHash, "plan");
        if (sourceResourceHashes.isEmpty() && disposition != IrisStructureAdoptionDisposition.BLOCKED) {
            throw new IllegalArgumentException("Applicable adoption plan requires source resources");
        }
    }

    public boolean canApply() {
        return disposition != IrisStructureAdoptionDisposition.BLOCKED
                && diagnostics.stream().noneMatch(IrisStructureAdoptionDiagnostic::blocking);
    }

    public boolean expiredAt(Instant instant) {
        return !Objects.requireNonNull(instant, "instant").isBefore(expiresAt);
    }

    public int resourceCount() {
        return sourceResourceHashes.size();
    }

    public long errorCount() {
        return diagnostics.stream().filter(IrisStructureAdoptionDiagnostic::blocking).count();
    }

    public long warningCount() {
        return diagnostics.stream().filter(diagnostic ->
                diagnostic.severity() == IrisStructureAdoptionDiagnostic.Severity.WARNING).count();
    }

    public List<String> summaryLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Plan " + planId + " -> " + disposition + " for " + targetStructure.value());
        lines.add(resourceCount() + " resources, " + totalSourceBytes + " bytes, "
                + errorCount() + " errors, " + warningCount() + " warnings");
        for (IrisStructureAdoptionDiagnostic diagnostic : diagnostics) {
            lines.add(diagnostic.summary());
        }
        return List.copyOf(lines);
    }

    private static List<IrisStructureAdoptionDiagnostic> sortedDiagnostics(
            List<IrisStructureAdoptionDiagnostic> values
    ) {
        ArrayList<IrisStructureAdoptionDiagnostic> ordered = new ArrayList<>(values);
        ordered.sort(IrisStructureAdoptionDiagnostic::compareTo);
        return List.copyOf(ordered);
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        Objects.requireNonNull(values, "plan values");
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    private static void requireHash(String value, String kind) {
        if (!StructureHash.isSha256(value)) {
            throw new IllegalArgumentException("Adoption " + kind + " hash must be SHA-256");
        }
    }
}
