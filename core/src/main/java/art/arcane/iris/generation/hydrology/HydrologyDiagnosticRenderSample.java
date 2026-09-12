package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record HydrologyDiagnosticRenderSample(
        int x,
        int z,
        List<HydrologyDiagnosticCandidate> candidates
) {
    private static final Comparator<HydrologyDiagnosticCandidate> CANDIDATE_ORDER = Comparator
            .comparing(HydrologyDiagnosticCandidate::kind)
            .thenComparing(HydrologyDiagnosticCandidate::rejection)
            .thenComparingLong(HydrologyDiagnosticCandidate::id);

    public HydrologyDiagnosticRenderSample {
        Objects.requireNonNull(candidates, "candidates");
        ArrayList<HydrologyDiagnosticCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(CANDIDATE_ORDER);
        candidates = List.copyOf(ordered);
    }

    public boolean present() {
        return !candidates.isEmpty();
    }
}
