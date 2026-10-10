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

package art.arcane.iris.engine.framework.structure.fit;

import art.arcane.iris.builtin.structure.StructureTerrainMode;
import art.arcane.iris.engine.object.placement.IrisObjectVacuum;
import com.volmit.ModuleLog;
import com.volmit.block.BlockState;
import com.volmit.vanilla.DecorationStep;
import com.volmit.vanilla.TemplateCells;
import com.volmit.vanilla.TerrainAdaptation;
import com.volmit.world.BlockPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntBinaryOperator;

/**
 * Fits the surface around the platform's surface structures before their pieces are placed. SOURCE replays the
 * platform's beard adaptation: the terrain within 12 blocks of the structure's ground planes bends toward them, never
 * more than 6 blocks. VACUUM raises the terrain to meet the processed solid foundations of rigid pieces, again over 12
 * blocks, without ever lowering it, and after the pieces are placed fills the gaps left under their bases
 * ({@link #repairVacuumFoundations}).
 */
final class SurfaceFitting {
    private static final double SURFACE_TERRAIN_FALLOFF = 2.0;
    private static final long SURFACE_TERRAIN_INFLUENCE_SCALE = 1_000_000L;
    private static final int SURFACE_TERRAIN_RADIUS = 12;
    private static final int MAX_SOURCE_SURFACE_DELTA = 6;
    static final int MAX_SURFACE_TEMPLATE_CELLS = 4_194_304;
    private static final int FOUNDATION_ANCHOR_STRENGTH = 2;
    private static final int JUNCTION_ANCHOR_STRENGTH = 1;
    private static final Set<String> WARNED_SOURCE_BUDGET = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_VACUUM_BUDGET = ConcurrentHashMap.newKeySet();

    private SurfaceFitting() {
    }

    /** The radius of the surface falloff, which a VACUUM start reaches beyond its pieces. */
    static int surfaceTerrainRadius() {
        return SURFACE_TERRAIN_RADIUS;
    }

    static VacuumFoundationPlan prepareSurfaceStructures(FitBlocks blocks, Box area, List<FitStart> starts,
                                                         IntBinaryOperator surfaceHeight, int maximumTemplateCells,
                                                         ModuleLog log) {
        if (starts.isEmpty()) {
            return VacuumFoundationPlan.EMPTY;
        }
        Objects.requireNonNull(surfaceHeight, "Surface structure terrain fitting requires an Iris height resolver");
        List<SurfaceAnchor> anchors = collectSourceSurfaceAnchors(blocks, area, starts, maximumTemplateCells, log);
        if (!anchors.isEmpty()) {
            fitSurfaceTerrain(blocks, area, anchors, surfaceHeight);
        }
        VacuumFootprint vacuum = collectVacuumFootprint(blocks, area, starts, maximumTemplateCells, log);
        if (!vacuum.anchors().isEmpty()) {
            fitVacuumTerrain(blocks, area, vacuum, surfaceHeight);
        }
        return VacuumFoundationPlan.create(vacuum.foundationBases(), vacuum.occupiedCells(), area);
    }

    static void repairVacuumFoundations(FitBlocks blocks, Box area, VacuumFoundationPlan plan) {
        if (plan == null || plan.foundationBases.isEmpty()) {
            return;
        }
        for (long packedBase : plan.foundationBases) {
            int baseX = TemplateFootprint.packedX(packedBase);
            int baseY = TemplateFootprint.packedY(packedBase);
            int baseZ = TemplateFootprint.packedZ(packedBase);
            if (!area.contains(baseX, baseY, baseZ)) {
                continue;
            }
            if (!blocks.isSolidBase(blocks.get(baseX, baseY, baseZ))) {
                continue;
            }
            int supportY = findVacuumFoundationSupport(blocks, area, baseX, baseY - 1, baseZ);
            if (supportY == Integer.MIN_VALUE || supportY == baseY - 1) {
                continue;
            }
            if (intersectsVacuumOccupancy(plan.occupiedCells, baseX, baseZ, supportY + 1, baseY - 1)) {
                continue;
            }
            BlockState fill = resolveSurfaceMaterials(blocks, baseX, baseZ, supportY, area.minY()).subsurface();
            for (int y = supportY + 1; y < baseY; y++) {
                BlockState existing = blocks.get(baseX, y, baseZ);
                if (blocks.isAir(existing) || blocks.holdsFluid(existing)) {
                    blocks.set(baseX, y, baseZ, fill);
                }
            }
        }
    }

