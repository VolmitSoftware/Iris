package com.volmit.vanilla;

/** The decoration step in which the platform places a structure's pieces, in the platform's order. */
public enum StructureStep {
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
