package art.arcane.iris.nativegen;

import art.arcane.iris.engine.object.IrisObjectVacuum;
import art.arcane.iris.engine.object.IrisStructureTerrain;
import art.arcane.iris.engine.object.IrisStructureTerrainMode;
import art.arcane.iris.spi.IrisLogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.IglooPieces;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.levelgen.structure.structures.OceanRuinStructure;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckStructure;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

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

public final class NativeStructureSurfaceFitter {
    private static final double SURFACE_TERRAIN_FALLOFF = 2.0;
    private static final long SURFACE_TERRAIN_INFLUENCE_SCALE = 1_000_000L;
    private static final int SURFACE_TERRAIN_RADIUS = 12;
    private static final int MAX_SOURCE_SURFACE_DELTA = 6;
    private static final int MAX_SURFACE_TEMPLATE_CELLS = 4_194_304;
    private static final Set<String> WARNED_SOURCE_BUDGET =
            ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_VACUUM_BUDGET =
            ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_FLATTEN_BUDGET =
            ConcurrentHashMap.newKeySet();

    private NativeStructureSurfaceFitter() {
    }

    public static SurfaceTerrainPlan prepareSurfaceStructures(
            WorldGenLevel world, BoundingBox area,
            List<NativeStructureTerrainIntegrator.TerrainTarget> targets,
            IntBinaryOperator surfaceHeight) {
        return prepareSurfaceStructures(
                world, area, targets, surfaceHeight, MAX_SURFACE_TEMPLATE_CELLS);
    }

    static SurfaceTerrainPlan prepareSurfaceStructures(
            WorldGenLevel world, BoundingBox area,
            List<NativeStructureTerrainIntegrator.TerrainTarget> targets,
            IntBinaryOperator surfaceHeight, int maximumTemplateCells) {
        if (targets == null || targets.isEmpty()) {
            return SurfaceTerrainPlan.empty();
        }
        Objects.requireNonNull(surfaceHeight, "Surface structure terrain fitting requires an Iris height resolver");
        List<SurfaceAnchor> anchors = collectSourceSurfaceAnchors(
                world, area, targets, maximumTemplateCells);
        if (!anchors.isEmpty()) {
            fitSurfaceTerrain(world, area, anchors, surfaceHeight);
        }
        FlattenFootprint flatten = collectFlattenFootprint(
                world, area, targets, surfaceHeight, maximumTemplateCells);
        Map<Long, Integer> flattenedHeights = flatten.anchors().isEmpty() && flatten.projectedSupport().isEmpty() ? Map.of()
                : fitFlattenTerrain(world, area, flatten, surfaceHeight);
        VacuumFootprint vacuum = collectVacuumFootprint(
                world, area, targets, maximumTemplateCells);
        if (!vacuum.anchors().isEmpty()) {
            fitVacuumTerrain(world, area, vacuum, surfaceHeight);
        }
        return SurfaceTerrainPlan.create(
                vacuum, area, flattenedHeights);
    }

    public static void repairVacuumFoundations(
            WorldGenLevel world, BoundingBox area,
            SurfaceTerrainPlan plan) {
        if (plan == null || plan.foundationBases.isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (long packedBase : plan.foundationBases) {
            BlockPos base = BlockPos.of(packedBase);
            if (!area.isInside(base)) {
                continue;
            }
            BlockState baseState = world.getBlockState(position.set(base));
            if (!NativeStructureTemplateOccupancy.isSolidBase(baseState)) {
                continue;
            }
            int supportY = findVacuumFoundationSupport(
                    world, area, position, base.getX(), base.getY() - 1, base.getZ());
            if (supportY == Integer.MIN_VALUE || supportY == base.getY() - 1) {
                continue;
            }
            if (intersectsVacuumOccupancy(
                    plan.occupiedCells, base.getX(), base.getZ(),
                    supportY + 1, base.getY() - 1)) {
                continue;
            }
            BlockState fill = resolveSurfaceMaterials(
                    world, position, base.getX(), base.getZ(), supportY,
                    area.minY()).subsurface();
            for (int y = supportY + 1; y < base.getY(); y++) {
                BlockState existing = world.getBlockState(
                        position.set(base.getX(), y, base.getZ()));
                if (existing.isAir() || !existing.getFluidState().isEmpty()) {
                    world.setBlock(position, fill, 2);
                }
            }
        }
    }

    static boolean shouldPrepareSurfaceTerrain(TerrainAdjustment adjustment,
                                               GenerationStep.Decoration step) {
        return step == GenerationStep.Decoration.SURFACE_STRUCTURES
                && (adjustment == TerrainAdjustment.BEARD_THIN
                || adjustment == TerrainAdjustment.BEARD_BOX);
    }

    static int surfaceTerrainRadius() {
        return SURFACE_TERRAIN_RADIUS;
    }

    static int resolveSurfaceTarget(List<SurfaceAnchor> anchors, int worldX, int worldZ,
                                    int originalY) {
        return resolveSurface(anchors, worldX, worldZ, originalY).targetY();
    }

    private static SurfaceResolution resolveSurface(List<SurfaceAnchor> anchors,
                                                    int worldX, int worldZ, int originalY) {
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
            double factor = Math.pow(
                    1D - distance / SURFACE_TERRAIN_RADIUS, SURFACE_TERRAIN_FALLOFF);
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
            return new SurfaceResolution(
                    localTargetY,
                    selectedLocal.strength() > 1
                            && localTargetY == selectedLocal.meetY());
        }
        if (totalInfluence == 0L) {
            return new SurfaceResolution(originalY, false);
        }
        double blendedTargetY = weightedTargetY / (double) totalInfluence;
        double factor = maximumInfluence / (double) SURFACE_TERRAIN_INFLUENCE_SCALE;
        return new SurfaceResolution(
                blendSurfaceTarget(originalY, blendedTargetY, factor), false);
    }

