package art.arcane.iris.nativegen;

import art.arcane.iris.generation.mantle.StructureCarveEnvelope;
import art.arcane.iris.generation.mantle.StructureCarvingFootprint;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.structure.placement.IrisStructureCarveShape;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureTerrainMode;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class NativeStructureTerrainIntegrator {
    private static final long CARVE_CEILING_ROLL_SIGNATURE = 0x2A17L;
    private static final long CARVE_FLOOR_ROLL_SIGNATURE = 0x5B3DL;
    private static final long CARVE_LOBE_SIGNATURE = 0x7C41L;
    private static final int MAX_CACHED_CARVE_CELLS = 2_000_000;
    private static final int MAX_CARVE_COLUMNS = 2_000_000;
    private static final int MAX_TEMPLATE_OCCUPANCY_CELLS = 4_194_304;
    private static final int SOURCE_BURY_HORIZONTAL_RADIUS = 6;
    private static final int SOURCE_BURY_VERTICAL_RADIUS = 12;
    private static final int SOURCE_ENCAPSULATE_RADIUS = 12;
    private static final int SOURCE_JUNCTION_RADIUS = 12;
    private static final int SOURCE_MATERIAL_SAMPLE_RADIUS = 8;
    private static final List<Block> TEMPLATE_VOID_BLOCKS = List.of(Blocks.AIR, Blocks.STRUCTURE_VOID);
    private static final Set<Block> VANILLA_SOURCE_TERRAIN_BLOCKS = Set.of(
            Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
            Blocks.TUFF, Blocks.DEEPSLATE, Blocks.NETHERRACK, Blocks.BASALT,
            Blocks.BLACKSTONE, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
            Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD,
            Blocks.MOSS_BLOCK, Blocks.SAND, Blocks.RED_SAND, Blocks.TERRACOTTA,
            Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM, Blocks.SNOW_BLOCK,
            Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SOUL_SAND,
            Blocks.SOUL_SOIL, Blocks.END_STONE, Blocks.GRAVEL, Blocks.CLAY,
            Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK, Blocks.SANDSTONE,
            Blocks.RED_SANDSTONE, Blocks.SCULK);
    // Weakly keyed on the StructureStart so retired starts (world unload, generation done)
    // are collectable instead of pinned forever by a process-wide static; values hold only
    // primitives, so there is no value-to-key cycle defeating the weak keys.
    private static final Map<StructureStart, Map<Integer, CachedCarveFootprint>> CARVE_FOOTPRINTS =
            new WeakHashMap<>(16);
    private static final ConcurrentHashMap<CarveFootprintKey, CompletableFuture<StructureCarvingFootprint>>
            CARVE_FOOTPRINT_BUILDS = new ConcurrentHashMap<>();
    private static int cachedCarveCells;

    private NativeStructureTerrainIntegrator() {
    }

    public static IrisStructureTerrain resolveNativeTerrain(StructureStart start,
                                                            IrisStructureTerrain configuredTerrain) {
        return configuredTerrain == null
                ? new IrisStructureTerrain().setMode(IrisStructureTerrainMode.SOURCE)
                : configuredTerrain;
    }

    static void integrateTerrain(WorldGenLevel world, BoundingBox area, String structureId,
                                 StructureStart start, IrisStructureTerrain configuredTerrain,
                                 NativeStructurePostProcessor.PaletteBlockResolver paletteBlockResolver) {
        SourceTerrainSnapshot sourceTerrain = requiresSourceTerrainFill(start, configuredTerrain)
                ? captureSourceTerrain(world, area, start, configuredTerrain) : null;
        integrateTerrain(world, area, structureId, start, configuredTerrain,
                paletteBlockResolver, sourceTerrain);
    }

    static void integrateTerrain(WorldGenLevel world, BoundingBox area, String structureId,
                                 StructureStart start, IrisStructureTerrain configuredTerrain,
                                 NativeStructurePostProcessor.PaletteBlockResolver paletteBlockResolver,
                                 SourceTerrainSnapshot sourceTerrain) {
        IrisStructureTerrain terrain = configuredTerrain == null
                ? new IrisStructureTerrain().setMode(IrisStructureTerrainMode.SOURCE)
                : configuredTerrain;
        IrisStructureTerrainMode mode = terrain.resolvedMode();
        if (mode == IrisStructureTerrainMode.SOURCE) {
            integrateSourceTerrain(world, area, start, sourceTerrain);
            return;
        }
        if (mode == IrisStructureTerrainMode.PRESERVE) {
            return;
        }
        if (mode == IrisStructureTerrainMode.VACUUM || mode == IrisStructureTerrainMode.FLATTEN) {
            return;
        }
        if (mode == IrisStructureTerrainMode.ENCASE) {
            encasePieces(world, area, structureId, start, terrain, paletteBlockResolver);
            return;
        }
        if (mode != IrisStructureTerrainMode.BORE && mode != IrisStructureTerrainMode.FORCE_CARVE) {
            throw new IllegalStateException("Native structure terrain mode " + mode
                    + " is not implemented for '" + structureId + "'");
        }
        IrisStructureCarveShape shape = mode == IrisStructureTerrainMode.BORE
                ? IrisStructureCarveShape.BOX : terrain.resolvedShape();
        if (shape == IrisStructureCarveShape.BOX) {
            carvePieceBoxes(world, area, start, terrain);
            return;
        }
        carveOrganicColumns(world, area, organicCarve(
                carveFootprint(start, Math.max(0, terrain.getHorizontalPadding()),
                        () -> world.getLevel().getStructureManager()),
                terrain, shape, carveNoiseIdentity(world, structureId, start)));
    }

    static SourceTerrainSnapshot captureSourceTerrain(
            WorldGenLevel world, BoundingBox area, List<TerrainTarget> targets) {
        BitSet requiredLayers = new BitSet(area.getYSpan());
        boolean requiresSnapshot = false;
        for (TerrainTarget target : targets) {
            if (target != null && requiresSourceTerrainFill(target.start(), target.terrain())) {
                requiresSnapshot = true;
                markSourceTerrainLayers(area, target.start(), target.terrain(), requiredLayers);
            }
        }
        return requiresSnapshot ? SourceTerrainSnapshot.capture(world, area, requiredLayers) : null;
    }

    private static SourceTerrainSnapshot captureSourceTerrain(
            WorldGenLevel world, BoundingBox area, StructureStart start,
            IrisStructureTerrain configuredTerrain) {
        BitSet requiredLayers = new BitSet(area.getYSpan());
        markSourceTerrainLayers(area, start, configuredTerrain, requiredLayers);
        return SourceTerrainSnapshot.capture(world, area, requiredLayers);
    }

    private static void markSourceTerrainLayers(
            BoundingBox area, StructureStart start, IrisStructureTerrain configuredTerrain,
            BitSet requiredLayers) {
        if (!requiresSourceTerrainFill(start, configuredTerrain)) {
            return;
        }
        TerrainAdjustment adjustment = start.getStructure().terrainAdaptation();
        for (StructurePiece piece : start.getPieces()) {
            if (!isSourceRigidPiece(piece)) {
                continue;
            }
            BoundingBox bounds = piece.getBoundingBox();
            long horizontalDistanceSquared = minimumHorizontalDistanceSquared(area, bounds);
            if (adjustment == TerrainAdjustment.BURY) {
                long remainingDistanceSquared = (long) SOURCE_BURY_VERTICAL_RADIUS
                        * SOURCE_BURY_VERTICAL_RADIUS - 1L - horizontalDistanceSquared * 4L;
                if (remainingDistanceSquared < 0L) {
                    continue;
                }
                int verticalRadius = (int) Math.floor(Math.sqrt(remainingDistanceSquared));
                long groundY = bounds.minY();
                if (piece instanceof PoolElementStructurePiece poolPiece) {
                    groundY += poolPiece.getGroundLevelDelta();
                }
                markLayers(area, requiredLayers,
                        groundY - verticalRadius, groundY + verticalRadius);
            } else {
                long remainingDistanceSquared = (long) SOURCE_ENCAPSULATE_RADIUS
                        * SOURCE_ENCAPSULATE_RADIUS - 1L - horizontalDistanceSquared;
                if (remainingDistanceSquared < 0L) {
                    continue;
                }
                int verticalRadius = (int) Math.floor(Math.sqrt(remainingDistanceSquared));
                markLayers(area, requiredLayers,
                        (long) bounds.minY() - verticalRadius,
                        (long) bounds.maxY() + verticalRadius);
            }
        }
        markSourceJunctionLayers(area, start, requiredLayers);
    }

    private static void markSourceJunctionLayers(
            BoundingBox area, StructureStart start, BitSet requiredLayers) {
        Set<JunctionAnchor> anchors = sourceJunctionAnchors(start);
        for (JunctionAnchor anchor : anchors) {
            long deltaX = intervalDistance(area.minX(), area.maxX(), anchor.x(), anchor.x());
            long deltaZ = intervalDistance(area.minZ(), area.maxZ(), anchor.z(), anchor.z());
            long horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
            long remainingDistanceSquared = (long) SOURCE_JUNCTION_RADIUS
                    * SOURCE_JUNCTION_RADIUS - 1L - horizontalDistanceSquared;
            if (remainingDistanceSquared < 1L) {
                continue;
            }
            int verticalRadius = (int) Math.floor(Math.sqrt(remainingDistanceSquared));
            markLayers(area, requiredLayers,
                    (long) anchor.y() - verticalRadius, (long) anchor.y() - 1L);
        }
    }

    private static long minimumHorizontalDistanceSquared(BoundingBox area, BoundingBox bounds) {
        long deltaX = intervalDistance(area.minX(), area.maxX(), bounds.minX(), bounds.maxX());
        long deltaZ = intervalDistance(area.minZ(), area.maxZ(), bounds.minZ(), bounds.maxZ());
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static long intervalDistance(int firstMin, int firstMax, int secondMin, int secondMax) {
        if (firstMax < secondMin) {
            return (long) secondMin - firstMax;
        }
        if (firstMin > secondMax) {
            return (long) firstMin - secondMax;
        }
        return 0L;
    }

    private static void markLayers(
            BoundingBox area, BitSet requiredLayers, long minimumY, long maximumY) {
        long clippedMinimumY = Math.max(area.minY(), minimumY);
        long clippedMaximumY = Math.min(area.maxY(), maximumY);
        if (clippedMinimumY > clippedMaximumY) {
            return;
        }
        int fromIndex = Math.toIntExact(clippedMinimumY - area.minY());
        int toIndex = Math.toIntExact(clippedMaximumY - area.minY() + 1L);
        requiredLayers.set(fromIndex, toIndex);
    }

    private static boolean requiresSourceTerrainFill(
            StructureStart start, IrisStructureTerrain configuredTerrain) {
        if (start == null || !start.isValid()) {
            return false;
        }
        IrisStructureTerrain terrain = configuredTerrain == null
                ? new IrisStructureTerrain().setMode(IrisStructureTerrainMode.SOURCE)
                : configuredTerrain;
        if (terrain.resolvedMode() != IrisStructureTerrainMode.SOURCE) {
            return false;
        }
        TerrainAdjustment adjustment = start.getStructure().terrainAdaptation();
        return adjustment == TerrainAdjustment.BURY
                || adjustment == TerrainAdjustment.ENCAPSULATE;
    }

    static boolean clearsLegacyTemplateAir(StructureStart start, IrisStructureTerrain terrain) {
        if (start == null || !start.isValid() || terrain == null) {
            return false;
        }
        IrisStructureTerrainMode mode = terrain.resolvedMode();
        return mode == IrisStructureTerrainMode.ENCASE
                || mode == IrisStructureTerrainMode.SOURCE
                && start.getStructure().terrainAdaptation() != TerrainAdjustment.NONE;
    }

    private static void integrateSourceTerrain(WorldGenLevel world, BoundingBox area,
                                               StructureStart start,
                                               SourceTerrainSnapshot sourceTerrain) {
        if (start == null || !start.isValid()) {
            return;
        }
        TerrainAdjustment adjustment = start.getStructure().terrainAdaptation();
        if (adjustment == TerrainAdjustment.BURY) {
            fillBuriedTerrain(world, area, start, requireSourceTerrain(
                    world, area, start, sourceTerrain));
        } else if (adjustment == TerrainAdjustment.ENCAPSULATE) {
            fillEncapsulatedTerrain(world, area, start, requireSourceTerrain(
                    world, area, start, sourceTerrain));
        }
    }

    private static SourceTerrainSnapshot requireSourceTerrain(
            WorldGenLevel world, BoundingBox area, StructureStart start,
            SourceTerrainSnapshot sourceTerrain) {
        return sourceTerrain == null
                ? captureSourceTerrain(world, area, start, null) : sourceTerrain;
    }

    private static void fillBuriedTerrain(WorldGenLevel world, BoundingBox area,
                                          StructureStart start,
                                          SourceTerrainSnapshot sourceTerrain) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (StructurePiece piece : start.getPieces()) {
            if (!isSourceRigidPiece(piece)) {
                continue;
            }
            BoundingBox bounds = piece.getBoundingBox();
            int groundY = bounds.minY();
            if (piece instanceof PoolElementStructurePiece poolPiece) {
                groundY += poolPiece.getGroundLevelDelta();
            }
            int minX = Math.max(area.minX(), bounds.minX() - SOURCE_BURY_HORIZONTAL_RADIUS);
            int maxX = Math.min(area.maxX(), bounds.maxX() + SOURCE_BURY_HORIZONTAL_RADIUS);
            int minY = Math.max(area.minY(), groundY - SOURCE_BURY_VERTICAL_RADIUS);
            int maxY = Math.min(area.maxY(), groundY + SOURCE_BURY_VERTICAL_RADIUS);
            int minZ = Math.max(area.minZ(), bounds.minZ() - SOURCE_BURY_HORIZONTAL_RADIUS);
            int maxZ = Math.min(area.maxZ(), bounds.maxZ() + SOURCE_BURY_HORIZONTAL_RADIUS);
            for (int x = minX; x <= maxX; x++) {
                int outX = outset(x, bounds.minX(), bounds.maxX());
                for (int z = minZ; z <= maxZ; z++) {
                    int outZ = outset(z, bounds.minZ(), bounds.maxZ());
                    for (int y = minY; y <= maxY; y++) {
                        int vertical = Math.abs(y - groundY);
                        if (!insideBurialEnvelope(outX, vertical, outZ)) {
                            continue;
                        }
                        fillEncaseable(world, position.set(x, y, z), sourceTerrain);
                    }
                }
            }
        }
        fillSourceJunctionTerrain(world, area, start, position, sourceTerrain);
    }

    private static void fillEncapsulatedTerrain(WorldGenLevel world, BoundingBox area,
                                                StructureStart start,
                                                SourceTerrainSnapshot sourceTerrain) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (StructurePiece piece : start.getPieces()) {
            if (!isSourceRigidPiece(piece)) {
                continue;
            }
            BoundingBox bounds = piece.getBoundingBox();
            int minX = Math.max(area.minX(), bounds.minX() - SOURCE_ENCAPSULATE_RADIUS);
            int maxX = Math.min(area.maxX(), bounds.maxX() + SOURCE_ENCAPSULATE_RADIUS);
            int minY = Math.max(area.minY(), bounds.minY() - SOURCE_ENCAPSULATE_RADIUS);
            int maxY = Math.min(area.maxY(), bounds.maxY() + SOURCE_ENCAPSULATE_RADIUS);
            int minZ = Math.max(area.minZ(), bounds.minZ() - SOURCE_ENCAPSULATE_RADIUS);
            int maxZ = Math.min(area.maxZ(), bounds.maxZ() + SOURCE_ENCAPSULATE_RADIUS);
            for (int x = minX; x <= maxX; x++) {
                int outX = outset(x, bounds.minX(), bounds.maxX());
                for (int z = minZ; z <= maxZ; z++) {
                    int outZ = outset(z, bounds.minZ(), bounds.maxZ());
                    for (int y = minY; y <= maxY; y++) {
                        int outY = outset(y, bounds.minY(), bounds.maxY());
                        if (!insideEncapsulationEnvelope(outX, outY, outZ)) {
                            continue;
                        }
                        fillEncaseable(world, position.set(x, y, z), sourceTerrain);
                    }
                }
            }
        }
        fillSourceJunctionTerrain(world, area, start, position, sourceTerrain);
    }

    static boolean insideBurialEnvelope(int outX, int verticalDistance, int outZ) {
        long horizontalSquared = (long) outX * outX + (long) outZ * outZ;
        long verticalSquared = (long) verticalDistance * verticalDistance;
        return horizontalSquared * 4L + verticalSquared
                < (long) SOURCE_BURY_VERTICAL_RADIUS * SOURCE_BURY_VERTICAL_RADIUS;
    }

    static boolean insideEncapsulationEnvelope(int outX, int outY, int outZ) {
        return (long) outX * outX + (long) outY * outY + (long) outZ * outZ
                < (long) SOURCE_ENCAPSULATE_RADIUS * SOURCE_ENCAPSULATE_RADIUS;
    }

    static boolean isSourceRigidPiece(StructurePiece piece) {
        if (piece == null) {
            return false;
        }
        return !(piece instanceof PoolElementStructurePiece poolPiece)
                || poolPiece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID;
    }

    static boolean insideSourceJunctionEnvelope(int deltaX, int deltaY, int deltaZ) {
        if (deltaY >= 0) {
            return false;
        }
        return (long) deltaX * deltaX + (long) deltaY * deltaY + (long) deltaZ * deltaZ
                < (long) SOURCE_JUNCTION_RADIUS * SOURCE_JUNCTION_RADIUS;
    }

    private static void fillSourceJunctionTerrain(WorldGenLevel world, BoundingBox area,
                                                  StructureStart start,
                                                  BlockPos.MutableBlockPos position,
                                                  SourceTerrainSnapshot sourceTerrain) {
        Set<JunctionAnchor> anchors = sourceJunctionAnchors(start);
        for (JunctionAnchor anchor : anchors) {
            int minX = Math.max(area.minX(), anchor.x() - SOURCE_JUNCTION_RADIUS + 1);
            int maxX = Math.min(area.maxX(), anchor.x() + SOURCE_JUNCTION_RADIUS - 1);
            int minY = Math.max(area.minY(), anchor.y() - SOURCE_JUNCTION_RADIUS + 1);
            int maxY = Math.min(area.maxY(), anchor.y() - 1);
            int minZ = Math.max(area.minZ(), anchor.z() - SOURCE_JUNCTION_RADIUS + 1);
            int maxZ = Math.min(area.maxZ(), anchor.z() + SOURCE_JUNCTION_RADIUS - 1);
            for (int x = minX; x <= maxX; x++) {
                int deltaX = x - anchor.x();
                for (int z = minZ; z <= maxZ; z++) {
                    int deltaZ = z - anchor.z();
                    for (int y = minY; y <= maxY; y++) {
                        if (insideSourceJunctionEnvelope(deltaX, y - anchor.y(), deltaZ)) {
                            fillEncaseable(world, position.set(x, y, z), sourceTerrain);
                        }
                    }
                }
            }
        }
    }

    private static Set<JunctionAnchor> sourceJunctionAnchors(StructureStart start) {
        Set<JunctionAnchor> anchors = new HashSet<>();
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                continue;
            }
            for (JigsawJunction junction : poolPiece.getJunctions()) {
                anchors.add(new JunctionAnchor(
                        junction.getSourceX(), junction.getSourceGroundY(), junction.getSourceZ()));
            }
        }
        return anchors;
    }

    private static int outset(int value, int minimum, int maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return Math.max(0, value - maximum);
    }

    private static void fillEncaseable(WorldGenLevel world, BlockPos position,
                                       SourceTerrainSnapshot sourceTerrain) {
        if (isEncaseable(world.getBlockState(position))) {
            world.setBlock(position, sourceEncaseBlock(world, position, sourceTerrain), 2);
        }
    }

    static BlockState sourceEncaseBlock(WorldGenLevel world, BlockPos position) {
        int minX = Math.subtractExact(position.getX(), SOURCE_MATERIAL_SAMPLE_RADIUS);
        int minZ = Math.subtractExact(position.getZ(), SOURCE_MATERIAL_SAMPLE_RADIUS);
        int maxX = Math.addExact(position.getX(), SOURCE_MATERIAL_SAMPLE_RADIUS);
        int maxZ = Math.addExact(position.getZ(), SOURCE_MATERIAL_SAMPLE_RADIUS);
        BoundingBox sampleArea = new BoundingBox(minX, position.getY(), minZ,
                maxX, position.getY(), maxZ);
        BitSet requiredLayers = new BitSet(1);
        requiredLayers.set(0);
        SourceTerrainSnapshot sourceTerrain = SourceTerrainSnapshot.capture(
                world, sampleArea, requiredLayers);
        return sourceEncaseBlock(world, position, sourceTerrain);
    }

    static BlockState sourceEncaseBlock(WorldGenLevel world, BlockPos position,
                                        SourceTerrainSnapshot sourceTerrain) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int distance = 1; distance <= SOURCE_MATERIAL_SAMPLE_RADIUS; distance++) {
            BlockState sampled = sourceTerrain.stateAt(probe.set(
                    position.getX() - distance, position.getY(), position.getZ()));
            if (sampled != null) {
                return sampled;
            }
            sampled = sourceTerrain.stateAt(probe.set(
                    position.getX() + distance, position.getY(), position.getZ()));
            if (sampled != null) {
                return sampled;
            }
            sampled = sourceTerrain.stateAt(probe.set(
                    position.getX(), position.getY(), position.getZ() - distance));
            if (sampled != null) {
                return sampled;
            }
            sampled = sourceTerrain.stateAt(probe.set(
                    position.getX(), position.getY(), position.getZ() + distance));
            if (sampled != null) {
                return sampled;
            }
        }
        return defaultEncaseBlock(world, position.getY());
    }

    private static BlockState sourceTerrainBlock(BlockState state) {
        if (!state.isSolid() || NativeStructureVegetationClearer.isTreeBlock(state)) {
            return null;
        }
        return VANILLA_SOURCE_TERRAIN_BLOCKS.contains(state.getBlock())
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.SUBSTRATE_OVERWORLD)
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.TERRACOTTA)
                || state.is(BlockTags.MUD)
                || state.is(BlockTags.MOSS_BLOCKS)
                || state.is(BlockTags.GRASS_BLOCKS)
                || state.is(BlockTags.NYLIUM)
                || state.is(BlockTags.SNOW)
                || state.is(BlockTags.ICE)
                || state.is(BlockTags.CORAL_BLOCKS)
                || state.is(BlockTags.SOUL_FIRE_BASE_BLOCKS)
                ? state : null;
    }

    static List<BoundingBox> contentPieceBounds(StructureStart start) {
        List<BoundingBox> bounds = new ArrayList<>(start.getPieces().size());
        for (StructurePiece piece : start.getPieces()) {
            bounds.add(piece.getBoundingBox());
        }
        return List.copyOf(bounds);
    }

    /**
     * Per-column occupancy of the whole start, cached because a single structure spans many chunks and
     * every one of them carves against the same shrinkwrapped footprint.
     */
    static StructureCarvingFootprint carveFootprint(StructureStart start, int horizontalPadding,
                                                    Supplier<StructureTemplateManager> templates) {
        CarveFootprintKey key = new CarveFootprintKey(start, horizontalPadding);
        StructureCarvingFootprint cached = cachedCarveFootprint(key);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<StructureCarvingFootprint> build = new CompletableFuture<>();
        CompletableFuture<StructureCarvingFootprint> active = CARVE_FOOTPRINT_BUILDS.putIfAbsent(key, build);
        if (active != null) {
            return awaitCarveFootprint(active);
        }
        // Close the check-then-claim window: a racer whose cache check missed before the
        // previous builder cached can claim the build slot after that builder retired it,
        // and would rebuild a second instance without this re-check.
        StructureCarvingFootprint published = cachedCarveFootprint(key);
        if (published != null) {
            build.complete(published);
            CARVE_FOOTPRINT_BUILDS.remove(key, build);
            return published;
        }
        try {
            StructureCarvingFootprint footprint = StructureCarvingFootprint.fromColumns(
                    sink -> emitCarveColumns(start, templates, sink), horizontalPadding, MAX_CARVE_COLUMNS);
            if (footprint == null) {
                throw new IllegalStateException("Native structure carve footprint is empty or exceeds "
                        + MAX_CARVE_COLUMNS + " columns");
            }
            cacheCarveFootprint(key, footprint);
            build.complete(footprint);
            return footprint;
        } catch (RuntimeException | Error error) {
            build.completeExceptionally(error);
            throw error;
        } finally {
            CARVE_FOOTPRINT_BUILDS.remove(key, build);
        }
    }

    static int cachedCarveFootprintCells() {
        synchronized (CARVE_FOOTPRINTS) {
            cachedCarveCells = liveCarveCells();
            return cachedCarveCells;
        }
    }

    private static int liveCarveCells() {
        int total = 0;
        for (Map<Integer, CachedCarveFootprint> perStart : CARVE_FOOTPRINTS.values()) {
            for (CachedCarveFootprint cached : perStart.values()) {
                total += cached.cells();
            }
        }
        return total;
    }

    static int maximumCachedCarveFootprintCells() {
        return MAX_CACHED_CARVE_CELLS;
    }

    private static StructureCarvingFootprint cachedCarveFootprint(CarveFootprintKey key) {
        synchronized (CARVE_FOOTPRINTS) {
            Map<Integer, CachedCarveFootprint> perStart = CARVE_FOOTPRINTS.get(key.start());
            CachedCarveFootprint cached = perStart == null ? null : perStart.get(key.padding());
            return cached == null ? null : cached.footprint();
        }
    }

    private static StructureCarvingFootprint awaitCarveFootprint(
            CompletableFuture<StructureCarvingFootprint> future) {
        return NativeBuildFutures.awaitBuild(future, "Native structure carve footprint build");
    }

    private static void cacheCarveFootprint(CarveFootprintKey key,
                                            StructureCarvingFootprint footprint) {
        int cells = Math.multiplyExact(footprint.width(), footprint.depth());
        synchronized (CARVE_FOOTPRINTS) {
            // Weak keys expunge silently as the GC drops retired starts; re-derive the live
            // total so the cell budget tracks reality instead of drifting.
            cachedCarveCells = liveCarveCells();
            Map<Integer, CachedCarveFootprint> perStart =
                    CARVE_FOOTPRINTS.computeIfAbsent(key.start(), ignored -> new LinkedHashMap<>(4));
            CachedCarveFootprint previous = perStart.remove(key.padding());
            if (previous != null) {
                cachedCarveCells -= previous.cells();
            }
            Iterator<Map.Entry<StructureStart, Map<Integer, CachedCarveFootprint>>> eviction =
                    CARVE_FOOTPRINTS.entrySet().iterator();
            while (cachedCarveCells + cells > MAX_CACHED_CARVE_CELLS && eviction.hasNext()) {
                Map.Entry<StructureStart, Map<Integer, CachedCarveFootprint>> victim = eviction.next();
                if (victim.getKey() == key.start()) {
                    continue;
                }
                for (CachedCarveFootprint evicted : victim.getValue().values()) {
                    cachedCarveCells -= evicted.cells();
                }
                eviction.remove();
            }
            perStart.put(key.padding(), new CachedCarveFootprint(footprint, cells));
            cachedCarveCells += cells;
        }
    }

    static OrganicCarve organicCarve(StructureCarvingFootprint footprint, IrisStructureTerrain terrain,
                                     IrisStructureCarveShape shape, long identity) {
        double strength = terrain.resolvedErosionStrength();
        double lobeStrength = terrain.resolvedLobeStrength();
        RNG noiseRng = new RNG(identity);
        CNG blob = null;
        CNG ceilingRoll = null;
        CNG floorRoll = null;
        CNG lobe = null;
        if (shape == IrisStructureCarveShape.ERODED && strength > 0D) {
            blob = CNG.signature(noiseRng);
            ceilingRoll = CNG.signature(noiseRng.nextParallelRNG(CARVE_CEILING_ROLL_SIGNATURE));
            floorRoll = CNG.signature(noiseRng.nextParallelRNG(CARVE_FLOOR_ROLL_SIGNATURE));
        }
        if (shape == IrisStructureCarveShape.ERODED && lobeStrength > 0D) {
            // A plain single octave channel: the fractured signature noise has no usable low frequency band.
            lobe = new CNG(noiseRng.nextParallelRNG(CARVE_LOBE_SIGNATURE), 1D, 1);
        }
        return new OrganicCarve(footprint, shape,
                Math.max(0, terrain.getHorizontalPadding()),
                Math.max(0, terrain.getCeilingPadding()),
                Math.max(0, terrain.getFloorPadding()),
                strength, terrain.resolvedErosionFrequency(), blob, ceilingRoll, floorRoll,
                lobe, terrain.resolvedLobeFrequency(), lobeStrength);
    }

    static void carveOrganicColumns(WorldGenLevel world, BoundingBox area, OrganicCarve carve) {
        StructureCarvingFootprint footprint = carve.footprint();
        int minX = Math.max(area.minX(), footprint.minX());
        int maxX = Math.min(area.maxX(), footprint.maxX());
        int minZ = Math.max(area.minZ(), footprint.minZ());
        int maxZ = Math.min(area.maxZ(), footprint.maxZ());
        if (minX > maxX || minZ > maxZ) {
            return;
        }
        boolean eroded = carve.blob() != null;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int index = footprint.indexAt(x, z);
                long horizontalDistanceSquared = footprint.distanceSquaredAt(index);
                double sideReach = StructureCarveEnvelope.lobedSideReach(carve.lobe(),
                        carve.lobeFrequency(), carve.lobeStrength(), x, z, carve.horizontalPadding());
                double sideReachSquared = sideReach * sideReach;
                if (horizontalDistanceSquared > sideReachSquared) {
                    continue;
                }
                double normalizedHorizontal = sideReachSquared == 0D
                        ? 0D : horizontalDistanceSquared / sideReachSquared;
                int sourceMinY = footprint.sourceMinYAt(index);
                int sourceMaxY = footprint.sourceMaxYAt(index);
                double upReach = eroded
                        ? StructureCarveEnvelope.lobedUpReach(carve.lobe(), carve.lobeFrequency(),
                                carve.lobeStrength(), x, z,
                                StructureCarveEnvelope.erodedUpReach(carve.ceilingRoll(),
                                        carve.frequency(), carve.strength(), x, z,
                                        carve.ceilingPadding()))
                        : Math.max(1D, carve.ceilingPadding());
                double floorReach = eroded
                        ? StructureCarveEnvelope.erodedDownReach(carve.floorRoll(), carve.frequency(),
                                carve.strength(), x, z, carve.floorPadding())
                        : carve.floorPadding();
                int columnMinY = Math.max(area.minY(), sourceMinY - (int) Math.floor(floorReach));
                int columnMaxY = Math.min(area.maxY(),
                        sourceMaxY + (eroded ? (int) Math.ceil(upReach) : carve.ceilingPadding()));
                double downReach = Math.max(1D, floorReach);
                for (int y = columnMinY; y <= columnMaxY; y++) {
                    double normalizedVertical = StructureCarveEnvelope.normalizedVerticalDistance(
                            y, sourceMinY, sourceMaxY, upReach, downReach);
                    double distanceSquared = normalizedHorizontal
                            + normalizedVertical * normalizedVertical;
                    if (distanceSquared > 1D) {
                        continue;
                    }
                    if (distanceSquared > 0D && eroded) {
                        double noise = carve.blob().fitDouble(0D, 1D,
                                x * carve.frequency(), y * carve.frequency(), z * carve.frequency());
                        if (!StructureCarveEnvelope.shouldCarveOverboreCell(
                                carve.shape(), distanceSquared, noise, carve.strength())) {
                            continue;
                        }
                    }
                    world.setBlock(position.set(x, y, z), air, 2);
                }
            }
        }
    }

    private static boolean emitCarveColumns(StructureStart start,
                                            Supplier<StructureTemplateManager> templates,
                                            StructureCarvingFootprint.ColumnSink sink) {
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox bounds = piece.getBoundingBox();
            if (!(piece instanceof PoolElementStructurePiece poolPiece)
                    || !emitTemplateColumns(pieceTemplates(poolPiece, templates),
                            poolPiece.getPosition(), poolPiece.getRotation(), bounds, sink)) {
                emitBoxColumns(bounds, sink);
            }
        }
        return true;
    }

    /**
     * Derives per-column vertical extents from the complement of the template's air and structure-void
     * cells, so a carve tracks the actual silhouette instead of the piece's rectangular bounding box.
     */
    static boolean emitTemplateColumns(List<StructureTemplate> templates, BlockPos position,
                                       Rotation rotation, BoundingBox bounds,
                                       StructureCarvingFootprint.ColumnSink sink) {
        if (templates.isEmpty()) {
            return false;
        }
        int width = bounds.getXSpan();
        int depth = bounds.getZSpan();
        long cells = (long) width * depth * bounds.getYSpan();
        if (cells < 1L || cells > MAX_TEMPLATE_OCCUPANCY_CELLS) {
            return false;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        boolean[] voidCells = null;
        for (StructureTemplate template : templates) {
            boolean[] templateVoid = new boolean[(int) cells];
            for (Block ignored : TEMPLATE_VOID_BLOCKS) {
                for (StructureTemplate.StructureBlockInfo info
                        : template.filterBlocks(position, settings, ignored)) {
                    BlockPos voidPosition = info.pos();
                    if (bounds.isInside(voidPosition)) {
                        templateVoid[templateCellIndex(bounds, width, depth,
                                voidPosition.getX(), voidPosition.getY(), voidPosition.getZ())] = true;
                    }
                }
            }
            if (voidCells == null) {
                voidCells = templateVoid;
                continue;
            }
            for (int cell = 0; cell < voidCells.length; cell++) {
                voidCells[cell] &= templateVoid[cell];
            }
        }
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                int minY = Integer.MAX_VALUE;
                int maxY = Integer.MIN_VALUE;
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    if (voidCells[templateCellIndex(bounds, width, depth, x, y, z)]) {
                        continue;
                    }
                    if (minY == Integer.MAX_VALUE) {
                        minY = y;
                    }
                    maxY = y;
                }
                if (minY != Integer.MAX_VALUE) {
                    sink.column(x, z, minY, maxY);
                }
            }
        }
        return true;
    }

    private static void emitBoxColumns(BoundingBox bounds, StructureCarvingFootprint.ColumnSink sink) {
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                sink.column(x, z, bounds.minY(), bounds.maxY());
            }
        }
    }

    private static int templateCellIndex(BoundingBox bounds, int width, int depth,
                                         int x, int y, int z) {
        return ((y - bounds.minY()) * depth + z - bounds.minZ()) * width + x - bounds.minX();
    }

    private static List<StructureTemplate> pieceTemplates(PoolElementStructurePiece poolPiece,
                                                          Supplier<StructureTemplateManager> templates) {
        List<StructureTemplate> resolved = new ArrayList<>(1);
        collectElementTemplates(poolPiece.getElement(), templates, resolved);
        return resolved;
    }

    private static void collectElementTemplates(StructurePoolElement element,
                                                Supplier<StructureTemplateManager> templates,
                                                List<StructureTemplate> resolved) {
        if (element instanceof ListPoolElement listElement) {
            for (StructurePoolElement child : listElement.getElements()) {
                collectElementTemplates(child, templates, resolved);
            }
            return;
        }
        if (element instanceof SinglePoolElement singleElement) {
            resolved.add(NativeStructureReflection.resolveTemplate(singleElement, templates));
        }
    }

    private static long carveNoiseIdentity(WorldGenLevel world, String structureId,
                                           StructureStart start) {
        return world.getSeed()
                ^ ((long) start.getChunkPos().x() * 341873128712L)
                ^ ((long) start.getChunkPos().z() * 132897987541L)
                ^ (structureId == null ? 0 : structureId.hashCode());
    }

    private static void encasePieces(WorldGenLevel world, BoundingBox area, String structureId,
                                     StructureStart start, IrisStructureTerrain terrain,
                                     NativeStructurePostProcessor.PaletteBlockResolver paletteBlockResolver) {
        IrisMaterialPalette palette = terrain.getEncasePalette();
        RNG rng = null;
        if (palette != null) {
            Objects.requireNonNull(paletteBlockResolver,
                    "Native structure encase palette requires a platform block resolver");
            rng = new RNG(world.getSeed() ^ (structureId == null ? 0 : structureId.hashCode()));
        }
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (BoundingBox bounds : contentPieceBounds(start)) {
            BoundingBox shell = paddedPieceArea(area, bounds, terrain);
            if (shell == null) {
                continue;
            }
            for (int x = shell.minX(); x <= shell.maxX(); x++) {
                for (int z = shell.minZ(); z <= shell.maxZ(); z++) {
                    for (int y = shell.minY(); y <= shell.maxY(); y++) {
                        BlockState existing = world.getBlockState(position.set(x, y, z));
                        if (!isEncaseable(existing)) {
                            continue;
                        }
                        BlockState fill = palette == null
                                ? defaultEncaseBlock(world, y)
                                : Objects.requireNonNull(
                                        paletteBlockResolver.resolve(palette, rng, x, y, z),
                                        "Encase palette returned no block for " + structureId + " at "
                                                + x + "," + y + "," + z);
                        world.setBlock(position, fill, 2);
                    }
                }
            }
        }
    }

    static boolean isEncaseable(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty();
    }

    static BlockState defaultEncaseBlock(int y) {
        return y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    static BlockState defaultEncaseBlock(WorldGenLevel world, int y) {
        ServerLevel level = world == null ? null : world.getLevel();
        if (level == null) {
            return defaultEncaseBlock(y);
        }
        Holder<DimensionType> dimensionType = level.dimensionTypeRegistration();
        ResourceKey<DimensionType> dimensionTypeKey = dimensionType.unwrapKey().orElse(null);
        return defaultEncaseBlock(level.dimension(), dimensionTypeKey, dimensionType.value(), y);
    }

    static BlockState defaultEncaseBlock(ResourceKey<Level> dimension, int y) {
        return defaultEncaseBlock(dimension, null, null, y);
    }

    static BlockState defaultEncaseBlock(ResourceKey<Level> dimension,
                                         ResourceKey<DimensionType> dimensionTypeKey,
                                         DimensionType dimensionType, int y) {
        if (Level.NETHER.equals(dimension)) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (Level.END.equals(dimension)) {
            return Blocks.END_STONE.defaultBlockState();
        }
        if (BuiltinDimensionTypes.NETHER.equals(dimensionTypeKey)
                || dimensionType != null && dimensionType.hasCeiling() && !dimensionType.hasSkyLight()) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (BuiltinDimensionTypes.END.equals(dimensionTypeKey)
                || dimensionType != null && (dimensionType.hasEnderDragonFight()
                || dimensionType.hasEndFlashes())) {
            return Blocks.END_STONE.defaultBlockState();
        }
        return defaultEncaseBlock(y);
    }

    private static void carvePieceBoxes(WorldGenLevel world, BoundingBox area, StructureStart start,
                                        IrisStructureTerrain terrain) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (BoundingBox bounds : contentPieceBounds(start)) {
            BoundingBox carve = paddedPieceArea(area, bounds, terrain);
            if (carve == null) {
                continue;
            }
            for (int x = carve.minX(); x <= carve.maxX(); x++) {
                for (int z = carve.minZ(); z <= carve.maxZ(); z++) {
                    for (int y = carve.minY(); y <= carve.maxY(); y++) {
                        world.setBlock(position.set(x, y, z), air, 2);
                    }
                }
            }
        }
    }

    private static BoundingBox paddedPieceArea(BoundingBox area, BoundingBox bounds,
                                               IrisStructureTerrain terrain) {
        int horizontalPadding = Math.max(0, terrain.getHorizontalPadding());
        int minX = Math.max(area.minX(), bounds.minX() - horizontalPadding);
        int minY = Math.max(area.minY(), bounds.minY() - Math.max(0, terrain.getFloorPadding()));
        int minZ = Math.max(area.minZ(), bounds.minZ() - horizontalPadding);
        int maxX = Math.min(area.maxX(), bounds.maxX() + horizontalPadding);
        int maxY = Math.min(area.maxY(), bounds.maxY() + Math.max(0, terrain.getCeilingPadding()));
        int maxZ = Math.min(area.maxZ(), bounds.maxZ() + horizontalPadding);
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static void clearLegacyTemplateAir(WorldGenLevel world, BoundingBox area,
                                       StructureStart start,
                                       Supplier<StructureTemplateManager> templates) {
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)
                    || poolPiece.getElement().getProjection() != StructureTemplatePool.Projection.RIGID
                    || !intersects(poolPiece.getBoundingBox(), area)) {
                continue;
            }
            int groundY = poolPiece.getBoundingBox().minY() + poolPiece.getGroundLevelDelta();
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(poolPiece.getRotation())
                    .setBoundingBox(area);
            clearLegacyTemplateAir(world, poolPiece.getElement(), poolPiece.getPosition(),
                    groundY, settings, templates);
        }
    }

    private static void clearLegacyTemplateAir(WorldGenLevel world, StructurePoolElement element,
                                               BlockPos position, int groundY,
                                               StructurePlaceSettings settings,
                                               Supplier<StructureTemplateManager> templates) {
        if (element instanceof ListPoolElement listElement) {
            for (StructurePoolElement child : listElement.getElements()) {
                clearLegacyTemplateAir(world, child, position, groundY, settings, templates);
            }
            return;
        }
        if (!(element instanceof LegacySinglePoolElement legacyElement)) {
            return;
        }
        StructureTemplate template = NativeStructureReflection.resolveTemplate(legacyElement, templates);
        clearTemplateAir(world, template, position, groundY, settings);
    }

    static void clearTemplateAir(WorldGenLevel world, StructureTemplate template,
                                 BlockPos position, int groundY,
                                 StructurePlaceSettings settings) {
        List<StructureTemplate.StructureBlockInfo> airBlocks = template.filterBlocks(
                position, settings, Blocks.AIR);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (StructureTemplate.StructureBlockInfo airBlock : airBlocks) {
            BlockPos airPosition = airBlock.pos();
            BlockState existingState = world.getBlockState(airPosition);
            if (shouldClearLegacyAir(
                    airPosition.getY(), groundY, existingState.isAir())
                    && !NativeStructureVegetationClearer.isTreeBlock(existingState)) {
                world.setBlock(airPosition, air, 2);
            }
        }
    }

    static boolean shouldClearLegacyAir(int airY, int groundY, boolean existingAir) {
        return airY >= groundY && !existingAir;
    }

    static boolean intersects(BoundingBox first, BoundingBox second) {
        return first.maxX() >= second.minX() && first.minX() <= second.maxX()
                && first.maxY() >= second.minY() && first.minY() <= second.maxY()
                && first.maxZ() >= second.minZ() && first.minZ() <= second.maxZ();
    }

    // StructureStart has no value equality, so the key pins the exact start instance a chunk carves against.
    private record CarveFootprintKey(StructureStart start, int padding) {
    }

    private record CachedCarveFootprint(StructureCarvingFootprint footprint, int cells) {
    }

    private record JunctionAnchor(int x, int y, int z) {
    }

    static final class SourceTerrainSnapshot {
        private final BoundingBox area;
        private final int width;
        private final BlockState[][] statesByLayer;
        private final int sampledCells;

        private SourceTerrainSnapshot(BoundingBox area, int width,
                                      BlockState[][] statesByLayer, int sampledCells) {
            this.area = area;
            this.width = width;
            this.statesByLayer = statesByLayer;
            this.sampledCells = sampledCells;
        }

        static SourceTerrainSnapshot capture(
                WorldGenLevel world, BoundingBox area, BitSet requiredLayers) {
            Objects.requireNonNull(world, "Source terrain snapshot requires a generation level");
            Objects.requireNonNull(area, "Source terrain snapshot requires writable bounds");
            Objects.requireNonNull(requiredLayers, "Source terrain snapshot requires sampled layers");
            int width = area.getXSpan();
            int depth = area.getZSpan();
            int height = area.getYSpan();
            int horizontalCells = Math.multiplyExact(width, depth);
            BlockState[][] statesByLayer = new BlockState[height][];
            int sampledCells = 0;
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            for (int layer = requiredLayers.nextSetBit(0);
                 layer >= 0; layer = requiredLayers.nextSetBit(layer + 1)) {
                if (layer >= height) {
                    throw new IllegalArgumentException(
                            "Source terrain snapshot layer exceeds writable bounds: " + layer);
                }
                BlockState[] states = new BlockState[horizontalCells];
                statesByLayer[layer] = states;
                sampledCells = Math.addExact(sampledCells, horizontalCells);
                int y = Math.addExact(area.minY(), layer);
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    for (int x = area.minX(); x <= area.maxX(); x++) {
                        int index = (z - area.minZ()) * width + x - area.minX();
                        states[index] = sourceTerrainBlock(
                                world.getBlockState(position.set(x, y, z)));
                    }
                }
            }
            return new SourceTerrainSnapshot(new BoundingBox(
                    area.minX(), area.minY(), area.minZ(),
                    area.maxX(), area.maxY(), area.maxZ()), width,
                    statesByLayer, sampledCells);
        }

        BlockState stateAt(BlockPos position) {
            if (!area.isInside(position)) {
                return null;
            }
            BlockState[] states = statesByLayer[position.getY() - area.minY()];
            if (states == null) {
                return null;
            }
            int index = (position.getZ() - area.minZ()) * width
                    + position.getX() - area.minX();
            return states[index];
        }

        int sampledCells() {
            return sampledCells;
        }
    }

    record OrganicCarve(StructureCarvingFootprint footprint, IrisStructureCarveShape shape,
                        int horizontalPadding, int ceilingPadding, int floorPadding,
                        double strength, double frequency, CNG blob, CNG ceilingRoll, CNG floorRoll,
                        CNG lobe, double lobeFrequency, double lobeStrength) {
    }

    public record TerrainTarget(String structureId, StructureStart start,
                                IrisStructureTerrain terrain) {
    }
}
