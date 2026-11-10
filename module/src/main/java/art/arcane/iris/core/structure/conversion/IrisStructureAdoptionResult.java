package art.arcane.iris.core.structure.conversion;

import art.arcane.iris.core.structure.authoring.StructureWriteResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record IrisStructureAdoptionResult(
        Status status,
        UUID planId,
        List<IrisStructureAdoptionDiagnostic> diagnostics,
        Optional<IrisStructureAdoptionReceipt> receipt,
        Optional<StructureWriteResult> writeResult
) {
    public IrisStructureAdoptionResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(writeResult, "writeResult");
        diagnostics = List.copyOf(diagnostics);
        if (status == Status.APPLIED && receipt.isEmpty()) {
            throw new IllegalArgumentException("Applied adoption result requires a receipt");
        }
        if (status != Status.APPLIED && receipt.isPresent()) {
            throw new IllegalArgumentException("Failed adoption result cannot expose a receipt");
        }
    }

    public boolean successful() {
        return status == Status.APPLIED;
    }

    public List<String> summaryLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Adoption plan " + planId + ": " + status);
        for (IrisStructureAdoptionDiagnostic diagnostic : diagnostics) {
            lines.add(diagnostic.summary());
        }
        return List.copyOf(lines);
    }

    public enum Status {
        APPLIED,
        BLOCKED,
        EXPIRED,
        UNKNOWN_PLAN,
        STALE,
        FAILED
    }
}
