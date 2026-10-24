package com.volmit.vanilla;

import com.volmit.config.Desc;

/** A decoration step of the platform's chunk generation, in the platform's order: the step in which it places a structure's
 * pieces, or a placed feature such as an ore or a tree.
 */
@Desc("A decoration step of the platform's chunk generation. Structures and placed features are grouped into these steps, which run in this order.")
public enum DecorationStep {
    @Desc("Raw generation - the earliest step, before lakes.")
    RAW_GENERATION,
    @Desc("Lakes.")
    LAKES,
    @Desc("Local modifications such as amethyst geodes, icebergs and dripstone clusters.")
    LOCAL_MODIFICATIONS,
    @Desc("Underground structures, and the features tagged to run with them.")
    UNDERGROUND_STRUCTURES,
    @Desc("Surface structures, and the features tagged to run with them.")
    SURFACE_STRUCTURES,
    @Desc("Strongholds.")
    STRONGHOLDS,
    @Desc("Underground ores. This is the step that carries vanilla and mod ore veins.")
    UNDERGROUND_ORES,
    @Desc("Underground decoration such as glow lichen, sculk patches and cave vegetation.")
    UNDERGROUND_DECORATION,
    @Desc("Fluid springs - the small water and lava spring features.")
    FLUID_SPRINGS,
    @Desc("Vegetal decoration - trees, grass, flowers, kelp and most surface plant life.")
    VEGETAL_DECORATION,
    @Desc("Top layer modification - freezing and snow placement.")
    TOP_LAYER_MODIFICATION;

    /** Whether this is one of the steps the platform places its underground structures in. */
    public boolean isUnderground() {
        return this == UNDERGROUND_STRUCTURES || this == UNDERGROUND_DECORATION || this == STRONGHOLDS;
    }
}
