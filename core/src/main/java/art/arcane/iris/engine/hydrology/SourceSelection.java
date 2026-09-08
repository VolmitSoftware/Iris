package art.arcane.iris.engine.hydrology;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SourceSelection {
    final boolean surface;
    final List<SourceCandidate> candidates;
    final SourceAdmissionSelection admission;
    final int guaranteedMinimum;
    final int maximumOptionalRejections;
    final int targetCount;
    final ArrayList<Integer> selectedCandidateIndices;
    final boolean[] selectedCandidates;
    final boolean[] attemptedCandidates;
    int rejectedOptionalCandidates;

    SourceSelection(
            boolean surface,
            List<SourceCandidate> candidates,
            SourceAdmissionSelection admission,
            int guaranteedMinimum,
            int maximumOptionalRejections
    ) {
        if (guaranteedMinimum < 0 || guaranteedMinimum > admission.selectedCandidateIndices().size()) {
            throw new IllegalArgumentException("Guaranteed source minimum is outside the admission bounds.");
        }
        if (maximumOptionalRejections < 0) {
            throw new IllegalArgumentException("Maximum optional source rejections cannot be negative.");
        }
        this.surface = surface;
        this.candidates = List.copyOf(candidates);
        this.admission = admission;
        this.guaranteedMinimum = guaranteedMinimum;
        this.maximumOptionalRejections = maximumOptionalRejections;
        this.selectedCandidateIndices = new ArrayList<>(admission.selectedCandidateIndices());
        this.targetCount = admission.targetCount();
        this.selectedCandidates = new boolean[candidates.size()];
        this.attemptedCandidates = new boolean[candidates.size()];
        for (int candidateIndex : selectedCandidateIndices) {
            selectedCandidates[candidateIndex] = true;
        }
    }

    static SourceSelection empty(boolean surface) {
        return new SourceSelection(
                surface,
                List.of(),
                new SourceAdmissionSelection(
                        0,
                        List.of(),
                        new boolean[0],
                        new boolean[0],
                        new boolean[0],
                        (int candidateIndex) -> false,
                        new int[0],
                        Integer.MAX_VALUE
                ),
                0,
                0
        );
    }

    List<Integer> selectedNodeIndices() {
        ArrayList<Integer> selectedNodes = new ArrayList<>(selectedCandidateIndices.size());
        for (int candidateIndex : selectedCandidateIndices) {
            selectedNodes.add(candidates.get(candidateIndex).nodeIndex());
        }
        return List.copyOf(selectedNodes);
    }

    int candidateCount() {
        return candidates.size();
    }

    boolean needsSurfaceFallback() {
        return surface && targetCount > 0 && selectedCandidateIndices.isEmpty();
    }

    boolean hasAcceptedSelection() {
        return !selectedCandidateIndices.isEmpty();
    }

    boolean advanceAfterPublication(List<RiverCourse> acceptedCourses, HydrologySampledGrid grid) {
        Set<Long> acceptedSourceNodeIds = acceptedSourceNodeIds(acceptedCourses);
        int acceptedRequired = 0;
        boolean changed = false;
        int selectedPosition = 0;
        while (selectedPosition < selectedCandidateIndices.size()) {
            int candidateIndex = selectedCandidateIndices.get(selectedPosition);
            SourceCandidate candidate = candidates.get(candidateIndex);
            long sourceNodeId = grid.node(candidate.nodeIndex()).id();
            if (acceptedSourceNodeIds.contains(sourceNodeId)) {
                if (candidate.required()) {
                    attemptedCandidates[candidateIndex] = true;
                    acceptedRequired++;
                }
                selectedPosition++;
                continue;
            }
            attemptedCandidates[candidateIndex] = true;
            if (!candidate.required()) {
                rejectedOptionalCandidates++;
            }
            selectedCandidateIndices.remove(selectedPosition);
            selectedCandidates[candidateIndex] = false;
            changed = true;
        }
        int requiredVacancies = Math.max(0, guaranteedMinimum - acceptedRequired);
        for (int vacancy = 0; vacancy < requiredVacancies; vacancy++) {
            int replacement = nextRequiredCandidate();
            if (replacement < 0) {
                break;
            }
            selectedCandidateIndices.add(replacement);
            selectedCandidates[replacement] = true;
            changed = true;
        }
        while (selectedCandidateIndices.size() < targetCount) {
            int replacement = nextAdmittedCandidate();
            if (replacement < 0) {
                break;
            }
            selectedCandidateIndices.add(replacement);
            selectedCandidates[replacement] = true;
            changed = true;
        }
        return changed;
    }

    Set<Long> acceptedSourceNodeIds(List<RiverCourse> acceptedCourses) {
        RiverCourseType expectedType = surface ? RiverCourseType.SURFACE : RiverCourseType.UNDERGROUND;
        HashSet<Long> accepted = new HashSet<>();
        for (RiverCourse course : acceptedCourses) {
            if (course.type() == expectedType && course.sourceNodeId().isPresent()) {
                accepted.add(course.sourceNodeId().getAsLong());
            }
        }
        return accepted;
    }

    int nextRequiredCandidate() {
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            SourceCandidate candidate = candidates.get(candidateIndex);
            if (candidate.required()
                    && !selectedCandidates[candidateIndex]
                    && !attemptedCandidates[candidateIndex]
                    && admission.outletQuotaAvailable(candidateIndex, selectedCandidateIndices)) {
                return candidateIndex;
            }
        }
        return -1;
    }

    int nextAdmittedCandidate() {
        if (rejectedOptionalCandidates >= maximumOptionalRejections) {
            return -1;
        }
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            if (!selectedCandidates[candidateIndex]
                    && !attemptedCandidates[candidateIndex]
                    && admission.outletQuotaAvailable(candidateIndex, selectedCandidateIndices)
                    && admission.admitted(candidateIndex)) {
                return candidateIndex;
            }
        }
        return -1;
    }

    void addFinalAdmissionDiagnostics(
            HydrologySampledGrid grid,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            if (selectedCandidates[candidateIndex] || attemptedCandidates[candidateIndex]) {
                continue;
            }
            SourceCandidate candidate = candidates.get(candidateIndex);
            HydrologySourcePlanner.addSourceDiagnostic(
                    grid.node(candidate.nodeIndex()),
                    surface,
                    candidate.stableId(),
                    admission.rejectedBySpacing(candidateIndex)
                            ? HydrologyCandidateRejection.SOURCE_SPACING
                            : HydrologyCandidateRejection.SOURCE_QUOTA,
                    diagnostics
            );
        }
    }
}