    static boolean shouldPrepareSurfaceTerrain(TerrainAdaptation adaptation, DecorationStep step) {
        return step == DecorationStep.SURFACE_STRUCTURES
                && (adaptation == TerrainAdaptation.BEARD_THIN || adaptation == TerrainAdaptation.BEARD_BOX);
    }

    static int resolveSurfaceTarget(List<SurfaceAnchor> anchors, int worldX, int worldZ, int originalY) {
        return resolveSurface(anchors, worldX, worldZ, originalY).targetY();
    }

    private static SurfaceResolution resolveSurface(List<SurfaceAnchor> anchors, int worldX, int worldZ, int originalY) {
        int localTargetY = originalY;
        SurfaceAnchor selectedLocal = null;
        long totalInfluence = 0L;
        long weightedTargetY = 0L;
        long maximumInfluence = 0L;
        for (SurfaceAnchor anchor : anchors) {
            int outX = IrisObjectVacuum.outset(worldX, anchor.minX(), anchor.maxX());
            int outZ = IrisObjectVacuum.outset(worldZ, anchor.minZ(), anchor.maxZ());
            boolean containsColumn = outX == 0 && outZ == 0;
            long distanceSquared = (long) outX * outX + (long) outZ * outZ;
            long radiusSquared = (long) SURFACE_TERRAIN_RADIUS * SURFACE_TERRAIN_RADIUS;
            if (distanceSquared > radiusSquared) {
                continue;
            }
            double distance = Math.sqrt(distanceSquared);
            double factor = Math.pow(1D - distance / SURFACE_TERRAIN_RADIUS, SURFACE_TERRAIN_FALLOFF);
            if (factor <= 0D) {
                continue;
            }
            int boundedTargetY = boundedSurfaceTarget(originalY, anchor.meetY());
            if (containsColumn) {
                if (precedes(anchor, selectedLocal)) {
                    localTargetY = boundedTargetY;
                    selectedLocal = anchor;
                }
                continue;
            }
            long influence = Math.round(factor * SURFACE_TERRAIN_INFLUENCE_SCALE);
            if (influence <= 0L) {
                continue;
            }
            long weightedInfluence = influence * Math.max(1, anchor.strength());
            totalInfluence += weightedInfluence;
            weightedTargetY += weightedInfluence * boundedTargetY;
            maximumInfluence = Math.max(maximumInfluence, influence);
        }
        if (selectedLocal != null) {
            return new SurfaceResolution(localTargetY,
                    selectedLocal.strength() > 1 && localTargetY == selectedLocal.meetY());
        }
        if (totalInfluence == 0L) {
            return new SurfaceResolution(originalY, false);
        }
        double blendedTargetY = weightedTargetY / (double) totalInfluence;
        double factor = maximumInfluence / (double) SURFACE_TERRAIN_INFLUENCE_SCALE;
        return new SurfaceResolution(blendSurfaceTarget(originalY, blendedTargetY, factor), false);
    }

    private static int boundedSurfaceTarget(int originalY, int meetY) {
        int delta = Math.max(-MAX_SOURCE_SURFACE_DELTA, Math.min(MAX_SOURCE_SURFACE_DELTA, meetY - originalY));
        return originalY + delta;
    }

    private static int blendSurfaceTarget(int originalY, double targetY, double factor) {
        double delta = (targetY - originalY) * factor;
        int magnitude = (int) Math.round(Math.abs(delta));
        return originalY + (delta < 0D ? -magnitude : magnitude);
    }

