package com.volmit.vanilla;

/** A decoration step of the platform's chunk generation, in the platform's order: the step in which it places a structure's
 * pieces, or a placed feature such as an ore or a tree.
 */
public enum DecorationStep {
    RAW_GENERATION,
    LAKES,
    LOCAL_MODIFICATIONS,
    UNDERGROUND_STRUCTURES,
    SURFACE_STRUCTURES,
    STRONGHOLDS,
    UNDERGROUND_ORES,
    UNDERGROUND_DECORATION,
    FLUID_SPRINGS,
    VEGETAL_DECORATION,
    TOP_LAYER_MODIFICATION;

    /** Whether this is one of the steps the platform places its underground structures in. */
    public boolean isUnderground() {
        return this == UNDERGROUND_STRUCTURES || this == UNDERGROUND_DECORATION || this == STRONGHOLDS;
    }
}