    private static int boundedSurfaceTarget(int originalY, int meetY) {
        int delta = Math.max(
                -MAX_SOURCE_SURFACE_DELTA,
                Math.min(MAX_SOURCE_SURFACE_DELTA, meetY - originalY));
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

    private static List<SurfaceAnchor> collectSourceSurfaceAnchors(
            WorldGenLevel world, BoundingBox area,
            List<NativeStructureTerrainIntegrator.TerrainTarget> targets,
            int maximumTemplateCells) {
        BoundingBox influenceArea = new BoundingBox(
                area.minX() - SURFACE_TERRAIN_RADIUS, area.minY(),
                area.minZ() - SURFACE_TERRAIN_RADIUS,
                area.maxX() + SURFACE_TERRAIN_RADIUS, area.maxY(),
                area.maxZ() + SURFACE_TERRAIN_RADIUS);
        List<SurfaceAnchor> anchors = new ArrayList<>();
        Set<StructureStart> seenStarts = Collections.newSetFromMap(
                new IdentityHashMap<>());
        for (NativeStructureTerrainIntegrator.TerrainTarget target : targets) {
            if (!requiresSourceSurfaceTerrain(target)) {
                continue;
            }
            StructureStart start = target.start();
            if (!seenStarts.add(start)) {
                continue;
            }
            BoundingBox firstBounds = start.getPieces().getFirst().getBoundingBox();
            BlockPos firstCenter = firstBounds.getCenter();
            BlockPos referencePosition = new BlockPos(
                    firstCenter.getX(), firstBounds.minY(), firstCenter.getZ());
            TemplateCellBudget budget = new TemplateCellBudget(maximumTemplateCells);
            boolean budgetExceeded = false;
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof PoolElementStructurePiece poolPiece) {
                    StructureTemplatePool.Projection projection =
                            poolPiece.getElement().getProjection();
                    if (!budgetExceeded
                            && (projection == StructureTemplatePool.Projection.RIGID
                            || projection == StructureTemplatePool.Projection.TERRAIN_MATCHING)
                            && intersectsHorizontally(poolPiece.getBoundingBox(), influenceArea)) {
                        try {
                            List<SurfaceAnchor> pieceAnchors = new ArrayList<>();
                            addProcessedSurfaceAnchors(
                                    world, influenceArea, poolPiece, referencePosition,
                                    projection, budget, pieceAnchors);
                            anchors.addAll(pieceAnchors);
                        } catch (TemplateBudgetExceeded ignored) {
                            budgetExceeded = true;
                            warnSourceBudget(target);
                        }
                    }
                    for (JigsawJunction junction : poolPiece.getJunctions()) {
                        anchors.add(new SurfaceAnchor(
                                junction.getSourceX(), junction.getSourceX(),
                                junction.getSourceZ(), junction.getSourceZ(),
                                junction.getSourceGroundY() - 1, 1));
                    }
                    continue;
                }
                BoundingBox bounds = piece.getBoundingBox();
                anchors.add(surfaceAnchor(bounds, bounds.minY(), 2));
            }
        }
        return List.copyOf(anchors);
    }

    private static void addProcessedSurfaceAnchors(
            WorldGenLevel world, BoundingBox influenceArea,
            PoolElementStructurePiece piece, BlockPos referencePosition,
            StructureTemplatePool.Projection projection,
            TemplateCellBudget budget,
            List<SurfaceAnchor> anchors) {
        NativeStructureTemplateOccupancy.OccupancyResult occupancy =
                NativeStructureTemplateOccupancy.resolve(
                        world, piece, referencePosition, influenceArea,
                        () -> world.getLevel().getStructureManager(),
                        influenceArea::isInside, budget::consume);
        if (!occupancy.resolved()) {
            return;
        }
        int maximumFoundationY = piece.getBoundingBox().minY()
                + piece.getGroundLevelDelta();
        Map<Long, NativeStructureTemplateOccupancy.LowestCell> lowest =
                NativeStructureTemplateOccupancy.lowestProcessedSolidCells(occupancy.cells());
        for (NativeStructureTemplateOccupancy.LowestCell cell : lowest.values()) {
            budget.consume(1);
            if (projection == StructureTemplatePool.Projection.RIGID
                    && cell.y() > maximumFoundationY) {
                continue;
            }
            int groundY = projection == StructureTemplatePool.Projection.RIGID
                    ? maximumFoundationY : cell.y();
            BoundingBox column = new BoundingBox(
                    cell.x(), groundY, cell.z(), cell.x(), groundY, cell.z());
            anchors.add(surfaceAnchor(column, groundY, 2));
        }
    }

    private static void warnSourceBudget(
            NativeStructureTerrainIntegrator.TerrainTarget target) {
        String structureId = target.structureId() == null
                ? target.start().getStructure().getClass().getName()
                : target.structureId();
        if (WARNED_SOURCE_BUDGET.add(structureId)) {
            IrisLogging.warn("Native structure SOURCE fitting for '"
                    + structureId + "' exceeded its bounded template budget; "
                    + "skipping remaining processed footprints for this start");
        }
    }

    private static VacuumFootprint collectVacuumFootprint(
            WorldGenLevel world, BoundingBox area,
            List<NativeStructureTerrainIntegrator.TerrainTarget> targets,
            int maximumTemplateCells) {
        BoundingBox influenceArea = new BoundingBox(
                area.minX() - SURFACE_TERRAIN_RADIUS, area.minY(),
                area.minZ() - SURFACE_TERRAIN_RADIUS,
                area.maxX() + SURFACE_TERRAIN_RADIUS, area.maxY(),
                area.maxZ() + SURFACE_TERRAIN_RADIUS);
        Map<Long, VacuumAnchor> anchors = new HashMap<>();
        Map<Long, Integer> columnCaps = new HashMap<>();
        Set<Long> foundationBases = new HashSet<>();
        Set<Long> occupiedCells = new HashSet<>();
        Set<StructureStart> seenStarts = Collections.newSetFromMap(
                new IdentityHashMap<>());
        for (NativeStructureTerrainIntegrator.TerrainTarget target : targets) {
            if (target == null || target.terrain() == null
                    || target.terrain().resolvedMode() != IrisStructureTerrainMode.VACUUM
                    || target.start() == null || !target.start().isValid()
                    || !seenStarts.add(target.start())) {
                continue;
            }
            StructureStart start = target.start();
            BoundingBox firstBounds = start.getPieces().getFirst().getBoundingBox();
            BlockPos firstCenter = firstBounds.getCenter();
            BlockPos referencePosition = new BlockPos(
                    firstCenter.getX(), firstBounds.minY(), firstCenter.getZ());
            Map<Long, VacuumAnchor> targetAnchors = new HashMap<>();
            Map<Long, Integer> targetCaps = new HashMap<>();
            Set<Long> targetFoundationBases = new HashSet<>();
            Set<Long> targetOccupiedCells = new HashSet<>();
            TemplateCellBudget budget = new TemplateCellBudget(maximumTemplateCells);
            try {
                for (StructurePiece piece : start.getPieces()) {
                    BoundingBox bounds = piece.getBoundingBox();
                    if (!intersectsHorizontally(bounds, influenceArea)
                            || !(piece instanceof PoolElementStructurePiece poolPiece)
                            || poolPiece.getElement().getProjection()
                            != StructureTemplatePool.Projection.RIGID) {
                        continue;
                    }
                    addVacuumJunctionAnchors(
                            anchors, influenceArea, poolPiece);
                    NativeStructureTemplateOccupancy.OccupancyResult occupancy =
                            NativeStructureTemplateOccupancy.resolve(
                                    world, poolPiece, referencePosition, influenceArea,
                                    () -> world.getLevel().getStructureManager(),
                                    influenceArea::isInside, budget::consume);
                    if (!occupancy.resolved()) {
                        continue;
                    }
                    targetOccupiedCells.addAll(occupancy.cells().keySet());
                    Map<Long, NativeStructureTemplateOccupancy.LowestCell> lowest =
                            NativeStructureTemplateOccupancy.lowestCells(
                                    occupancy.cells());
                    int groundY = bounds.minY()
                            + poolPiece.getGroundLevelDelta() - 1;
                    for (NativeStructureTemplateOccupancy.LowestCell cell : lowest.values()) {
                        budget.consume(1);
                        long column = NativeStructureTemplateOccupancy.columnKey(
                                cell.x(), cell.z());
                        targetCaps.merge(
                                column, Math.min(cell.y(), groundY), Math::min);
                        if (cell.occupancy().blocker()
                                || cell.y() > groundY + 1
                                || !NativeStructureTemplateOccupancy.isSolidBase(
                                cell.occupancy().state())) {
                            continue;
                        }
                        addVacuumAnchor(targetAnchors, cell.x(), cell.z(),
                                new VacuumAnchor(cell.y() - 1, 2));
                        targetFoundationBases.add(BlockPos.asLong(
                                cell.x(), cell.y(), cell.z()));
                    }
                }
            } catch (TemplateBudgetExceeded ignored) {
                warnVacuumBudget(target);
                continue;
            }
            targetAnchors.forEach((column, anchor) -> anchors.merge(
                    column, anchor, NativeStructureSurfaceFitter::preferredVacuumAnchor));
            targetCaps.forEach((column, cap) -> columnCaps.merge(
                    column, cap, Math::min));
            foundationBases.addAll(targetFoundationBases);
            occupiedCells.addAll(targetOccupiedCells);
        }
        return new VacuumFootprint(
                Map.copyOf(anchors), Map.copyOf(columnCaps),
                Set.copyOf(foundationBases), Set.copyOf(occupiedCells));
    }

    private static void addVacuumJunctionAnchors(
            Map<Long, VacuumAnchor> anchors, BoundingBox influenceArea,
            PoolElementStructurePiece poolPiece) {
        for (JigsawJunction junction : poolPiece.getJunctions()) {
            if (junction.getSourceX() < influenceArea.minX()
                    || junction.getSourceX() > influenceArea.maxX()
                    || junction.getSourceZ() < influenceArea.minZ()
                    || junction.getSourceZ() > influenceArea.maxZ()) {
                continue;
            }
            addVacuumAnchor(anchors,
                    junction.getSourceX(), junction.getSourceZ(),
                    new VacuumAnchor(junction.getSourceGroundY() - 1, 1));
        }
    }

    private static void warnVacuumBudget(
            NativeStructureTerrainIntegrator.TerrainTarget target) {
        String structureId = target.structureId() == null
                ? target.start().getStructure().getClass().getName()
                : target.structureId();
        if (WARNED_VACUUM_BUDGET.add(structureId)) {
            IrisLogging.warn("Native structure VACUUM fitting for '"
                    + structureId + "' exceeded its bounded template budget; "
                    + "using jigsaw junction smoothing for oversized starts");
        }
    }

    private static boolean intersectsHorizontally(
            BoundingBox first, BoundingBox second) {
        return first.maxX() >= second.minX() && first.minX() <= second.maxX()
                && first.maxZ() >= second.minZ() && first.minZ() <= second.maxZ();
    }

    private static void addVacuumAnchor(
            Map<Long, VacuumAnchor> anchors, int x, int z,
            VacuumAnchor candidate) {
        anchors.merge(NativeStructureTemplateOccupancy.columnKey(x, z), candidate,
                NativeStructureSurfaceFitter::preferredVacuumAnchor);
    }

    private static VacuumAnchor preferredVacuumAnchor(
            VacuumAnchor first, VacuumAnchor second) {
        if (first.strength() != second.strength()) {
            return first.strength() > second.strength() ? first : second;
        }
        return first.surfaceY() <= second.surfaceY() ? first : second;
    }

    static SurfaceAnchor surfaceAnchor(BoundingBox bounds, int groundY, int strength) {
        return new SurfaceAnchor(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ(),
                groundY - 1, strength);
    }

    static boolean requiresSurfaceTerrain(StructureStart start) {
        return requiresSurfaceTerrain(new NativeStructureTerrainIntegrator.TerrainTarget(
                null, start, new IrisStructureTerrain().setMode(IrisStructureTerrainMode.SOURCE)));
    }

    static boolean requiresSurfaceTerrain(NativeStructureTerrainIntegrator.TerrainTarget target) {
        if (target == null || target.terrain() == null) {
            return false;
        }
        StructureStart start = target.start();
        if (start == null || !start.isValid()) {
            return false;
        }
        IrisStructureTerrainMode mode = target.terrain().resolvedMode();
        return mode == IrisStructureTerrainMode.VACUUM
                || requiresFlattenTerrain(target)
                || requiresSourceSurfaceTerrain(target);
    }

    private static boolean requiresSourceSurfaceTerrain(
            NativeStructureTerrainIntegrator.TerrainTarget target) {
        return target != null && target.terrain() != null
                && target.start() != null && target.start().isValid()
                && target.terrain().resolvedMode() == IrisStructureTerrainMode.SOURCE
                && shouldPrepareSurfaceTerrain(
                        target.start().getStructure().terrainAdaptation(),
                        target.start().getStructure().step());
    }

    private static void fitSurfaceTerrain(WorldGenLevel world, BoundingBox area,
                                          List<SurfaceAnchor> anchors,
                                          IntBinaryOperator surfaceHeight) {
        int width = area.getXSpan();
        int depth = area.getZSpan();
        int[] originalHeights = new int[width * depth];
        int[] targetHeights = new int[width * depth];
        boolean[] rigidBaseSupport = new boolean[width * depth];
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                int column = (z - area.minZ()) * width + x - area.minX();
                int originalY = Math.max(area.minY(), Math.min(
                        area.maxY(), surfaceHeight.applyAsInt(x, z)));
                SurfaceResolution resolution = resolveSurface(anchors, x, z, originalY);
                originalHeights[column] = originalY;
                targetHeights[column] = Math.max(area.minY(), Math.min(
                        area.maxY(), resolution.targetY()));
                rigidBaseSupport[column] = resolution.rigidBaseSupport();
            }
        }
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                int column = (z - area.minZ()) * width + x - area.minX();
                applySurfaceColumn(world, position, x, z,
                        originalHeights[column], targetHeights[column], area.minY(), area.maxY(),
                        rigidBaseSupport[column]);
            }
        }
    }

    static boolean requiresFlattenTerrain(NativeStructureTerrainIntegrator.TerrainTarget target) {
        if (target == null || target.terrain() == null
                || target.terrain().resolvedMode() != IrisStructureTerrainMode.FLATTEN
                || target.terrain().resolvedFlattenRange() == 0
                || target.start() == null || !target.start().isValid()) {
            return false;
        }
        StructureStart start = target.start();
        return start.getStructure().step() == GenerationStep.Decoration.SURFACE_STRUCTURES
                && start.getStructure().terrainAdaptation() != TerrainAdjustment.BURY
                && start.getStructure().terrainAdaptation() != TerrainAdjustment.ENCAPSULATE
                && !(start.getStructure() instanceof StrongholdStructure)
                && !(start.getStructure() instanceof OceanMonumentStructure)
                && !(start.getStructure() instanceof OceanRuinStructure)
                && (!(start.getStructure() instanceof ShipwreckStructure)
                || "minecraft:shipwreck_beached".equals(target.structureId()));
    }

    private static FlattenFootprint collectFlattenFootprint(
            WorldGenLevel world, BoundingBox area,
            List<NativeStructureTerrainIntegrator.TerrainTarget> targets,
            IntBinaryOperator surfaceHeight, int maximumTemplateCells) {
        List<FlattenAnchor> anchors = new ArrayList<>();
        Map<Long, Integer> projectedSupport = new HashMap<>();
        Set<StructureStart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (NativeStructureTerrainIntegrator.TerrainTarget target : targets) {
            if (!requiresFlattenTerrain(target) || !seen.add(target.start())) {
                continue;
            }
            int radius = Math.max(0, Math.min(128, target.terrain().getHorizontalPadding()));
            int range = target.terrain().resolvedFlattenRange();
            BoundingBox influenceArea = new BoundingBox(
                    area.minX() - radius, area.minY(), area.minZ() - radius,
                    area.maxX() + radius, area.maxY(), area.maxZ() + radius);
            StructureStart start = target.start();
            if (!hasExposedFlattenPiece(start, surfaceHeight, range)) {
                continue;
            }
            BoundingBox first = start.getPieces().getFirst().getBoundingBox();
            BlockPos reference = new BlockPos(first.getCenter().getX(), first.minY(), first.getCenter().getZ());
            TemplateCellBudget budget = new TemplateCellBudget(maximumTemplateCells);
            List<SurfaceAnchor> sources = new ArrayList<>();
            Set<Long> projectedColumns = new HashSet<>();
            try {
                for (StructurePiece piece : start.getPieces()) {
                    BoundingBox bounds = piece.getBoundingBox();
                    if (!intersectsHorizontally(bounds, influenceArea) || !flattenPieceAllowed(start, piece)) {
                        continue;
                    }
                    if (piece instanceof PoolElementStructurePiece poolPiece) {
                        if (poolPiece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID) {
                            addProcessedSurfaceAnchors(world, influenceArea, poolPiece, reference,
                                    StructureTemplatePool.Projection.RIGID, budget, sources);
                        } else {
                            addProjectedFlattenSupport(world, area, poolPiece, reference, budget, projectedColumns);
                        }
                        for (JigsawJunction junction : poolPiece.getJunctions()) {
                            sources.add(new SurfaceAnchor(junction.getSourceX(), junction.getSourceX(),
                                    junction.getSourceZ(), junction.getSourceZ(), junction.getSourceGroundY() - 1, 1));
                        }
                    } else {
                        sources.add(new SurfaceAnchor(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ(),
                                flattenGroundY(start, piece, surfaceHeight), 2));
                    }
                }
            } catch (TemplateBudgetExceeded ignored) {
                String structureId = target.structureId() == null
                        ? start.getStructure().getClass().getName() : target.structureId();
                if (WARNED_FLATTEN_BUDGET.add(structureId)) {
                    IrisLogging.warn("Native structure FLATTEN fitting for '" + structureId
                            + "' exceeded its bounded template budget; skipping this start");
                }
                continue;
            }
            for (SurfaceAnchor source : sources) {
                anchors.add(new FlattenAnchor(source, radius, range));
            }
            for (long column : projectedColumns) {
                projectedSupport.merge(column, range, Math::max);
            }
        }
        return new FlattenFootprint(List.copyOf(anchors), Map.copyOf(projectedSupport));
    }

    private static void addProjectedFlattenSupport(
            WorldGenLevel world, BoundingBox area, PoolElementStructurePiece piece,
            BlockPos reference, TemplateCellBudget budget, Set<Long> columns) {
        NativeStructureTemplateOccupancy.OccupancyResult occupancy = NativeStructureTemplateOccupancy.resolve(
                world, piece, reference, area, () -> world.getLevel().getStructureManager(),
                area::isInside, budget::consume);
        for (Map.Entry<Long, NativeStructureTemplateOccupancy.OccupancyCell> entry : occupancy.cells().entrySet()) {
            NativeStructureTemplateOccupancy.OccupancyCell cell = entry.getValue();
            if (cell.blocker() || !NativeStructureTemplateOccupancy.isSolidBase(cell.state())) {
                continue;
            }
            BlockPos position = BlockPos.of(entry.getKey());
            columns.add(NativeStructureTemplateOccupancy.columnKey(position.getX(), position.getZ()));
        }
    }

    private static boolean flattenPieceAllowed(StructureStart start, StructurePiece piece) {
        if (piece instanceof RuinedPortalPiece portal) {
            RuinedPortalPiece.VerticalPlacement placement = NativeStructureReflection.ruinedPortalVerticalPlacement(portal);
            return placement == RuinedPortalPiece.VerticalPlacement.ON_LAND_SURFACE
                    || placement == RuinedPortalPiece.VerticalPlacement.IN_NETHER;
        }
        if (piece instanceof IglooPieces.IglooPiece) {
            int highest = Integer.MIN_VALUE;
            for (StructurePiece candidate : start.getPieces()) {
                if (candidate instanceof IglooPieces.IglooPiece) {
                    highest = Math.max(highest, candidate.getBoundingBox().minY());
                }
            }
            return piece.getBoundingBox().minY() == highest;
        }
        return true;
    }

    static boolean hasExposedFlattenPiece(
            StructureStart start, IntBinaryOperator surfaceHeight, int range) {
        for (StructurePiece piece : start.getPieces()) {
            if (!flattenPieceAllowed(start, piece)) {
                continue;
            }
            BoundingBox bounds = piece.getBoundingBox();
            int groundY = flattenGroundY(start, piece, surfaceHeight);
            int roofY = piece instanceof IglooPieces.IglooPiece || piece instanceof SwampHutPiece
                    ? groundY + bounds.getYSpan() : bounds.maxY();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    int surfaceY = surfaceHeight.applyAsInt(x, z);
                    if (surfaceY <= roofY && (long) groundY - surfaceY <= range) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int flattenGroundY(
            StructureStart start, StructurePiece piece, IntBinaryOperator surfaceHeight) {
        BoundingBox bounds = piece.getBoundingBox();
        if (piece instanceof PoolElementStructurePiece poolPiece) {
            return bounds.minY() + poolPiece.getGroundLevelDelta() - 1;
        }
        if (piece instanceof IglooPieces.IglooPiece igloo) {
            BlockPos entrance = igloo.templatePosition().offset(
                    StructureTemplate.calculateRelativePosition(igloo.placeSettings(), new BlockPos(3, 0, 0)));
            return surfaceHeight.applyAsInt(entrance.getX(), entrance.getZ());
        }
        if (piece instanceof SwampHutPiece || start.getStructure() instanceof ShipwreckStructure) {
            long sum = 0;
            int minimum = Integer.MAX_VALUE;
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    int surfaceY = surfaceHeight.applyAsInt(x, z);
                    sum += surfaceY;
                    minimum = Math.min(minimum, surfaceY);
                }
            }
            return piece instanceof SwampHutPiece
                    ? (int) Math.floorDiv(sum, (long) bounds.getXSpan() * bounds.getZSpan()) : minimum;
        }
        if (piece instanceof WoodlandMansionPieces.WoodlandMansionPiece) {
            return start.getBoundingBox().minY() - 1;
        }
        return bounds.minY() - 1;
    }

    static FlattenResolution resolveFlattenSurface(
            List<FlattenAnchor> anchors, int x, int z, int originalY) {
        FlattenAnchor selected = null;
        long weightedY = 0;
        long totalWeight = 0;
        long maximumInfluence = 0;
        int range = 0;
        for (FlattenAnchor anchor : anchors) {
            SurfaceAnchor source = anchor.source();
            int outX = IrisObjectVacuum.outset(x, source.minX(), source.maxX());
            int outZ = IrisObjectVacuum.outset(z, source.minZ(), source.maxZ());
            if (outX == 0 && outZ == 0) {
                if (selected == null || precedes(source, selected.source())) {
                    selected = anchor;
                }
                continue;
            }
            double distance = Math.sqrt((double) outX * outX + (double) outZ * outZ);
            if (anchor.radius() == 0 || distance >= anchor.radius()) {
                continue;
            }
            double progress = distance / anchor.radius();
            double factor = 1D - progress * progress * (3D - 2D * progress);
            long influence = Math.round(factor * SURFACE_TERRAIN_INFLUENCE_SCALE);
            long weight = influence * source.strength();
            int targetY = originalY + Math.max(-anchor.range(), Math.min(anchor.range(), source.meetY() - originalY));
            weightedY += weight * targetY;
            totalWeight += weight;
            maximumInfluence = Math.max(maximumInfluence, influence);
            range = Math.max(range, anchor.range());
        }
        if (selected != null) {
            int targetY = originalY + Math.max(-selected.range(),
                    Math.min(selected.range(), selected.source().meetY() - originalY));
            return new FlattenResolution(targetY, selected.range());
        }
        if (totalWeight == 0) {
            return new FlattenResolution(originalY, 0);
        }
        int targetY = blendSurfaceTarget(originalY, weightedY / (double) totalWeight,
                maximumInfluence / (double) SURFACE_TERRAIN_INFLUENCE_SCALE);
        return new FlattenResolution(targetY, range);
    }

    private static Map<Long, Integer> fitFlattenTerrain(
            WorldGenLevel world, BoundingBox area, FlattenFootprint footprint,
            IntBinaryOperator surfaceHeight) {
        Map<Long, Integer> heights = new HashMap<>();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                int originalY = Math.max(area.minY(), Math.min(area.maxY(), surfaceHeight.applyAsInt(x, z)));
                long column = NativeStructureTemplateOccupancy.columnKey(x, z);
                FlattenResolution resolution = resolveFlattenSurface(footprint.anchors(), x, z, originalY);
                int projectedRange = footprint.projectedSupport().getOrDefault(column, 0);
                int range = Math.max(resolution.range(), projectedRange);
                if (range == 0 || originalY < area.maxY()
                        && !world.getBlockState(position.set(x, originalY + 1, z)).getFluidState().isEmpty()) {
                    continue;
                }
                int targetY = Math.max(area.minY(), Math.min(area.maxY(), resolution.targetY()));
                int minimumY = Math.max(area.minY(), targetY - range);
                applySurfaceColumn(world, position, x, z, originalY, targetY,
                        minimumY, Math.min(area.maxY(), originalY + range));
                fillFlattenFoundation(world, position, x, z, targetY, minimumY);
                heights.put(column, targetY + 1);
            }
        }
        return Map.copyOf(heights);
    }

    private static void fillFlattenFoundation(
            WorldGenLevel world, BlockPos.MutableBlockPos position,
            int x, int z, int targetY, int minimumY) {
        SurfaceMaterials materials = resolveSurfaceMaterials(world, position, x, z, targetY, minimumY);
        if (!isTerrainBlock(world.getBlockState(position.set(x, targetY, z)))) {
            world.setBlock(position, materials.surface(), 2);
        }
        for (int y = targetY - 1; y >= minimumY; y--) {
            BlockState existing = world.getBlockState(position.set(x, y, z));
            if (!existing.getFluidState().isEmpty()
                    || NativeStructureVegetationClearer.isTreeBlock(existing)) {
                break;
            }
            if (!isTerrainBlock(existing)) {
                world.setBlock(position, materials.subsurface(), 2);
            }
        }
    }

    private static void fitVacuumTerrain(
            WorldGenLevel world, BoundingBox area, VacuumFootprint footprint,
            IntBinaryOperator surfaceHeight) {
        int width = area.getXSpan();
        int depth = area.getZSpan();
        int[] originalHeights = new int[width * depth];
        int[] targetHeights = new int[width * depth];
        boolean[] rigidBaseSupport = new boolean[width * depth];
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                int column = (z - area.minZ()) * width + x - area.minX();
                int nominalY = Math.max(area.minY(), Math.min(
                        area.maxY(), surfaceHeight.applyAsInt(x, z)));
                long columnKey = NativeStructureTemplateOccupancy.columnKey(x, z);
                VacuumAnchor local = footprint.anchors().get(columnKey);
                Integer cap = footprint.columnCaps().get(columnKey);
                boolean suppressed = local != null && local.strength() > 1
                        && cap != null && cap < local.surfaceY();
                int scanStartY = local != null && local.strength() > 1 && !suppressed
                        ? Math.max(area.minY(), Math.min(area.maxY(), local.surfaceY()))
                        : nominalY;
                int originalY = resolveVacuumSurfaceHeight(
                        world, position, x, z, scanStartY, area.minY());
                SurfaceResolution resolution = resolveVacuumSurface(
                        footprint, x, z, originalY);
                originalHeights[column] = originalY;
                targetHeights[column] = Math.max(originalY, Math.min(
                        area.maxY(), resolution.targetY()));
                rigidBaseSupport[column] = resolution.rigidBaseSupport()
                        && targetHeights[column] > originalY;
            }
        }
        for (int z = area.minZ(); z <= area.maxZ(); z++) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                int column = (z - area.minZ()) * width + x - area.minX();
                applySurfaceColumn(world, position, x, z,
                        originalHeights[column], targetHeights[column],
                        area.minY(), area.maxY(), rigidBaseSupport[column]);
            }
        }
    }

    private static int resolveVacuumSurfaceHeight(
            WorldGenLevel world, BlockPos.MutableBlockPos position,
            int x, int z, int nominalY, int worldMinY) {
        for (int y = nominalY; y >= worldMinY; y--) {
            if (isTerrainBlock(world.getBlockState(position.set(x, y, z)))) {
                return y;
            }
        }
        return nominalY;
    }

    private static int findVacuumFoundationSupport(
            WorldGenLevel world, BoundingBox area,
            BlockPos.MutableBlockPos position, int x, int startY, int z) {
        if (startY < area.minY()) {
            return Integer.MIN_VALUE;
        }
        int maximumY = Math.min(area.maxY(), startY);
        for (int y = maximumY; y >= area.minY(); y--) {
            BlockState state = world.getBlockState(position.set(x, y, z));
            if (isTerrainBlock(state)) {
                return y;
            }
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean intersectsVacuumOccupancy(
            Set<Long> occupiedCells, int x, int z, int minimumY, int maximumY) {
        for (int y = minimumY; y <= maximumY; y++) {
            if (occupiedCells.contains(BlockPos.asLong(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    private static SurfaceResolution resolveVacuumSurface(
            VacuumFootprint footprint, int worldX, int worldZ, int originalY) {
        long column = NativeStructureTemplateOccupancy.columnKey(worldX, worldZ);
        Integer cap = footprint.columnCaps().get(column);
        VacuumAnchor local = footprint.anchors().get(column);
        if (local != null && local.strength() > 1) {
            int localTargetY = cap == null
                    ? local.surfaceY() : Math.min(local.surfaceY(), cap);
            if (localTargetY <= originalY) {
                return new SurfaceResolution(originalY, false);
            }
            return new SurfaceResolution(
                    localTargetY, localTargetY == local.surfaceY());
        }
        long totalInfluence = 0L;
        long weightedSurfaceY = 0L;
        long maximumInfluence = 0L;
        int radiusSquared = SURFACE_TERRAIN_RADIUS * SURFACE_TERRAIN_RADIUS;
        for (int offsetX = -SURFACE_TERRAIN_RADIUS;
             offsetX <= SURFACE_TERRAIN_RADIUS; offsetX++) {
            for (int offsetZ = -SURFACE_TERRAIN_RADIUS;
                 offsetZ <= SURFACE_TERRAIN_RADIUS; offsetZ++) {
                int horizontalDistanceSquared = offsetX * offsetX + offsetZ * offsetZ;
                if (horizontalDistanceSquared > radiusSquared) {
                    continue;
                }
                VacuumAnchor anchor = footprint.anchors().get(
                        NativeStructureTemplateOccupancy.columnKey(
                                worldX + offsetX, worldZ + offsetZ));
                if (anchor == null || anchor.surfaceY() <= originalY) {
                    continue;
                }
                int verticalDistance = Math.abs(anchor.surfaceY() + 1 - originalY);
                long distanceSquared = horizontalDistanceSquared
                        + (long) verticalDistance * verticalDistance;
                double factor = 0D;
                if (distanceSquared <= radiusSquared) {
                    factor = surfaceFactor(Math.sqrt(distanceSquared));
                }
                if (anchor.strength() > 1
                        && verticalDistance > SURFACE_TERRAIN_RADIUS) {
                    double rescueProgress = Math.min(1D,
                            (verticalDistance - SURFACE_TERRAIN_RADIUS)
                                    / (double) SURFACE_TERRAIN_RADIUS);
                    double rescueWeight = rescueProgress * rescueProgress
                            * (3D - 2D * rescueProgress);
                    factor = Math.max(factor,
                            surfaceFactor(Math.sqrt(horizontalDistanceSquared))
                                    * rescueWeight);
                }
                long influence = Math.round(
                        factor * SURFACE_TERRAIN_INFLUENCE_SCALE);
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
        int targetY = (int) Math.round(
                originalY + ((blendedSurfaceY - originalY) * factor));
        if (cap != null) {
            targetY = Math.min(targetY, cap);
        }
        return new SurfaceResolution(Math.max(originalY, targetY), false);
    }

    private static double surfaceFactor(double distance) {
        return Math.pow(1D - distance / SURFACE_TERRAIN_RADIUS,
                SURFACE_TERRAIN_FALLOFF);
    }

    static void applySurfaceColumn(WorldGenLevel world, BlockPos.MutableBlockPos position,
                                   int x, int z, int originalY, int targetY,
                                   int worldMinY, int worldMaxY) {
        applySurfaceColumn(world, position, x, z, originalY, targetY,
                worldMinY, worldMaxY, false);
    }

    static void applySurfaceColumn(WorldGenLevel world, BlockPos.MutableBlockPos position,
                                   int x, int z, int originalY, int targetY,
                                   int worldMinY, int worldMaxY,
                                   boolean requireRigidBaseSupport) {
        if (targetY == originalY) {
            if (requireRigidBaseSupport) {
                ensureRigidBaseTerrain(world, position, x, z, targetY, worldMinY);
            }
            return;
        }
        SurfaceMaterials materials = resolveSurfaceMaterials(world, position, x, z, originalY, worldMinY);
        if (targetY < originalY) {
            BlockState clearedState = clearSurfaceDecorationAndResolveFill(
                    world, position, x, z, originalY, worldMaxY);
            for (int y = originalY; y > targetY; y--) {
                world.setBlock(position.set(x, y, z), clearedState, 2);
            }
            world.setBlock(position.set(x, targetY, z), materials.surface(), 2);
            if (requireRigidBaseSupport) {
                ensureRigidBaseTerrain(world, position, x, z, targetY, worldMinY, materials);
            }
            return;
        }
        for (int y = originalY + 1; y < targetY; y++) {
            position.set(x, y, z);
            if (!NativeStructureVegetationClearer.isTreeBlock(world.getBlockState(position))) {
                world.setBlock(position, materials.subsurface(), 2);
            }
        }
        position.set(x, targetY, z);
        if (!NativeStructureVegetationClearer.isTreeBlock(world.getBlockState(position))) {
            world.setBlock(position, materials.surface(), 2);
        }
        if (requireRigidBaseSupport) {
            ensureRigidBaseTerrain(world, position, x, z, targetY, worldMinY, materials);
        }
    }

    private static void ensureRigidBaseTerrain(WorldGenLevel world,
                                               BlockPos.MutableBlockPos position,
                                               int x, int z, int targetY, int worldMinY) {
        int supportY = targetY - 1;
        boolean targetIsTerrain = isTerrainBlock(world.getBlockState(position.set(x, targetY, z)));
        boolean supportIsTerrain = supportY < worldMinY
                || isTerrainBlock(world.getBlockState(position.set(x, supportY, z)));
        if (targetIsTerrain && supportIsTerrain) {
            return;
        }
        SurfaceMaterials materials = resolveSurfaceMaterials(
                world, position, x, z, targetY, worldMinY);
        ensureRigidBaseTerrain(world, position, x, z, targetY, worldMinY, materials);
    }

    private static void ensureRigidBaseTerrain(WorldGenLevel world,
                                               BlockPos.MutableBlockPos position,
                                               int x, int z, int targetY, int worldMinY,
                                               SurfaceMaterials materials) {
        int supportY = targetY - 1;
        position.set(x, targetY, z);
        if (!isTerrainBlock(world.getBlockState(position))) {
            world.setBlock(position, materials.surface(), 2);
        }
        if (supportY < worldMinY) {
            return;
        }
        position.set(x, supportY, z);
        if (!isTerrainBlock(world.getBlockState(position))) {
            world.setBlock(position, materials.subsurface(), 2);
        }
    }

    private static BlockState clearSurfaceDecorationAndResolveFill(
            WorldGenLevel world, BlockPos.MutableBlockPos position,
            int x, int z, int originalY, int worldMaxY) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = originalY + 1; y <= worldMaxY; y++) {
            BlockState state = world.getBlockState(position.set(x, y, z));
            if (state.isAir()) {
                return air;
            }
            if (!state.getFluidState().isEmpty()) {
                BlockState fluid = state.getFluidState().createLegacyBlock();
                if (state != fluid) {
                    world.setBlock(position, fluid, 2);
                }
                return fluid;
            }
            if (state.isSolid() || NativeStructureVegetationClearer.isTreeBlock(state)) {
                return air;
            }
            world.setBlock(position, air, 2);
        }
        return air;
    }

    private static SurfaceMaterials resolveSurfaceMaterials(WorldGenLevel world,
                                                            BlockPos.MutableBlockPos position,
                                                            int x, int z, int originalY,
                                                            int worldMinY) {
        BlockState surface = world.getBlockState(position.set(x, originalY, z));
        BlockState subsurface = null;
        for (int y = originalY - 1; y >= worldMinY; y--) {
            BlockState candidate = world.getBlockState(position.set(x, y, z));
            if (isTerrainBlock(candidate)) {
                subsurface = candidate;
                break;
            }
        }
        if (subsurface == null) {
            subsurface = isTerrainBlock(surface) ? surface : Blocks.STONE.defaultBlockState();
        }
        if (!isTerrainBlock(surface)) {
            surface = subsurface;
        }
        return new SurfaceMaterials(surface, subsurface);
    }

    private static boolean isTerrainBlock(BlockState state) {
        return state.isSolid() && !NativeStructureVegetationClearer.isTreeBlock(state);
    }

    record SurfaceAnchor(int minX, int maxX, int minZ, int maxZ, int meetY, int strength) {
    }

    record FlattenAnchor(SurfaceAnchor source, int radius, int range) {
    }

    record FlattenResolution(int targetY, int range) {
    }

    private record FlattenFootprint(List<FlattenAnchor> anchors, Map<Long, Integer> projectedSupport) {
    }

    private record SurfaceMaterials(BlockState surface, BlockState subsurface) {
    }

    private record SurfaceResolution(int targetY, boolean rigidBaseSupport) {
    }

    private record VacuumFootprint(
            Map<Long, VacuumAnchor> anchors, Map<Long, Integer> columnCaps,
            Set<Long> foundationBases, Set<Long> occupiedCells) {
    }

    private record VacuumAnchor(int surfaceY, int strength) {
    }

    public record SurfaceTerrainPlan(
            List<Long> foundationBases, Set<Long> occupiedCells, Map<Long, Integer> flattenedHeights) {
        private static final SurfaceTerrainPlan EMPTY =
                new SurfaceTerrainPlan(List.of(), Set.of(), Map.of());

        public SurfaceTerrainPlan {
            foundationBases = List.copyOf(foundationBases);
            occupiedCells = Set.copyOf(occupiedCells);
            flattenedHeights = Map.copyOf(flattenedHeights);
        }

        private static SurfaceTerrainPlan empty() {
            return EMPTY;
        }

        private static SurfaceTerrainPlan create(
                VacuumFootprint vacuum, BoundingBox area, Map<Long, Integer> flattenedHeights) {
            Set<Long> packedBases = vacuum.foundationBases();
            if (packedBases.isEmpty() && flattenedHeights.isEmpty()) {
                return EMPTY;
            }
            List<Long> bases = new ArrayList<>(packedBases.size());
            Set<Long> columns = new HashSet<>();
            for (long packedBase : packedBases) {
                BlockPos base = BlockPos.of(packedBase);
                if (!area.isInside(base)) {
                    continue;
                }
                bases.add(packedBase);
                columns.add(NativeStructureTemplateOccupancy.columnKey(
                        base.getX(), base.getZ()));
            }
            if (bases.isEmpty() && flattenedHeights.isEmpty()) {
                return EMPTY;
            }
            bases.sort((first, second) -> {
                int yOrder = Integer.compare(
                        BlockPos.of(first).getY(), BlockPos.of(second).getY());
                return yOrder == 0 ? Long.compare(first, second) : yOrder;
            });
            Set<Long> occupancy = new HashSet<>();
            for (long packedCell : vacuum.occupiedCells()) {
                BlockPos cell = BlockPos.of(packedCell);
                if (columns.contains(NativeStructureTemplateOccupancy.columnKey(
                        cell.getX(), cell.getZ()))) {
                    occupancy.add(packedCell);
                }
            }
            return new SurfaceTerrainPlan(
                    bases, occupancy, flattenedHeights);
        }

        public void primeHeightmaps(ChunkAccess chunk) {
            if (flattenedHeights.isEmpty()) {
                return;
            }
            Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            WorldgenTerrainHeightmaps.primeTerrain(chunk,
                    (x, z) -> flattenedHeights.getOrDefault(NativeStructureTemplateOccupancy.columnKey(x, z),
                            surface.getFirstAvailable(x & 15, z & 15)),
                    (x, z) -> flattenedHeights.getOrDefault(NativeStructureTemplateOccupancy.columnKey(x, z),
                            floor.getFirstAvailable(x & 15, z & 15)));
        }
    }

    private static final class TemplateCellBudget {
        private final int maximum;
        private int consumed;

        private TemplateCellBudget(int maximum) {
            if (maximum < 0) {
                throw new IllegalArgumentException(
                        "Native structure surface template budget cannot be negative");
            }
            this.maximum = maximum;
        }

        private void consume(int amount) {
            if (amount < 0 || consumed > maximum - amount) {
                throw TemplateBudgetExceeded.INSTANCE;
            }
            consumed += amount;
        }
    }

    private static final class TemplateBudgetExceeded extends RuntimeException {
        private static final TemplateBudgetExceeded INSTANCE =
                new TemplateBudgetExceeded();

        private TemplateBudgetExceeded() {
            super(null, null, false, false);
        }
    }
}