    private static boolean precedes(SurfaceAnchor candidate, SurfaceAnchor selected) {
        if (selected == null) {
            return true;
        }
        if (candidate.strength() != selected.strength()) {
            return candidate.strength() > selected.strength();
        }
        if (candidate.meetY() != selected.meetY()) {
            return candidate.meetY() < selected.meetY();
        }
        if (candidate.minX() != selected.minX()) {
            return candidate.minX() < selected.minX();
        }
        if (candidate.minZ() != selected.minZ()) {
            return candidate.minZ() < selected.minZ();
        }
        if (candidate.maxX() != selected.maxX()) {
            return candidate.maxX() < selected.maxX();
        }
        if (candidate.maxZ() != selected.maxZ()) {
            return candidate.maxZ() < selected.maxZ();
        }
        return false;
    }

    private static List<SurfaceAnchor> collectSourceSurfaceAnchors(FitBlocks blocks, Box area, List<FitStart> starts,
                                                                   int maximumTemplateCells, ModuleLog log) {
        Box influenceArea = area.widened(SURFACE_TERRAIN_RADIUS);
        List<SurfaceAnchor> anchors = new ArrayList<>();
        Set<Object> seenStarts = Collections.newSetFromMap(new IdentityHashMap<>());
        for (FitStart start : starts) {
            if (!requiresSourceSurfaceTerrain(start) || !seenStarts.add(start.key())) {
                continue;
            }
            TemplateCellBudget budget = new TemplateCellBudget(maximumTemplateCells);
            boolean budgetExceeded = false;
            for (FitPiece piece : start.pieces()) {
                if (piece.isJigsaw()) {
                    if (!budgetExceeded && piece.box().intersectsHorizontally(influenceArea)) {
                        try {
                            List<SurfaceAnchor> pieceAnchors = new ArrayList<>();
                            addProcessedSurfaceAnchors(blocks, influenceArea, piece, budget, pieceAnchors);
                            anchors.addAll(pieceAnchors);
                        } catch (TemplateBudgetExceeded ignored) {
                            budgetExceeded = true;
                            warnBudget(WARNED_SOURCE_BUDGET, start, "SOURCE",
                                    "skipping remaining processed footprints for this start", log);
                        }
                    }
                    for (BlockPosition junction : piece.junctions()) {
                        anchors.add(new SurfaceAnchor(junction.x(), junction.x(), junction.z(), junction.z(),
                                junction.y() - 1, JUNCTION_ANCHOR_STRENGTH));
                    }
                    continue;
                }
                Box bounds = piece.box();
                anchors.add(surfaceAnchor(bounds, bounds.minY(), FOUNDATION_ANCHOR_STRENGTH));
            }
        }
        return List.copyOf(anchors);
    }

    private static void addProcessedSurfaceAnchors(FitBlocks blocks, Box influenceArea, FitPiece piece,
                                                   TemplateCellBudget budget, List<SurfaceAnchor> anchors) {
        TemplateCells cells = piece.cells(influenceArea);
        budget.consume(cells.readBlocks());
        if (!cells.resolved()) {
            return;
        }
        int maximumFoundationY = piece.groundY();
        for (TemplateFootprint.Cell cell : TemplateFootprint.lowestSolidCells(cells, piece, blocks).values()) {
            budget.consume(1);
            if (piece.isRigid() && cell.y() > maximumFoundationY) {
                continue;
            }
            int groundY = piece.isRigid() ? maximumFoundationY : cell.y();
            Box column = new Box(cell.x(), groundY, cell.z(), cell.x(), groundY, cell.z());
            anchors.add(surfaceAnchor(column, groundY, FOUNDATION_ANCHOR_STRENGTH));
        }
    }

    private static void warnBudget(Set<String> warned, FitStart start, String mode, String consequence, ModuleLog log) {
        if (warned.add(start.structureId())) {
            log.warn("Native structure " + mode + " fitting for '" + start.structureId()
                    + "' exceeded its bounded template budget; " + consequence);
        }
    }

