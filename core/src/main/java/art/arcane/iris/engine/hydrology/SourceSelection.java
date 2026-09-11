package art.arcane.iris.engine.hydrology;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SourceSelection {
    final boolean surface;
    final List<SourceCandidate> candidates;
    final SourceAdmissionSelection admission;
    private final int[] requiredMinimums;
    final int maximumOptionalRejections;
    final int targetCount;
    final ArrayList<Integer> selectedCandidateIndices;
    final boolean[] selectedCandidates;
    private final boolean[] attemptedCandidates;
    private int rejectedOptionalCandidates;

    SourceSelection(
            boolean surface,
            List<SourceCandidate> candidates,
            SourceAdmissionSelection admission,
            int[] requiredMinimums,
            int maximumOptionalRejections
    ) {
        if (requiredMinimums.length != admission.quotas.areaLimits().length) {
            throw new IllegalArgumentException("Required source areas must match the admission bounds.");
        }
        for (int area = 0; area < requiredMinimums.length; area++) {
            if (requiredMinimums[area] < 0 || requiredMinimums[area] > admission.quotas.areaLimits()[area]) {
                throw new IllegalArgumentException("Required source minimum is outside the area bounds.");
            }
        }
        if (maximumOptionalRejections < 0) {
            throw new IllegalArgumentException("Maximum optional source rejections cannot be negative.");
        }
        this.surface = surface;
        this.candidates = List.copyOf(candidates);
        this.admission = admission;
        this.requiredMinimums = requiredMinimums.clone();
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
                        SourceAdmissionSelection.Quotas.uniform(new int[0], 0, Integer.MAX_VALUE)
                ),
                new int[]{0},
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

    boolean advanceAfterPublication(HydrologyCaveCourseFilter.Result result, HydrologySampledGrid grid) {
        AcceptedSources acceptedSources = acceptedSources(result);
        boolean changed = false;
        int selectedPosition = 0;
        while (selectedPosition < selectedCandidateIndices.size()) {
            int candidateIndex = selectedCandidateIndices.get(selectedPosition);
            SourceCandidate candidate = candidates.get(candidateIndex);
            HydrologyGridNode source = grid.node(candidate.nodeIndex());
            if (acceptedSources.nodeIds().contains(source.id())
                    || surface && acceptedSources.coordinates().contains(RiverFootprint.pack(source.x(), source.z()))) {
                if (candidate.required()) {
                    attemptedCandidates[candidateIndex] = true;
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
        while (true) {
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

    private AcceptedSources acceptedSources(HydrologyCaveCourseFilter.Result result) {
        RiverCourseType expectedType = surface ? RiverCourseType.SURFACE : RiverCourseType.UNDERGROUND;
        HashSet<Long> accepted = new HashSet<>();
        for (RiverCourse course : result.courses()) {
            if (course.type() == expectedType && course.sourceNodeId().isPresent()) {
                accepted.add(course.sourceNodeId().getAsLong());
            }
        }
        HashSet<Long> coordinates = new HashSet<>();
        if (surface) {
            for (DrainageNode node : result.nodes()) {
                if (accepted.contains(node.id())) {
                    coordinates.add(RiverFootprint.pack(node.x(), node.z()));
                }
            }
        }
        return new AcceptedSources(accepted, coordinates);
    }

    private int nextRequiredCandidate() {
        int[] selectedRequired = new int[requiredMinimums.length];
        for (int candidateIndex : selectedCandidateIndices) {
            if (candidates.get(candidateIndex).required()) {
                selectedRequired[admission.quotas.areaIndices()[candidateIndex]]++;
            }
        }
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            SourceCandidate candidate = candidates.get(candidateIndex);
            int area = admission.quotas.areaIndices()[candidateIndex];
            if (candidate.required()
                    && selectedRequired[area] < requiredMinimums[area]
                    && !selectedCandidates[candidateIndex]
                    && !attemptedCandidates[candidateIndex]
                    && admission.outletQuotaAvailable(candidateIndex, selectedCandidateIndices)) {
                return candidateIndex;
            }
        }
        return -1;
    }

    private int nextAdmittedCandidate() {
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

    private record AcceptedSources(Set<Long> nodeIds, Set<Long> coordinates) {
    }
}
