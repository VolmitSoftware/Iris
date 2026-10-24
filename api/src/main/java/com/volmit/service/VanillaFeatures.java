package com.volmit.service;

import com.volmit.resource.ResourceId;
import com.volmit.vanilla.DecorationStep;
import com.volmit.world.PackWorld;

import java.util.Optional;

/**
 * A module's say over the platform's own placed features in the pack's worlds: the ores, trees, plants, springs and other
 * decorations Minecraft, data packs and mods declare for a biome. By default the platform runs none of them over the pack's
 * terrain. The host asks every provider, in module id order: it runs the platform's feature pass in a world when any
 * provider turns it on with {@link #generates}, and then skips a decoration step or a feature that any provider refuses.
 *
 * <p>Every answer is decided from the world alone (its settings and resources), never from a position: the host asks once
 * per world, and once per world and step or feature, and keeps the answer while the world runs. Every method may be
 * called from any thread, concurrently.
 */
@Service(Providers.ALL)
public interface VanillaFeatures {
    /** Whether the platform runs its placed features over the terrain of {@code world}. False when the module has no part in it. */
    default boolean generates(PackWorld world) {
        return false;
    }

    /**
     * Whether the platform places the features of decoration step {@code step} in {@code world}: empty for a step of a newer
     * platform the API does not know yet. True when the module has no part in it.
     */
    default boolean generatesStep(PackWorld world, Optional<DecorationStep> step) {
        return true;
    }

    /** Whether the platform places the feature {@code feature}, such as {@code minecraft:ore_diamond}, in {@code world}. True when the module has no part in it. */
    default boolean generatesFeature(PackWorld world, ResourceId feature) {
        return true;
    }
}
