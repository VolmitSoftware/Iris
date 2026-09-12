package art.arcane.iris.structure.jigsaw;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls how an Iris jigsaw assembly handles an optional connector branch that exhausts its primary pool and direct fallback without attaching a piece.")
public enum IrisJigsawBranchFailurePolicy {
    @Description("Fails the complete assembly when an optional connector branch cannot attach before the maximum depth.")
    FAIL_ASSEMBLY,

    @Description("Ends only the unresolved optional connector branch, matching vanilla jigsaw placement behavior.")
    TERMINATE_BRANCH
}
