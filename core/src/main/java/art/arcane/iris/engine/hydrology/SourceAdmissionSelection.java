package art.arcane.iris.engine.hydrology;

import java.util.List;
import java.util.Arrays;
import java.util.function.IntPredicate;

final class SourceAdmissionSelection {
    final int targetCount;
    final List<Integer> selectedCandidateIndices;
    final boolean[] selectedCandidates;
    final boolean[] spacingRejectedCandidates;
    final boolean[] evaluatedCandidates;
    final IntPredicate globallyAdmitted;
    final Quotas quotas;

    SourceAdmissionSelection(
            int targetCount,
            List<Integer> selectedCandidateIndices,
            boolean[] selectedCandidates,
            boolean[] spacingRejectedCandidates,
            boolean[] evaluatedCandidates,
            IntPredicate globallyAdmitted,
            Quotas quotas
    ) {
        if (quotas.outletIndices().length != selectedCandidates.length) {
            throw new IllegalArgumentException("Source outlet admission bounds are invalid.");
        }
        this.targetCount = targetCount;
        this.selectedCandidateIndices = List.copyOf(selectedCandidateIndices);
        this.selectedCandidates = selectedCandidates;
        this.spacingRejectedCandidates = spacingRejectedCandidates;
        this.evaluatedCandidates = evaluatedCandidates;
        this.globallyAdmitted = globallyAdmitted;
        this.quotas = quotas;
    }

    List<Integer> selectedCandidateIndices() {
        return selectedCandidateIndices;
    }

    int targetCount() {
        return targetCount;
    }

    boolean selected(int candidateIndex) {
        return selectedCandidates[candidateIndex];
    }

    boolean rejectedBySpacing(int candidateIndex) {
        return spacingRejectedCandidates[candidateIndex];
    }

    boolean admitted(int candidateIndex) {
        if (!evaluatedCandidates[candidateIndex]) {
            evaluatedCandidates[candidateIndex] = true;
            spacingRejectedCandidates[candidateIndex] = !globallyAdmitted.test(candidateIndex);
        }
        return !spacingRejectedCandidates[candidateIndex];
    }

    boolean outletQuotaAvailable(int candidateIndex, List<Integer> selectedIndices) {
        int outletIndex = quotas.outletIndices()[candidateIndex];
        int areaIndex = quotas.areaIndices()[candidateIndex];
        int selectedForOutlet = 0;
        int selectedForArea = 0;
        for (int selectedIndex : selectedIndices) {
            if (quotas.outletIndices()[selectedIndex] == outletIndex) {
                selectedForOutlet++;
            }
            if (quotas.areaIndices()[selectedIndex] == areaIndex) {
                selectedForArea++;
            }
        }
        return selectedForOutlet < quotas.outletLimits()[candidateIndex]
                && selectedForArea < quotas.areaLimits()[areaIndex];
    }

    record Quotas(int[] outletIndices, int[] outletLimits, int[] areaIndices, int[] areaLimits) {
        Quotas {
            if (outletIndices.length != outletLimits.length || outletIndices.length != areaIndices.length) {
                throw new IllegalArgumentException("Source quota arrays must match candidate count.");
            }
        }

        static Quotas uniform(int[] outlets, int target, int maximumCoursesPerOutlet) {
            int[] limits = new int[outlets.length];
            Arrays.fill(limits, maximumCoursesPerOutlet);
            return new Quotas(outlets, limits, new int[outlets.length], new int[]{target});
        }
    }
}
