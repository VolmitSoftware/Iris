package com.volmit.placement;

import com.volmit.geometry.BlockBox;

import java.util.Objects;

/**
 * One object a module asks the host to place through the pack's placement stages ({@link Placements#place}).
 *
 * @param request the object and where it goes
 * @param area    the blocks the object may reach around its anchor in any way it is placed; the host widens it by the
 *                reach the pack's placement stages declare, and the result bounds the placement's {@link PlacementAccess}.
 *                Reaching outside fails the placement
 * @param terrain the world the placement reads, and where the host writes the blocks when the placement commits
 * @param input   what the placing module hands its own stages, such as its placement settings; stages read it with
 *                {@link PlacementContext#input}, and stages of other modules do not know its type
 * @param seed    the seed the stages' random streams derive from; the same seed, stages and inputs give the same placement
 */
public record PlacementRun(PlacementRequest request, BlockBox area, PlacementTerrain terrain, Object input, long seed) {
    public PlacementRun {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(input, "input");
    }
}
