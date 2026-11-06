package com.volmit.mantle;

import com.volmit.resource.ResourceId;

import java.util.Objects;
import java.util.Optional;

/** A block the mantle holds as carved out: what fills the void once the chunk takes it over, and optionally the biome it shows.
 * Instances are immutable and the four plain fills are shared constants ({@link #of}), so a carver marking thousands of blocks
 * allocates nothing.
 *
 * @param fill what fills the void
 * @param biome a pack biome the void shows instead of the cave biome its column would get; empty for the usual one
 */
public record CarvedVoid(Fill fill, Optional<ResourceId> biome) {
    /** Cut open, and left to the dimension. */
    public static final CarvedVoid OPEN = new CarvedVoid(Fill.OPEN, Optional.empty());

    /** Filled with the dimension's fluid. */
    public static final CarvedVoid FLUID = new CarvedVoid(Fill.FLUID, Optional.empty());

    /** Filled with lava. */
    public static final CarvedVoid LAVA = new CarvedVoid(Fill.LAVA, Optional.empty());

    /** Filled with air, at any height. */
    public static final CarvedVoid AIR = new CarvedVoid(Fill.AIR, Optional.empty());

    public CarvedVoid {
        Objects.requireNonNull(fill, "fill");
        Objects.requireNonNull(biome, "biome");
    }

    /** The shared void of a fill, with no biome of its own. */
    public static CarvedVoid of(Fill fill) {
        return switch (Objects.requireNonNull(fill, "fill")) {
            case OPEN -> OPEN;
            case FLUID -> FLUID;
            case LAVA -> LAVA;
            case AIR -> AIR;
        };
    }

    /** This void's fill, showing {@code biome}. */
    public CarvedVoid withBiome(ResourceId biome) {
        return new CarvedVoid(fill, Optional.of(biome));
    }

    /** What fills a carved void. */
    public enum Fill {
        /** The solid is cut away and the dimension decides what is left: cave air, or lava at and below its lava height.
         * Air and fluid the terrain already has there stay.
         */
        OPEN,

        /** The dimension's fluid, replacing what the terrain has there. */
        FLUID,

        /** Lava, replacing what the terrain has there. */
        LAVA,

        /** Cave air at any height, replacing fluid and lava as well. */
        AIR
    }
}