    private static VacuumFootprint collectVacuumFootprint(FitBlocks blocks, Box area, List<FitStart> starts,
                                                          int maximumTemplateCells, ModuleLog log) {
        Box influenceArea = area.widened(SURFACE_TERRAIN_RADIUS);
        Map<Long, VacuumAnchor> anchors = new HashMap<>();
        Map<Long, Integer> columnCaps = new HashMap<>();
        Set<Long> foundationBases = new HashSet<>();
        Set<Long> occupiedCells = new HashSet<>();
        Set<Object> seenStarts = Collections.newSetFromMap(new IdentityHashMap<>());
        for (FitStart start : starts) {
            if (start.terrain().resolvedMode() != StructureTerrainMode.VACUUM || !seenStarts.add(start.key())) {
                continue;
            }
            Map<Long, VacuumAnchor> targetAnchors = new HashMap<>();
            Map<Long, Integer> targetCaps = new HashMap<>();
            Set<Long> targetFoundationBases = new HashSet<>();
            Set<Long> targetOccupiedCells = new HashSet<>();
            TemplateCellBudget budget = new TemplateCellBudget(maximumTemplateCells);
            try {
                for (FitPiece piece : start.pieces()) {
                    if (!piece.box().intersectsHorizontally(influenceArea) || !piece.isRigid()) {
                        continue;
                    }
                    addVacuumJunctionAnchors(anchors, influenceArea, piece);
                    TemplateCells cells = piece.cells(influenceArea);
                    budget.consume(cells.readBlocks());
                    if (!cells.resolved()) {
                        continue;
                    }
                    for (int cell = 0; cell < cells.size(); cell++) {
                        targetOccupiedCells.add(TemplateFootprint.packedPosition(
                                cells.x(cell), piece.cellY(cells, cell), cells.z(cell)));
                    }
                    int groundY = piece.groundY() - 1;
                    for (TemplateFootprint.Cell cell : TemplateFootprint.lowestCells(cells, piece).values()) {
                        budget.consume(1);
                        long column = TemplateFootprint.columnKey(cell.x(), cell.z());
                        targetCaps.merge(column, Math.min(cell.y(), groundY), Math::min);
                        if (cell.blocker() || cell.y() > groundY + 1 || !blocks.isSolidBase(cell.state())) {
                            continue;
                        }
                        addVacuumAnchor(targetAnchors, cell.x(), cell.z(),
                                new VacuumAnchor(cell.y() - 1, FOUNDATION_ANCHOR_STRENGTH));
                        targetFoundationBases.add(TemplateFootprint.packedPosition(cell.x(), cell.y(), cell.z()));
                    }
                }
            } catch (TemplateBudgetExceeded ignored) {
                warnBudget(WARNED_VACUUM_BUDGET, start, "VACUUM", "using jigsaw junction smoothing for oversized starts", log);
                continue;
            }
            targetAnchors.forEach((column, anchor) -> anchors.merge(column, anchor, SurfaceFitting::preferredVacuumAnchor));
            targetCaps.forEach((column, cap) -> columnCaps.merge(column, cap, Math::min));
            foundationBases.addAll(targetFoundationBases);
            occupiedCells.addAll(targetOccupiedCells);
        }
        return new VacuumFootprint(Map.copyOf(anchors), Map.copyOf(columnCaps),
                Set.copyOf(foundationBases), Set.copyOf(occupiedCells));
    }

    private static void addVacuumJunctionAnchors(Map<Long, VacuumAnchor> anchors, Box influenceArea, FitPiece piece) {
        for (BlockPosition junction : piece.junctions()) {
            if (junction.x() < influenceArea.minX() || junction.x() > influenceArea.maxX()
                    || junction.z() < influenceArea.minZ() || junction.z() > influenceArea.maxZ()) {
                continue;
            }
            addVacuumAnchor(anchors, junction.x(), junction.z(),
                    new VacuumAnchor(junction.y() - 1, JUNCTION_ANCHOR_STRENGTH));
        }
    }

    private static void addVacuumAnchor(Map<Long, VacuumAnchor> anchors, int x, int z, VacuumAnchor candidate) {
        anchors.merge(TemplateFootprint.columnKey(x, z), candidate, SurfaceFitting::preferredVacuumAnchor);
    }

