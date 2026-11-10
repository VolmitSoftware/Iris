package com.volmit.service;

import com.volmit.entity.EntitySpec;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The entities a module spawns in the world over time: spawners the dimension, region and biomes list, and the spawners
 * markers in the world name. The host decides when and where to look, asks the providers, and spawns what the first
 * answer plans, after checking the conditions of the platform that come with it (light, the surface below, free space).
 * It tells the module how many entities appeared so that the module can keep its limits on how often a spawner fires.
 */
@Service(Providers.ALL)
public interface EntitySpawns {
    /** What the module plans to spawn in the chunk or at the marker, or empty to spawn nothing. */
    Optional<Plan> plan(PackWorld world, Site site);

    /** Called with the number of entities of {@code plan} that appeared, so the spawner's rate limits count them. */
    void spawned(PackWorld world, Plan plan, int count);

    /** The pack's entity {@code entity} as this module would spawn it, for tools that spawn one on request; empty for an unknown one. */
    Optional<EntitySpec> entity(PackWorld world, ResourceId entity);

    /**
     * What a marker in the world means for spawning; empty for a marker this module does not know. A marker's tag is a
     * key of the pack, as an object's marker block names it, and {@code tag} the id the pack gives it
     * ({@link com.volmit.resource.PackResources#idOf}).
     */
    Optional<MarkerRule> marker(PackWorld world, ResourceId tag);

    /** Where entities may spawn. */
    sealed interface Site permits Chunk, Marker {
    }

    /**
     * An ambient spawn in a chunk.
     *
     * @param chunkX          chunk coordinates
     * @param livingEntities  how many living entities the chunk already holds
     * @param initial         whether this is the spawn of a chunk that was just generated
     */
    record Chunk(int chunkX, int chunkZ, int livingEntities, boolean initial) implements Site {
    }

    /**
     * A spawn at a marker.
     *
     * @param position the marker's block: world X and Z, Y the height above the dimension's bottom
     * @param marker   the id of the marker's tag, as {@link EntitySpawns#marker} takes it
     * @param spawner  the pack's spawner chosen among the marker's
     * @param initial  whether the spawner's initial spawns are used rather than its ordinary ones
     */
    record Marker(BlockPosition position, ResourceId marker, ResourceId spawner, boolean initial) implements Site {
        public Marker {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(marker, "marker");
            Objects.requireNonNull(spawner, "spawner");
        }
    }

    /**
     * What a marker does.
     *
     * @param spawners       the pack's spawners at the marker
     * @param emptyAbove     whether it only counts with free space above it
     * @param removeOnChange whether it goes away when its block changes
     */
    record MarkerRule(List<ResourceId> spawners, boolean emptyAbove, boolean removeOnChange) {
        public MarkerRule {
            spawners = List.copyOf(spawners);
        }
    }

    /**
     * Entities to spawn, tried one after the other.
     *
     * @param spawner        the pack's spawner that planned them
     * @param chunkX         the chunk they spawn in, where the spawner's per-chunk limit counts them
     * @param exhaustsMarker whether the marker that led here is used up and should be removed
     * @param attempts       the spawns to try
     */
    record Plan(ResourceId spawner, int chunkX, int chunkZ, boolean exhaustsMarker, List<Attempt> attempts) {
        public Plan {
            Objects.requireNonNull(spawner, "spawner");
            attempts = List.copyOf(attempts);
        }
    }

    /**
     * One entity to spawn at a block. The host spawns it only if the block's light level lies in the range (when the
     * range is restricted) and, when {@code checkSurface}, the block below suits the entity's {@code surface} and the
     * space the entity needs is free. An entity of a fluid surface is always checked, and needs the block it spawns in
     * and the space it fills to be that fluid.
     *
     * @param at           world X and Z, Y the height above the dimension's bottom
     * @param entity       what to spawn
     * @param surface      the kind of ground or fluid the entity needs below it
     * @param minLight     lowest light level allowed, 0 for any
     * @param maxLight     highest light level allowed, 15 for any
     * @param checkSurface whether to check the surface and the free space
     */
    record Attempt(BlockPosition at, EntitySpec entity, Surface surface, int minLight, int maxLight, boolean checkSurface) {
        public Attempt {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(surface, "surface");
        }

        /** Whether the light level matters. */
        public boolean restrictsLight() {
            return minLight > 0 || maxLight < 15;
        }
    }

    /** The ground or fluid an entity spawns on. */
    enum Surface {
        /** Solid ground. */
        LAND,
        /** Ground animals spawn on, such as grass, dirt and podzol. */
        ANIMAL,
        /** Within water. */
        WATER,
        /** Solid ground or water. */
        OVERWORLD,
        /** Within lava. */
        LAVA;

        /** Whether the entity lives in the fluid, so the block it spawns in must be that fluid. */
        public boolean isFluid() {
            return this == WATER || this == LAVA;
        }
    }
}
