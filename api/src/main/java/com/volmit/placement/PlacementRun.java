package com.volmit.placement;

import com.volmit.geometry.BlockBox;

import java.util.Objects;
import java.util.Optional;

/**
 * One object a module asks the host to place through the pack's placement stages ({@link Placements#place}).
 *
 * @param request  the object and where it goes
 * @param name     the placement's display name, such as the object's key: it labels the placement in the host's logs and
 *                 names the source of the settings the pack declares on it
 * @param settings the placement as the pack wrote it, a JSON object, from which the host reads the settings that pack
 *                 modules declare at {@link com.volmit.config.Level#PLACEMENT} so that stages see them like any other
 *                 level's; the placement type's own fields ({@link com.volmit.FeatureRegistry#registerPlacementType}) are
 *                 not settings and are ignored. Empty for a placement the pack did not write. Equal text is read once
 * @param area     the blocks the object may reach around its anchor in any way it is placed; the host widens it by the
 *                 reach the pack's placement stages declare, and the result bounds the placement's {@link PlacementAccess}.
 *                 Reaching outside fails the placement
 * @param terrain  the world the placement reads, and where the host writes the blocks when the placement commits
 * @param input    what the placing module hands its own stages, such as its placement settings; stages read it with
 *                 {@link PlacementContext#input}, and stages of other modules do not know its type
 * @param seed     the seed the stages' random streams derive from; the same seed, stages and inputs give the same placement
 */
public record PlacementRun(PlacementRequest request, String name, Optional<String> settings, BlockBox area,
                           PlacementTerrain terrain, Object input, long seed) {
    public PlacementRun {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(input, "input");
    }
}
