package art.arcane.iris.engine.hydrology;

import java.util.List;
import java.util.Objects;

record CrossTileResolvedOwner(
        HydrologyOwnerDraft draft,
        List<CrossTileRejectedCourse> observedRejections
) {
    CrossTileResolvedOwner {
        Objects.requireNonNull(draft, "draft");
        observedRejections = List.copyOf(Objects.requireNonNull(observedRejections, "observedRejections"));
    }

    CrossTileResolvedOwner withoutFootprintCompiler() {
        HydrologyOwnerDraft uncachedDraft = draft.withoutFootprintCompiler();
        return uncachedDraft == draft
                ? this
                : new CrossTileResolvedOwner(uncachedDraft, observedRejections);
    }
}
