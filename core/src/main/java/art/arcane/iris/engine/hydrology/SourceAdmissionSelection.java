package art.arcane.iris.engine.hydrology;

import java.util.List;
import java.util.function.IntPredicate;

final class SourceAdmissionSelection {
    final int targetCount;
    final List<Integer> selectedCandidateIndices;
    final boolean[] selectedCandidates;
    final boolean[] spacingRejectedCandidates;
    final boolean[] evaluatedCandidates;
    final IntPredicate globallyAdmitted;
    final int[] outletIndices;
    final int maximumCoursesPerOutlet;

    SourceAdmissionSelection(
            int targetCount,
            List<Integer> selectedCandidateIndices,
            boolean[] selectedCandidates,
            boolean[] spacingRejectedCandidates,
            boolean[] evaluatedCandidates,
            IntPredicate globallyAdmitted,
            int[] outletIndices,
            int maximumCoursesPerOutlet
    ) {
        if (outletIndices.length != selectedCandidates.length || maximumCoursesPerOutlet < 1) {
            throw new IllegalArgumentException("Source outlet admission bounds are invalid.");
        }
        this.targetCount = targetCount;
        this.selectedCandidateIndices = List.copyOf(selectedCandidateIndices);
        this.selectedCandidates = selectedCandidates;
        this.spacingRejectedCandidates = spacingRejectedCandidates;
        this.evaluatedCandidates = evaluatedCandidates;
        this.globallyAdmitted = globallyAdmitted;
        this.outletIndices = outletIndices;
        this.maximumCoursesPerOutlet = maximumCoursesPerOutlet;
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
        int outletIndex = outletIndices[candidateIndex];
        int selectedForOutlet = 0;
        for (int selectedIndex : selectedIndices) {
            if (outletIndices[selectedIndex] == outletIndex) {
                selectedForOutlet++;
            }
        }
        return selectedForOutlet < maximumCoursesPerOutlet;
    }
}
