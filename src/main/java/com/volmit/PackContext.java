package com.volmit;

import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;

/** A pack snapshot the host loaded, as {@link ModuleLifecycle#packLoaded} sees it. Do not retain it after the hook
 * returns: what a module builds from it for a world belongs in that world's state.
 */
public interface PackContext {
    /** The snapshot's resources. */
    PackResources resources();

    /** Every settings entry of every dimension, region and biome of the pack, as validators see them. */
    PackSettings settings();

    /** The log of the module the hook belongs to, labelled with its id. */
    ModuleLog log();
}
