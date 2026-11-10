package com.volmit.vanilla;

import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;

import java.util.Optional;

/** The world a {@link com.volmit.service.VanillaStructures} provider decides for. Valid only while the provider runs. */
public interface VanillaStructureContext {
    /** The resources of the world's pack, module resource kinds included. */
    PackResources resources();

    /** The module settings the world's dimension declares: its own, and every region and biome it can reach. */
    PackSettings settings();

    /** The world the provider decides for; empty when it decides before any world exists, such as for the setup of a dimension. */
    default Optional<PackWorld> world() {
        return Optional.empty();
    }
}
