/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.anchor.AnchorHeight;
import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.objects.ObjectRun;
import art.arcane.iris.builtin.prepare.TransformedBounds;
import art.arcane.iris.modules.placement.ObjectShape;
import art.arcane.iris.modules.placement.PlacementSettings;
import art.arcane.iris.modules.placement.PlacementStages;
import art.arcane.iris.modules.placement.PreparedPlacement;
import art.arcane.iris.modules.placement.RunningPlacement;
import art.arcane.iris.modules.placement.StageTerrain;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.stage.PlacementStageContext;

import java.util.Optional;

/**
 * Module {@code iris:anchor}: the height an object is placed at, requested by the caller, counted from the
 * bottom of the world, or sampled from the terrain the way the placement mode says. A surface-anchored
 * placement rejects a height its carving support forbids.
 */
public final class AnchorModule implements IrisModule {
    private static final String CARVING_ANCHOR = "carving-anchor";
    private static final int CARVING_CLEARANCE_BLOCKS = 4;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:anchor", "Placement anchor", PlacementModules.VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var graph = setup.stages();
        graph.define(PlacementStages.Anchor.HEIGHT)
                .requires(PlacementStages.Prepare.TRANSFORMS)
                .produce(AnchorModule::height);
        graph.define(PlacementStages.Anchor.FINAL)
                .requires(PlacementStages.Anchor.HEIGHT, PlacementStages.Validate.COLLISIONS)
                .produce(AnchorModule::finalHeight);
    }

    private static AnchorHeight height(PlacementStageContext context) {
        RunningPlacement placement = context.output(PlacementStages.Prepare.TRANSFORMS).placement();
        PreparedPlacement prepared = context.output(PlacementStages.Prepare.TRANSFORMS);
        PlacementSettings settings = prepared.settings();
        boolean structurePiece = prepared.mode().has(PlaceTrait.RAW_STAMP);
        if (!settings.fromBottom() && placement.automaticHeight()) {
            return new AnchorHeight(sampled(context, placement, prepared));
        }
        ObjectShape object = placement.object();
        int y = settings.fromBottom() ? object.height() + 1 + prepared.rotatedCenterY() : placement.requestedY();
        if (!settings.forcePlace() && !structurePiece && rejectsCarvingAnchor(prepared, placement.x(), y, placement.z())) {
            context.reject(CARVING_ANCHOR);
            return null;
        }
        if (placement.automaticHeight() || !settings.bottom() || structurePiece) {
            return new AnchorHeight(y);
        }
        y += Math.floorDiv(object.height(), 2);
        if (!settings.forcePlace() && settings.carvingSupport() != CarvingMode.CARVING_ONLY
                && rejectsCarvingAnchor(prepared, placement.x(), y, placement.z())) {
            context.reject(CARVING_ANCHOR);
            return null;
        }
        return new AnchorHeight(y);
    }

    private static AnchorHeight finalHeight(PlacementStageContext context) {
        int y = context.output(PlacementStages.Anchor.HEIGHT).y() + context.output(PlacementStages.Prepare.TRANSFORMS).randomY();
        context.output(PlacementStages.Prepare.TRANSFORMS).placement().anchoredAt(y);
        return new AnchorHeight(y);
    }

    private static int sampled(PlacementStageContext context, RunningPlacement placement, PreparedPlacement prepared) {
        return switch (prepared.mode().anchor()) {
            case CENTER -> checked(context, prepared, placement.x(), centerSurface(placement, prepared), placement.z());
            case SURFACE -> checked(context, prepared, placement.x(), surface(prepared, placement.x(), placement.z()), placement.z());
            case HIGHEST -> extremeSurface(context, placement, prepared, true, false);
            case HIGHEST_CORNER -> extremeSurface(context, placement, prepared, true, true);
            case LOWEST -> extremeSurface(context, placement, prepared, false, false);
            case LOWEST_CORNER -> extremeSurface(context, placement, prepared, false, true);
            case TRANSLATION -> prepared.rotatedCenterY();
            case REQUESTED -> placement.requestedY();
        };
    }

    /** The floor of the cave the object is placed in, otherwise the surface at its center. */
    private static int centerSurface(RunningPlacement placement, PreparedPlacement prepared) {
        Optional<ObjectRun.Cave> cave = placement.cave();
        if (cave.isPresent()) {
            return cave.get().surface() + prepared.rotatedCenterY();
        }
        return surface(prepared, placement.x(), placement.z());
    }

    private static int surface(PreparedPlacement prepared, int x, int z) {
        return prepared.terrain().highest(x, z, prepared.settings().underwater()) + prepared.rotatedCenterY();
    }

    private static int checked(PlacementStageContext context, PreparedPlacement prepared, int x, int y, int z) {
        if (!prepared.settings().forcePlace() && rejectsCarvingAnchor(prepared, x, y, z)) {
            context.reject(CARVING_ANCHOR);
        }
        return y;
    }

    /** The highest or lowest surface under the object's columns, or under its four corners only. */
    private static int extremeSurface(PlacementStageContext context, RunningPlacement placement, PreparedPlacement prepared,
                                      boolean highest, boolean cornersOnly) {
        TransformedBounds bounds = prepared.bounds();
        int minX = placement.x() + bounds.minX();
        int maxX = placement.x() + bounds.maxX();
        int minZ = placement.z() + bounds.minZ();
        int maxZ = placement.z() + bounds.maxZ();
        int xStep = cornersOnly ? Math.max(0, (maxX - minX) / 2) + 1 : 1;
        int zStep = cornersOnly ? Math.max(0, (maxZ - minZ) / 2) + 1 : 1;
        int y = highest ? -1 : Integer.MAX_VALUE;
        for (int x = minX; x <= maxX; x += xStep) {
            for (int z = minZ; z <= maxZ; z += zStep) {
                int column = surface(prepared, x, z);
                if (!prepared.settings().forcePlace() && rejectsCarvingAnchor(prepared, x, column, z)) {
                    context.reject(CARVING_ANCHOR);
                    return y;
                }
                y = highest ? Math.max(y, column) : Math.min(y, column);
            }
        }
        return y;
    }

    /** Whether the placement's carving support forbids anchoring at this block. */
    private static boolean rejectsCarvingAnchor(PreparedPlacement prepared, int x, int y, int z) {
        StageTerrain terrain = prepared.terrain();
        return switch (prepared.settings().carvingSupport()) {
            case SURFACE_ONLY -> terrain.isCarved(x, y, z);
            case CARVING_ONLY -> !carvedWithin(terrain, x, y, z);
            case ANYWHERE -> false;
        };
    }

    private static boolean carvedWithin(StageTerrain terrain, int x, int y, int z) {
        for (int depth = 0; depth < CARVING_CLEARANCE_BLOCKS; depth++) {
            if (terrain.isCarved(x, y - depth, z)) {
                return true;
            }
        }
        return false;
    }
}
