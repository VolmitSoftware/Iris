package com.volmit.config;

import com.volmit.IrisFeature;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;

import java.util.Collection;
import java.util.Set;

/**
 * Implemented by settings that make pack biomes part of a pack's layout besides the ones its regions list: the biome
 * that a cave band puts underground, or the target of a floating island. The host asks the settings of every dimension
 * and biome it reaches, without knowing the module behind them, so such a biome is loaded, registered and given its own
 * settings like any other. Biomes are named by their ids, {@link PackResources#idOf} of the keys the settings hold; ids
 * naming no biome of the pack are ignored.
 */
public interface LinkedBiomes {
    /**
     * Biomes that join the biome tree of every region the owner is part of, like the owner's children: the host registers
     * them with the region, so their terrain generators take part in the region's terrain.
     *
     * @param pack the pack the settings belong to, which turns their keys into ids
     */
    default Set<ResourceId> treeBiomes(PackResources pack) {
        return Set.of();
    }

    /**
     * Biomes the settings use besides, which a dimension reaches (their objects, decorators and settings take part in
     * generation) without joining the region's biome tree.
     *
     * @param pack      the pack the settings belong to, which turns their keys into ids
     * @param dimension the settings the owner's dimension declares, for settings that name something the dimension
     *                  declares, such as a band of its caves
     */
    default Set<ResourceId> usedBiomes(PackResources pack, Collection<IrisFeature> dimension) {
        return Set.of();
    }
}
