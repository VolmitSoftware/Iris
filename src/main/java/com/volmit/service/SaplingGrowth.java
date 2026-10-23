package com.volmit.service;

import com.volmit.block.BlockState;
import com.volmit.placement.PlacedBlock;
import com.volmit.resource.ResourceId;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Trees a module grows when a sapling or mushroom grows in a world generated with the pack, and which objects it
 * counts as trees. The host asks every provider in order when something grows and uses the first {@link Growth}; no
 * answer leaves the growth to the platform. The module decides what grows and the blocks it consists of; the host
 * clears the sapling, places the blocks on the right thread and tells listeners.
 */
@Service(Providers.ALL)
public interface SaplingGrowth {
    /**
     * The tree that replaces the growth at {@code sapling}, or empty to leave it to the platform. Reads the world around
     * the sapling through {@code surroundings}, which is only valid during the call and sees the saplings that are
     * growing as air.
     */
    Optional<Growth> grow(PackWorld world, Sapling sapling, Surroundings surroundings);

    /** The objects of the pack this module places as trees, for tools that tell trees from other objects. */
    TreeObjects treeObjects(PackWorld world);

    /**
     * What grows.
     *
     * @param at      where the growth happened: world X and Z, Y the height above the dimension's bottom
     * @param center  the middle of the plane of saplings that grow together, the same way
     * @param species the platform's name for the kind of tree that would grow, such as {@code TREE} or {@code JUNGLE}
     */
    record Sapling(BlockPosition at, BlockPosition center, String species) {
        public Sapling {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(species, "species");
        }
    }

    /** The world around a growing sapling, read where the tree is placed. X and Z are world coordinates, Y the height above the dimension's bottom. */
    interface Surroundings {
        /** The height of the topmost block at the column, ignoring fluids when {@code ignoreFluid}. */
        int highest(int x, int z, boolean ignoreFluid);

        BlockState blockAt(int x, int y, int z);
    }

    /**
     * The tree to place.
     *
     * @param object the id of the object the tree comes from, as {@link com.volmit.resource.PackResources#idOf} gives it
     *               for the object's key
     * @param blocks the blocks to place, in order, later ones over earlier ones
     */
    record Growth(ResourceId object, List<PlacedBlock> blocks) {
        public Growth {
            Objects.requireNonNull(object, "object");
            blocks = List.copyOf(blocks);
        }
    }

    /**
     * Objects a module places as trees, by the ids {@link com.volmit.resource.PackResources#idOf} gives their keys.
     *
     * @param objects    pack objects its placements mark as trees
     * @param procedural the objects its procedural trees generate, one per variant
     */
    record TreeObjects(Set<ResourceId> objects, Set<ResourceId> procedural) {
        public static final TreeObjects NONE = new TreeObjects(Set.of(), Set.of());

        public TreeObjects {
            objects = Set.copyOf(objects);
            procedural = Set.copyOf(procedural);
        }
    }
}
