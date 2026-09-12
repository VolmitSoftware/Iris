package art.arcane.iris.generation.hydrology;

public enum HydrologyCandidateKind {
    SOURCE,
    OUTLET,
    DEEP_FLUID,
    POOL,
    /** A later course to an outlet that could not be joined to the outlet's main course. */
    TRIBUTARY,
    REGIONAL_SOURCE,
    COASTAL_CHANNEL
}
