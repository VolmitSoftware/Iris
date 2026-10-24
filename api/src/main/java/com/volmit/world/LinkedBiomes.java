package com.volmit.world;

import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;

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
     * @param dimension what the owner's dimension itself declares, each type as a stack of at most its dimension entry,
     *                  for settings that name something the dimension declares, such as a band of its caves. The
     *                  dimension's regions and biomes are not in it: they are what the host is working out.
     */
    default Set<ResourceId> usedBiomes(PackResources pack, PackSettings dimension) {
        return Set.of();
    }
}