    private static VacuumAnchor preferredVacuumAnchor(VacuumAnchor first, VacuumAnchor second) {
        if (first.strength() != second.strength()) {
            return first.strength() > second.strength() ? first : second;
        }
        return first.surfaceY() <= second.surfaceY() ? first : second;
    }

    static SurfaceAnchor surfaceAnchor(Box bounds, int groundY, int strength) {
        return new SurfaceAnchor(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ(), groundY - 1, strength);
    }

    /** Whether the start's pieces need the surface fitted around them: VACUUM, or SOURCE with a beard adaptation. */
    static boolean requiresSurfaceTerrain(FitStart start) {
        return start.terrain().resolvedMode() == StructureTerrainMode.VACUUM || requiresSourceSurfaceTerrain(start);
    }

    private static boolean requiresSourceSurfaceTerrain(FitStart start) {
        return start.terrain().resolvedMode() == StructureTerrainMode.SOURCE
                && shouldPrepareSurfaceTerrain(start.adaptation(), start.step());
    }

    private static void fitSurfaceTerrain(FitBlocks blocks, Box area, List<SurfaceAnchor> anchors,
                                          IntBinaryOperator surfaceHeight) {
        int width = area.xSpan();
        int depth = area.zSpan();
        int[] originalHeights = new int[width * depth];
        int[] targetHeights = new int[width * depth];
        boolean[] rigidBaseSupport = new boolean[width * depth];
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                int column = area.columnIndex(x, z);
                int originalY = Math.max(area.minY(), Math.min(area.maxY(), surfaceHeight.applyAsInt(x, z)));
                SurfaceResolution resolution = resolveSurface(anchors, x, z, originalY);
                originalHeights[column] = originalY;
                targetHeights[column] = Math.max(area.minY(), Math.min(area.maxY(), resolution.targetY()));
                rigidBaseSupport[column] = resolution.rigidBaseSupport();
            }
        }
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                int column = area.columnIndex(x, z);
                applySurfaceColumn(blocks, x, z, originalHeights[column], targetHeights[column], area.minY(), area.maxY(),
                        rigidBaseSupport[column]);
            }
        }
    }

    private static void fitVacuumTerrain(FitBlocks blocks, Box area, VacuumFootprint footprint,
                                         IntBinaryOperator surfaceHeight) {
        int width = area.xSpan();
        int depth = area.zSpan();
        int[] originalHeights = new int[width * depth];
        int[] targetHeights = new int[width * depth];
        boolean[] rigidBaseSupport = new boolean[width * depth];
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                int column = area.columnIndex(x, z);
                int nominalY = Math.max(area.minY(), Math.min(area.maxY(), surfaceHeight.applyAsInt(x, z)));
                long columnKey = TemplateFootprint.columnKey(x, z);
                VacuumAnchor local = footprint.anchors().get(columnKey);
                Integer cap = footprint.columnCaps().get(columnKey);
                boolean suppressed = local != null && local.strength() > 1 && cap != null && cap < local.surfaceY();
                int scanStartY = local != null && local.strength() > 1 && !suppressed
                        ? Math.max(area.minY(), Math.min(area.maxY(), local.surfaceY()))
                        : nominalY;
                int originalY = resolveVacuumSurfaceHeight(blocks, x, z, scanStartY, area.minY());
                SurfaceResolution resolution = resolveVacuumSurface(footprint, x, z, originalY);
                originalHeights[column] = originalY;
                targetHeights[column] = Math.max(originalY, Math.min(area.maxY(), resolution.targetY()));
                rigidBaseSupport[column] = resolution.rigidBaseSupport() && targetHeights[column] > originalY;
            }
        }
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                int column = area.columnIndex(x, z);
                applySurfaceColumn(blocks, x, z, originalHeights[column], targetHeights[column], area.minY(), area.maxY(),
                        rigidBaseSupport[column]);
            }
        }
    }

    private static int resolveVacuumSurfaceHeight(FitBlocks blocks, int x, int z, int nominalY, int worldMinY) {
        for (int y = nominalY; y >= worldMinY; y--) {
            if (blocks.isTerrain(blocks.get(x, y, z))) {
                return y;
            }
        }
        return nominalY;
    }

    private static int findVacuumFoundationSupport(FitBlocks blocks, Box area, int x, int startY, int z) {
        if (startY < area.minY()) {
            return Integer.MIN_VALUE;
        }
        int maximumY = Math.min(area.maxY(), startY);
        for (int y = maximumY; y >= area.minY(); y--) {
            BlockState state = blocks.get(x, y, z);
            if (blocks.isTerrain(state)) {
                return y;
            }
            if (!blocks.isAir(state) && !blocks.holdsFluid(state)) {
                return Integer.MIN_VALUE;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean intersectsVacuumOccupancy(Set<Long> occupiedCells, int x, int z, int minimumY, int maximumY) {
        for (int y = minimumY; y <= maximumY; y++) {
            if (occupiedCells.contains(TemplateFootprint.packedPosition(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    private static SurfaceResolution resolveVacuumSurface(VacuumFootprint footprint, int worldX, int worldZ, int originalY) {
        long column = TemplateFootprint.columnKey(worldX, worldZ);
        Integer cap = footprint.columnCaps().get(column);
        VacuumAnchor local = footprint.anchors().get(column);
        if (local != null && local.strength() > 1) {
            int localTargetY = cap == null ? local.surfaceY() : Math.min(local.surfaceY(), cap);
            if (localTargetY <= originalY) {
                return new SurfaceResolution(originalY, false);
            }
            return new SurfaceResolution(localTargetY, localTargetY == local.surfaceY());
        }
        long totalInfluence = 0L;
        long weightedSurfaceY = 0L;
        long maximumInfluence = 0L;
        int radiusSquared = SURFACE_TERRAIN_RADIUS * SURFACE_TERRAIN_RADIUS;
        for (int offsetX = -SURFACE_TERRAIN_RADIUS; offsetX <= SURFACE_TERRAIN_RADIUS; offsetX++) {
            for (int offsetZ = -SURFACE_TERRAIN_RADIUS; offsetZ <= SURFACE_TERRAIN_RADIUS; offsetZ++) {
                int horizontalDistanceSquared = offsetX * offsetX + offsetZ * offsetZ;
                if (horizontalDistanceSquared > radiusSquared) {
                    continue;
                }
                VacuumAnchor anchor = footprint.anchors().get(
                        TemplateFootprint.columnKey(worldX + offsetX, worldZ + offsetZ));
                if (anchor == null || anchor.surfaceY() <= originalY) {
                    continue;
                }
                int verticalDistance = Math.abs(anchor.surfaceY() + 1 - originalY);
                long distanceSquared = horizontalDistanceSquared + (long) verticalDistance * verticalDistance;
                double factor = 0D;
                if (distanceSquared <= radiusSquared) {
                    factor = surfaceFactor(Math.sqrt(distanceSquared));
                }
                if (anchor.strength() > 1 && verticalDistance > SURFACE_TERRAIN_RADIUS) {
                    double rescueProgress = Math.min(1D,
                            (verticalDistance - SURFACE_TERRAIN_RADIUS) / (double) SURFACE_TERRAIN_RADIUS);
                    double rescueWeight = rescueProgress * rescueProgress * (3D - 2D * rescueProgress);
                    factor = Math.max(factor, surfaceFactor(Math.sqrt(horizontalDistanceSquared)) * rescueWeight);
                }
                long influence = Math.round(factor * SURFACE_TERRAIN_INFLUENCE_SCALE);
                if (influence <= 0L) {
                    continue;
                }
                long weightedInfluence = influence * Math.max(1, anchor.strength());
                totalInfluence += weightedInfluence;
                weightedSurfaceY += weightedInfluence * anchor.surfaceY();
                maximumInfluence = Math.max(maximumInfluence, influence);
            }
        }
        if (totalInfluence == 0L) {
            return new SurfaceResolution(originalY, false);
        }
        double blendedSurfaceY = weightedSurfaceY / (double) totalInfluence;
        double factor = maximumInfluence / (double) SURFACE_TERRAIN_INFLUENCE_SCALE;
        int targetY = (int) Math.round(originalY + ((blendedSurfaceY - originalY) * factor));
        if (cap != null) {
            targetY = Math.min(targetY, cap);
        }
        return new SurfaceResolution(Math.max(originalY, targetY), false);
    }

    private static double surfaceFactor(double distance) {
        return Math.pow(1D - distance / SURFACE_TERRAIN_RADIUS, SURFACE_TERRAIN_FALLOFF);
    }

    static void applySurfaceColumn(FitBlocks blocks, int x, int z, int originalY, int targetY, int worldMinY,
                                   int worldMaxY, boolean requireRigidBaseSupport) {
        if (targetY == originalY) {
            if (requireRigidBaseSupport) {
                ensureRigidBaseTerrain(blocks, x, z, targetY, worldMinY);
            }
            return;
        }
        SurfaceMaterials materials = resolveSurfaceMaterials(blocks, x, z, originalY, worldMinY);
        if (targetY < originalY) {
            BlockState clearedState = clearSurfaceDecorationAndResolveFill(blocks, x, z, originalY, worldMaxY);
            for (int y = originalY; y > targetY; y--) {
                blocks.set(x, y, z, clearedState);
            }
            blocks.set(x, targetY, z, materials.surface());
            if (requireRigidBaseSupport) {
                ensureRigidBaseTerrain(blocks, x, z, targetY, worldMinY, materials);
            }
            return;
        }
        for (int y = originalY + 1; y < targetY; y++) {
            if (!blocks.isTree(blocks.get(x, y, z))) {
                blocks.set(x, y, z, materials.subsurface());
            }
        }
        if (!blocks.isTree(blocks.get(x, targetY, z))) {
            blocks.set(x, targetY, z, materials.surface());
        }
        if (requireRigidBaseSupport) {
            ensureRigidBaseTerrain(blocks, x, z, targetY, worldMinY, materials);
        }
    }

    private static void ensureRigidBaseTerrain(FitBlocks blocks, int x, int z, int targetY, int worldMinY) {
        int supportY = targetY - 1;
        boolean targetIsTerrain = blocks.isTerrain(blocks.get(x, targetY, z));
        boolean supportIsTerrain = supportY < worldMinY || blocks.isTerrain(blocks.get(x, supportY, z));
        if (targetIsTerrain && supportIsTerrain) {
            return;
        }
        SurfaceMaterials materials = resolveSurfaceMaterials(blocks, x, z, targetY, worldMinY);
        ensureRigidBaseTerrain(blocks, x, z, targetY, worldMinY, materials);
    }

    private static void ensureRigidBaseTerrain(FitBlocks blocks, int x, int z, int targetY, int worldMinY,
                                               SurfaceMaterials materials) {
        int supportY = targetY - 1;
        if (!blocks.isTerrain(blocks.get(x, targetY, z))) {
            blocks.set(x, targetY, z, materials.surface());
        }
        if (supportY < worldMinY) {
            return;
        }
        if (!blocks.isTerrain(blocks.get(x, supportY, z))) {
            blocks.set(x, supportY, z, materials.subsurface());
        }
    }

    private static BlockState clearSurfaceDecorationAndResolveFill(FitBlocks blocks, int x, int z, int originalY,
                                                                   int worldMaxY) {
        for (int y = originalY + 1; y <= worldMaxY; y++) {
            BlockState state = blocks.get(x, y, z);
            if (blocks.isAir(state)) {
                return FitBlocks.AIR;
            }
            if (blocks.holdsFluid(state)) {
                BlockState fluid = blocks.fluidOf(state);
                if (!state.equals(fluid)) {
                    blocks.set(x, y, z, fluid);
                }
                return fluid;
            }
            if (blocks.isSolid(state) || blocks.isTree(state)) {
                return FitBlocks.AIR;
            }
            blocks.set(x, y, z, FitBlocks.AIR);
        }
        return FitBlocks.AIR;
    }

    private static SurfaceMaterials resolveSurfaceMaterials(FitBlocks blocks, int x, int z, int originalY, int worldMinY) {
        BlockState surface = blocks.get(x, originalY, z);
        BlockState subsurface = null;
        for (int y = originalY - 1; y >= worldMinY; y--) {
            BlockState candidate = blocks.get(x, y, z);
            if (blocks.isTerrain(candidate)) {
                subsurface = candidate;
                break;
            }
        }
        if (subsurface == null) {
            subsurface = blocks.isTerrain(surface) ? surface : FitBlocks.STONE;
        }
        if (!blocks.isTerrain(surface)) {
            surface = subsurface;
        }
        return new SurfaceMaterials(surface, subsurface);
    }

    record SurfaceAnchor(int minX, int maxX, int minZ, int maxZ, int meetY, int strength) {
    }

    private record SurfaceMaterials(BlockState surface, BlockState subsurface) {
    }

    private record SurfaceResolution(int targetY, boolean rigidBaseSupport) {
    }

    private record VacuumFootprint(Map<Long, VacuumAnchor> anchors, Map<Long, Integer> columnCaps,
                                   Set<Long> foundationBases, Set<Long> occupiedCells) {
    }

    private record VacuumAnchor(int surfaceY, int strength) {
    }

    /** The foundation bases VACUUM raised the terrain to, kept until the pieces are placed for the repair. */
    static final class VacuumFoundationPlan {
        static final VacuumFoundationPlan EMPTY = new VacuumFoundationPlan(List.of(), Set.of());

        private final List<Long> foundationBases;
        private final Set<Long> occupiedCells;

        private VacuumFoundationPlan(List<Long> foundationBases, Set<Long> occupiedCells) {
            this.foundationBases = foundationBases;
            this.occupiedCells = occupiedCells;
        }

        private static VacuumFoundationPlan create(Set<Long> packedBases, Set<Long> packedOccupancy, Box area) {
            if (packedBases.isEmpty()) {
                return EMPTY;
            }
            List<Long> bases = new ArrayList<>(packedBases.size());
            Set<Long> columns = new HashSet<>();
            for (long packedBase : packedBases) {
                int x = TemplateFootprint.packedX(packedBase);
                int z = TemplateFootprint.packedZ(packedBase);
                if (!area.contains(x, TemplateFootprint.packedY(packedBase), z)) {
                    continue;
                }
                bases.add(packedBase);
                columns.add(TemplateFootprint.columnKey(x, z));
            }
            if (bases.isEmpty()) {
                return EMPTY;
            }
            bases.sort((first, second) -> {
                int yOrder = Integer.compare(TemplateFootprint.packedY(first), TemplateFootprint.packedY(second));
                return yOrder == 0 ? Long.compare(first, second) : yOrder;
            });
            Set<Long> occupancy = new HashSet<>();
            for (long packedCell : packedOccupancy) {
                if (columns.contains(TemplateFootprint.columnKey(
                        TemplateFootprint.packedX(packedCell), TemplateFootprint.packedZ(packedCell)))) {
                    occupancy.add(packedCell);
                }
            }
            return new VacuumFoundationPlan(List.copyOf(bases), Set.copyOf(occupancy));
        }
    }

    /** Bounds the template blocks one start may read for one fitting. */
    static final class TemplateCellBudget {
        private final int maximum;
        private int consumed;

        TemplateCellBudget(int maximum) {
            if (maximum < 0) {
                throw new IllegalArgumentException("Native structure surface template budget cannot be negative");
            }
            this.maximum = maximum;
        }

        void consume(int amount) {
            if (amount < 0 || consumed > maximum - amount) {
                throw TemplateBudgetExceeded.INSTANCE;
            }
            consumed += amount;
        }
    }

    static final class TemplateBudgetExceeded extends RuntimeException {
        static final TemplateBudgetExceeded INSTANCE = new TemplateBudgetExceeded();

        private TemplateBudgetExceeded() {
            super(null, null, false, false);
        }
    }
}
