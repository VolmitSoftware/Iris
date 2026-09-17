package art.arcane.iris.engine.object.structure.jigsaw;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Controls how an Iris jigsaw assembly handles an optional connector branch that exhausts its primary pool and direct fallback without attaching a piece.")
public enum IrisJigsawBranchFailurePolicy {
    @Desc("Fails the complete assembly when an optional connector branch cannot attach before the maximum depth.")
    FAIL_ASSEMBLY,

    @Desc("Ends only the unresolved optional connector branch, matching vanilla jigsaw placement behavior.")
    TERMINATE_BRANCH
}
