package com.volmit.vanilla;

import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

/** The world and chunk a {@link VanillaStructureHook} places starts for. Valid only while the hook runs. */
public interface VanillaStructureStartContext {
    /** The world: its seed, pack resources and settings, and the state modules keep for it. */
    PackWorld world();

    /** The host's terrain over the chunk and the 8 blocks around it. */
    TerrainSampler terrain();
}
