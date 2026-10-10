package art.arcane.iris.engine.framework.nativefit;

/** The block questions a structure fit asks the platform about a state. */
public enum NativeFitTrait {
    /** Air. */
    AIR,
    /** What the platform counts as solid. */
    SOLID,
    /** Holds a fluid: a source, a flowing fluid or a waterlogged block. */
    FLUID,
    /** A log, wood, stem or hyphae, by tag or by name. */
    LOG,
    /** Leaves, by tag or by name. */
    LEAVES
}
