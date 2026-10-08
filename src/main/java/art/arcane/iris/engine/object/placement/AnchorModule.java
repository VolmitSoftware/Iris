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
import art.arcane.iris.builtin.anchor.AnchorStages;
import art.arcane.iris.builtin.prepare.PrepareStages;
import art.arcane.iris.builtin.validate.ValidateStages;
import art.arcane.iris.core.modules.IrisPlacement;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.terrain.carving.CarvingMode;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.StageGraph;

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
    public void install(StageGraph graph) {
        graph.define(AnchorStages.HEIGHT)
                .requires(PrepareStages.TRANSFORMS)
                .produce(AnchorModule::height);
        graph.define(AnchorStages.FINAL)
                .requires(AnchorStages.HEIGHT, ValidateStages.COLLISIONS)
                .produce(AnchorModule::finalHeight);
    }

    private static AnchorHeight height(PlacementStageContext context) {
        IrisPlacement placement = IrisPlacement.current();
        PreparedPlacement prepared = context.output(PrepareStages.TRANSFORMS);
        IrisObjectPlacement config = prepared.config();
        boolean structurePiece = prepared.mode() == ObjectPlaceMode.STRUCTURE_PIECE;
        if (!config.isFromBottom() && placement.requestedY() < 0) {
            return new AnchorHeight(sampled(context, placement, prepared));
        }
        IrisObject object = placement.object();
        int y = config.isFromBottom() ? object.getH() + 1 + prepared.rotatedCenterY() : placement.requestedY();
        if (!config.isForcePlace() && !structurePiece && rejectsCarvingAnchor(prepared, placement.x(), y, placement.z())) {
            context.reject(CARVING_ANCHOR);
            return new AnchorHeight(y);
        }
        if (placement.requestedY() < 0 || !config.isBottom() || structurePiece) {
            return new AnchorHeight(y);
        }
        y += Math.floorDiv(object.h, 2);
        if (!config.isForcePlace() && config.getCarvingSupport() != CarvingMode.CARVING_ONLY
                && rejectsCarvingAnchor(prepared, placement.x(), y, placement.z())) {
            context.reject(CARVING_ANCHOR);
        }
        return new AnchorHeight(y);
    }

    private static AnchorHeight finalHeight(PlacementStageContext context) {
        int y = context.output(AnchorStages.HEIGHT).y() + context.output(PrepareStages.TRANSFORMS).randomY();
        IrisPlacement.current().anchoredAt(y);
        return new AnchorHeight(y);
    }

    private static int sampled(PlacementStageContext context, IrisPlacement placement, PreparedPlacement prepared) {
        return switch (prepared.mode().anchor()) {
            case CENTER -> checked(context, prepared, placement.x(), centerSurface(placement, prepared), placement.z());
            case SURFACE -> checked(context, prepared, placement.x(), surface(placement, prepared, placement.x(), placement.z()), placement.z());
            case HIGHEST -> extremeSurface(context, placement, prepared, true, false);
            case HIGHEST_CORNER -> extremeSurface(context, placement, prepared, true, true);
            case LOWEST -> extremeSurface(context, placement, prepared, false, false);
            case LOWEST_CORNER -> extremeSurface(context, placement, prepared, false, true);
            case TRANSLATION -> prepared.rotatedCenterY();
            case REQUESTED -> placement.requestedY();
        };
    }

    /** The floor of the cave the object is placed in, otherwise the surface at its center. */
    private static int centerSurface(IrisPlacement placement, PreparedPlacement prepared) {
        if (placement.cave() != null) {
            return placement.cave().getSurface() + prepared.rotatedCenterY();
        }
        return surface(placement, prepared, placement.x(), placement.z());
    }

    private static int surface(IrisPlacement placement, PreparedPlacement prepared, int x, int z) {
        return prepared.terrain().getHighest(x, z, placement.object().getLoader(), prepared.config().isUnderwater())
                + prepared.rotatedCenterY();
    }

    private static int checked(PlacementStageContext context, PreparedPlacement prepared, int x, int y, int z) {
        if (!prepared.config().isForcePlace() && rejectsCarvingAnchor(prepared, x, y, z)) {
            context.reject(CARVING_ANCHOR);
        }
        return y;
    }

    /** The highest or lowest surface under the object's columns, or under its four corners only. */
    private static int extremeSurface(PlacementStageContext context, IrisPlacement placement, PreparedPlacement prepared,
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
                int column = surface(placement, prepared, x, z);
                if (!prepared.config().isForcePlace() && rejectsCarvingAnchor(prepared, x, column, z)) {
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
        IObjectPlacer terrain = prepared.terrain();
        return switch (prepared.config().getCarvingSupport()) {
            case SURFACE_ONLY -> terrain.isCarved(x, y, z);
            case CARVING_ONLY -> !carvedWithin(terrain, x, y, z);
            case ANYWHERE -> false;
        };
    }

    private static boolean carvedWithin(IObjectPlacer terrain, int x, int y, int z) {
        for (int depth = 0; depth < CARVING_CLEARANCE_BLOCKS; depth++) {
            if (terrain.isCarved(x, y - depth, z)) {
                return true;
            }
        }
        return false;
    }
}
