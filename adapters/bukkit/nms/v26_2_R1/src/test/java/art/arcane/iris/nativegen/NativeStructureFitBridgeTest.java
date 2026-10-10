package art.arcane.iris.nativegen;

import art.arcane.iris.engine.framework.NativeStartMemo;
import art.arcane.iris.engine.framework.NativeStructureReach;
import art.arcane.iris.engine.framework.nativefit.NativeFitPiece;
import art.arcane.iris.engine.framework.nativefit.NativeStructureFitting;
import art.arcane.iris.engine.framework.nativefit.NativeTemplateCells;
import com.mojang.datafixers.util.Either;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockStateMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The platform half of the structure fit: what the bridge reads off the platform's starts (processed template cells,
 * template columns, legacy template air) and the blocks the built-in {@code iris:structure} fit then changes through
 * it, on in-memory worlds. The fit's own rules are tested in core against stated cells.
 */
public class NativeStructureFitBridgeTest {
    private static final long TEST_SEED = 8675309L;
    private static final int WORLD_MIN_Y = -64;
    private static final int WORLD_HEIGHT = 384;
    private static final int SUPPORT_SURFACE_Y = 62;

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void shallowUndergroundBeardBoxDoesNotMutateTheTopSurface() throws Exception {
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 1, 0, Blocks.DEEPSLATE_BRICKS.defaultBlockState())))),
                new BoundingBox(0, 67, 0, 0, 74, 0), 1, Rotation.NONE);
        Structure structure = new DesertPyramidStructure(
                new Structure.StructureSettings(
                        HolderSet.empty(), Map.of(),
                        GenerationStep.Decoration.UNDERGROUND_DECORATION,
                        TerrainAdjustment.BEARD_BOX));
        StructureStart start = new StructureStart(
                structure, new ChunkPos(0, 0), 0,
                new PiecesContainer(List.of(piece)));
        BoundingBox area = new BoundingBox(0, 48, 0, 0, 80, 0);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 64);
        Map<BlockPos, BlockState> before = new HashMap<>(blocks);

        fit(
                world(blocks), area, List.of(target(start, "SOURCE")),
                (x, z) -> 64);

        assertEquals(before, blocks);
    }

    @Test
    public void projectedCenterAndRigidChildrenUseProcessedFoundationCells() throws Exception {
        Structure structure = new DesertPyramidStructure(
                new Structure.StructureSettings(
                        HolderSet.empty(), Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES,
                        TerrainAdjustment.BEARD_BOX));
        PoolElementStructurePiece center = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(1, 1, 1, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 71, 0, 3, 80, 3), 1, Rotation.NONE);
        PoolElementStructurePiece child = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(1, 7, 1, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(10, 65, 0, 13, 76, 3), 7, Rotation.NONE);
        StructureStart start = new StructureStart(
                structure, new ChunkPos(0, 0), 0,
                new PiecesContainer(List.of(center, child)));
        BoundingBox area = new BoundingBox(0, 60, 0, 15, 90, 15);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                put(blocks, x, 64, z, Blocks.DIRT.defaultBlockState());
                put(blocks, x, 65, z, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }

        List<NativeStructureFitBridge.FitTarget> targets = List.of(
                new NativeStructureFitBridge.FitTarget("nova_structures:tavern_oak", start, memo("SOURCE")));
        fit(
                world(blocks), area, targets,
                (x, z) -> 65);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 1, 71, 1));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 11, 71, 1));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 11, 70, 1));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 2, 65, 2));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 12, 65, 2));
    }

    @Test
    public void sparseRigidFootprintsIgnoreAirAndSolidsAboveTheGroundPlane() throws Exception {
        StructureTemplate sparseTemplate = template(List.of(
                block(0, 1, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(15, 1, 0, Blocks.AIR.defaultBlockState()),
                block(30, 5, 0, Blocks.STONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(sparseTemplate),
                new BoundingBox(0, 68, 0, 30, 76, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(
                List.of(piece), TerrainAdjustment.BEARD_THIN);
        BoundingBox area = new BoundingBox(0, 52, 0, 30, 80, 0);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 64);

        fit(
                world(blocks), area, List.of(target(start, "SOURCE")),
                (x, z) -> 64);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 0, 68, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 15, 64, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 30, 64, 0));
    }

    @Test
    public void extremeRigidFoundationGapDoesNotCreateATerrainPillar() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 1, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(template),
                new BoundingBox(0, 90, 0, 0, 96, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(
                List.of(piece), TerrainAdjustment.BEARD_THIN);
        BoundingBox area = new BoundingBox(0, 30, 0, 0, 100, 0);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 40);

        fit(
                world(blocks), area, List.of(target(start, "SOURCE")),
                (x, z) -> 40);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 0, 46, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 47, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 89, 0));
    }

    @Test
    public void terrainMatchingFootprintsFollowProcessedLowestSolidColumns() throws Exception {
        StructureTemplate pathTemplate = template(List.of(
                block(0, 0, 0, Blocks.AIR.defaultBlockState()),
                block(0, 2, 0, Blocks.DIRT.defaultBlockState()),
                block(15, 0, 0, Blocks.AIR.defaultBlockState()),
                block(30, 0, 0, Blocks.DIRT.defaultBlockState()),
                block(45, 0, 0, Blocks.DANDELION.defaultBlockState())));
        InlineSinglePoolElement element = new InlineSinglePoolElement(
                pathTemplate, List.of(), StructureTemplatePool.Projection.TERRAIN_MATCHING);
        PoolElementStructurePiece piece = rigidTemplatePiece(
                element, new BoundingBox(0, 68, 0, 45, 74, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(
                List.of(piece), TerrainAdjustment.BEARD_THIN);
        BoundingBox area = new BoundingBox(0, 52, 0, 45, 76, 0);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 64);
        blocks.remove(new BlockPos(0, 63, 0));
        blocks.remove(new BlockPos(0, 64, 0));
        put(blocks, 0, 59, 0, Blocks.DIRT.defaultBlockState());
        put(blocks, 0, 60, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        put(blocks, 30, 71, 0, Blocks.DIRT.defaultBlockState());
        put(blocks, 30, 72, 0, Blocks.GRASS_BLOCK.defaultBlockState());

        fit(
                world(blocks), area, List.of(target(start, "SOURCE")),
                (x, z) -> x == 0 ? 60 : x == 30 ? 72 : 64);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 0, 61, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 30, 71, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 30, 72, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 15, 64, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 15, 65, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 45, 64, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 45, 65, 0));
    }

    @Test
    public void vacuumFitsOnlyProcessedSolidTemplateColumns() throws Exception {
        StructureTemplate sparseTemplate = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(15, 0, 0, Blocks.STONE.defaultBlockState())));
        InlineSinglePoolElement element = new InlineSinglePoolElement(
                sparseTemplate, List.of(replaceBlockProcessor(
                        Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                element, new BoundingBox(0, 64, 0, 15, 70, 3), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 56, 0, 15, 72, 3);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 60);

        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 64, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 0, 63, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 15, 60, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 15, 61, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 15, 64, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 15, 60, 3));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 15, 64, 3));
    }

    @Test
    public void vacuumSupportsGroundDeltaZeroFoundations() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(template),
                new BoundingBox(0, 66, 0, 0, 70, 0), 0, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 3, 72, 3);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 60);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 0, 65, 0));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 0, 64, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 66, 0));
    }

    @Test
    public void vacuumFindsActualTerrainBelowNominalOpenAirSurface() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(template),
                new BoundingBox(0, 75, 0, 0, 81, 0), 2, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 3, 84, 3);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 80);

        for (int y = 61; y <= 74; y++) {
            assertFalse(state(blocks, 0, y, 0).isAir());
        }
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 0, 74, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 75, 0));
    }

    @Test
    public void vacuumSupportsFoundationBelowHigherNaturalSurface() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineLegacyPoolElement(template),
                new BoundingBox(0, 75, 0, 0, 81, 0), 2, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 15, 84, 3);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);
        put(blocks, 0, 78, 0, Blocks.DIRT.defaultBlockState());
        put(blocks, 0, 79, 0, Blocks.GRASS_BLOCK.defaultBlockState());

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 79);

        for (int y = 61; y <= 74; y++) {
            assertFalse(state(blocks, 0, y, 0).isAir());
        }
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 75, 0));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 0, 78, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 0, 79, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 15, 60, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 15, 61, 0));
    }

    @Test
    public void vacuumRepairUsesOnlyProcessedSolidFoundationColumns() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(1, 0, 0, Blocks.STONE.defaultBlockState()),
                block(2, 0, 0, Blocks.AIR.defaultBlockState()),
                block(2, 2, 0, Blocks.COBBLESTONE.defaultBlockState())));
        InlineSinglePoolElement element = new InlineSinglePoolElement(
                template, List.of(replaceBlockProcessor(
                        Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                element, new BoundingBox(0, 65, 0, 2, 72, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 3, 74, 3);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);

        NativeStructureFitting.Session plan = fit(
                        world(blocks), area,
                        List.of(target(start, "VACUUM")),
                        (x, z) -> 60);
        for (int y = 61; y <= 64; y++) {
            put(blocks, 0, y, 0, Blocks.CAVE_AIR.defaultBlockState());
        }
        put(blocks, 0, 65, 0, Blocks.COBBLESTONE.defaultBlockState());
        put(blocks, 1, 65, 0, Blocks.COBBLESTONE.defaultBlockState());
        put(blocks, 2, 67, 0, Blocks.COBBLESTONE.defaultBlockState());
        BlockState processorAirSupport = state(blocks, 1, 64, 0);
        BlockState authoredAirSupport = state(blocks, 2, 66, 0);

        plan.afterPieces(0);

        for (int y = 61; y <= 64; y++) {
            assertFalse(state(blocks, 0, y, 0).isAir());
        }
        assertEquals(processorAirSupport, state(blocks, 1, 64, 0));
        assertEquals(authoredAirSupport, state(blocks, 2, 66, 0));
    }

    @Test
    public void vacuumRepairPreservesOverlappingAuthoredInterior() throws Exception {
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState()),
                        block(0, 1, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 61, 0, 0, 62, 0), 0, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 65, 0, 0, 70, 0), 0, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(
                List.of(lower, upper), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 3, 72, 3);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);

        NativeStructureFitting.Session plan = fit(
                        world(blocks), area,
                        List.of(target(start, "VACUUM")),
                        (x, z) -> 60);
        put(blocks, 0, 65, 0, Blocks.COBBLESTONE.defaultBlockState());

        plan.afterPieces(0);

        for (int y = 61; y <= 64; y++) {
            assertTrue(state(blocks, 0, y, 0).isAir());
        }
    }

    @Test
    public void vacuumRepairIsStableAcrossSplitChunkAreas() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(1, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineLegacyPoolElement(template),
                new BoundingBox(15, 65, 0, 16, 70, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        NativeStructureFitBridge.FitTarget target =
                target(start, "VACUUM");
        BoundingBox wideArea = new BoundingBox(0, 54, 0, 31, 72, 15);
        BoundingBox westArea = new BoundingBox(0, 54, 0, 15, 72, 15);
        BoundingBox eastArea = new BoundingBox(16, 54, 0, 31, 72, 15);
        Map<BlockPos, BlockState> wideBlocks = flatTerrain(wideArea, 60);
        Map<BlockPos, BlockState> splitBlocks = flatTerrain(wideArea, 60);

        NativeStructureFitting.Session widePlan = fit(
                        world(wideBlocks), wideArea, List.of(target), (x, z) -> 60);
        NativeStructureFitting.Session westPlan = fit(
                        world(splitBlocks), westArea, List.of(target), (x, z) -> 60);
        NativeStructureFitting.Session eastPlan = fit(
                        world(splitBlocks), eastArea, List.of(target), (x, z) -> 60);
        for (int x = 15; x <= 16; x++) {
            for (int y = 61; y <= 64; y++) {
                put(wideBlocks, x, y, 0, Blocks.CAVE_AIR.defaultBlockState());
                put(splitBlocks, x, y, 0, Blocks.CAVE_AIR.defaultBlockState());
            }
            put(wideBlocks, x, 65, 0, Blocks.COBBLESTONE.defaultBlockState());
            put(splitBlocks, x, 65, 0, Blocks.COBBLESTONE.defaultBlockState());
        }

        widePlan.afterPieces(0);
        eastPlan.afterPieces(0);
        westPlan.afterPieces(0);

        assertEquals(wideBlocks, splitBlocks);
    }

    @Test
    public void vacuumNeverLowersExistingTerrainAroundSparsePieces() throws Exception {
        StructureTemplate sparseTemplate = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(sparseTemplate),
                new BoundingBox(0, 56, 0, 7, 62, 7), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 48, 0, 7, 70, 7);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);
        Map<BlockPos, BlockState> originalBlocks = new HashMap<>(blocks);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 60);

        assertEquals(originalBlocks, blocks);
    }

    @Test
    public void vacuumDoesNotRaiseTerrainToRoofsAboveLegacyAir() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.AIR.defaultBlockState()),
                block(0, 2, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineLegacyPoolElement(template),
                new BoundingBox(0, 66, 0, 7, 72, 7), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 15, 74, 15);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);
        Map<BlockPos, BlockState> originalBlocks = new HashMap<>(blocks);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 60);

        assertEquals(originalBlocks, blocks);
    }

    @Test
    public void vacuumCapsNeighborTaperAtAuthoredAirGroundPlanes() throws Exception {
        PoolElementStructurePiece roof = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState()),
                        block(0, 2, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(1, 66, 0, 1, 72, 0), 1, Rotation.NONE);
        PoolElementStructurePiece highChild = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 90, 0, 0, 94, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(
                List.of(roof, highChild), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 15, 96, 15);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 60);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 1, 66, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 1, 67, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 0, 89, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 90, 0));
    }

    @Test
    public void vacuumDoesNotAnchorRoofsAboveProcessorCreatedAir() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.STONE.defaultBlockState()),
                block(0, 2, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(template, List.of(replaceBlockProcessor(
                        Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState()))),
                new BoundingBox(0, 66, 0, 7, 72, 7), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 15, 74, 15);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);
        Map<BlockPos, BlockState> originalBlocks = new HashMap<>(blocks);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 60);

        assertEquals(originalBlocks, blocks);
    }

    @Test
    public void vacuumDoesNotTreatSparseRoofCellsAsFoundations() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.SANDSTONE.defaultBlockState()),
                block(1, 20, 0, Blocks.SANDSTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(template),
                new BoundingBox(0, 66, 0, 20, 100, 20), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 20, 102, 20);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 60);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 0, 65, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 66, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 1, 67, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 1, 86, 0));
    }

    @Test
    public void vacuumDoesNotFlattenTerrainMatchingPoolPieces() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(
                        template, List.of(),
                        StructureTemplatePool.Projection.TERRAIN_MATCHING),
                new BoundingBox(0, 68, 0, 15, 72, 15), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 15, 74, 15);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);
        Map<BlockPos, BlockState> originalBlocks = new HashMap<>(blocks);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 60);

        assertEquals(originalBlocks, blocks);
    }

    @Test
    public void vacuumRetainsJunctionSmoothingForRigidFeaturePieces() {
        StructurePoolElement feature = StructurePoolElement.feature(
                Holder.<PlacedFeature>direct(null))
                .apply(StructureTemplatePool.Projection.RIGID);
        PoolElementStructurePiece piece = rigidTemplatePiece(
                feature, new BoundingBox(4, 66, 4, 4, 70, 4), 1, Rotation.NONE);
        piece.addJunction(new JigsawJunction(
                4, 66, 4, 0, StructureTemplatePool.Projection.RIGID));
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 15, 72, 15);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 60);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 4, 61, 4));
    }

    @Test
    public void vacuumDoesNotInventNonPoolBoundingBoxFootprints() {
        StructureStart start = desertStart(TerrainAdjustment.NONE);
        BoundingBox bounds = start.getBoundingBox();
        BoundingBox area = new BoundingBox(
                bounds.minX() - 12, 40, bounds.minZ() - 12,
                bounds.maxX() + 12, bounds.maxY() + 12, bounds.maxZ() + 12);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 50);
        Map<BlockPos, BlockState> originalBlocks = new HashMap<>(blocks);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 50);

        assertEquals(originalBlocks, blocks);
    }

    @Test
    public void vacuumContinuouslySupportsProcessedChildBases() throws Exception {
        PoolElementStructurePiece center = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 60, 0, 0, 64, 0), 1, Rotation.NONE);
        StructureTemplate childTemplate = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece child = rigidTemplatePiece(
                new InlineSinglePoolElement(childTemplate),
                new BoundingBox(10, 66, 0, 10, 72, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(
                List.of(center, child), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 15, 74, 3);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);

        fit(
                world(blocks), area,
                List.of(target(start, "VACUUM")),
                (x, z) -> 60);

        for (int y = 61; y <= 65; y++) {
            assertFalse(state(blocks, 10, y, 0).isAir());
        }
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 10, 65, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 10, 66, 0));
    }

    @Test
    public void vacuumSurfaceFittingIsStableAcrossSplitChunkAreas() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(1, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(template),
                new BoundingBox(15, 66, 0, 16, 72, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        NativeStructureFitBridge.FitTarget target =
                target(start, "VACUUM");
        BoundingBox wideArea = new BoundingBox(0, 54, 0, 31, 74, 15);
        BoundingBox westArea = new BoundingBox(0, 54, 0, 15, 74, 15);
        BoundingBox eastArea = new BoundingBox(16, 54, 0, 31, 74, 15);
        Map<BlockPos, BlockState> wideBlocks = flatTerrain(wideArea, 60);
        Map<BlockPos, BlockState> splitBlocks = flatTerrain(wideArea, 60);
        Map<BlockPos, BlockState> reverseSplitBlocks = flatTerrain(wideArea, 60);

        fit(
                world(wideBlocks), wideArea, List.of(target), (x, z) -> 60);
        fit(
                world(splitBlocks), westArea, List.of(target), (x, z) -> 60);
        fit(
                world(splitBlocks), eastArea, List.of(target), (x, z) -> 60);
        fit(
                world(reverseSplitBlocks), eastArea, List.of(target), (x, z) -> 60);
        fit(
                world(reverseSplitBlocks), westArea, List.of(target), (x, z) -> 60);

        assertEquals(wideBlocks, splitBlocks);
        assertEquals(wideBlocks, reverseSplitBlocks);
    }

    @Test
    public void stackedRigidPiecesPreserveTheLowerProcessedFoundation() throws Exception {
        Structure structure = new DesertPyramidStructure(
                new Structure.StructureSettings(
                        HolderSet.empty(), Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES,
                        TerrainAdjustment.BEARD_BOX));
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(2, 1, 2, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 4, 65, 4), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(2, 1, 2, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 66, 0, 4, 78, 4), 1, Rotation.NONE);
        StructureStart start = new StructureStart(
                structure, new ChunkPos(0, 0), 0,
                new PiecesContainer(List.of(upper, lower)));
        BoundingBox area = new BoundingBox(0, 58, 0, 4, 78, 4);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                put(blocks, x, 59, z, Blocks.DIRT.defaultBlockState());
                put(blocks, x, 60, z, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }

        WorldGenLevel world = world(blocks);
        List<NativeStructureFitBridge.FitTarget> targets =
                List.of(target(start, "SOURCE"));
        fit(
                world, area, targets, (x, z) -> 60);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 2, 62, 2));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 2, 65, 2));
    }

    @Test
    public void singlePoolTemplateFieldMatchesTheRuntimeContract() {
        Field field = NativeStructureReflection.resolveSinglePoolTemplateField();

        assertEquals(SinglePoolElement.class, field.getDeclaringClass());
        assertEquals(Either.class, field.getType());
        assertTrue(Modifier.isProtected(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertFalse(Modifier.isStatic(field.getModifiers()));
        assertTrue(field.trySetAccessible());
    }

    @Test
    public void legacyAirAndUpperOccupancyAreReportedPerPiece() throws Exception {
        PoolElementStructurePiece lower = rigidTemplatePiece(new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState()), block(1, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 1, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                        block(1, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 64, 0, 1, 70, 0), 1, Rotation.NONE);
        BoundingBox area = new BoundingBox(0, 58, 0, 1, 72, 0);

        assertEquals(List.of("0,62,0", "1,62,0"), legacyAir(lower, area));
        assertEquals(List.of("0,64,0=minecraft:cobblestone", "1,64,0=minecraft:cobblestone"), cells(upper, area));
    }

    @Test
    public void rotatedCellsKeepAirVoidFluidAndJigsawAtTheirRotatedPositions() throws Exception {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        blocks.add(block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()));
        blocks.add(block(1, 0, 0, Blocks.AIR.defaultBlockState()));
        blocks.add(block(2, 0, 0, Blocks.STRUCTURE_VOID.defaultBlockState()));
        blocks.add(block(3, 0, 0, Blocks.WATER.defaultBlockState()));
        blocks.add(block(4, 0, 0, Blocks.JIGSAW.defaultBlockState()));
        PoolElementStructurePiece upper = rigidTemplatePiece(new InlineSinglePoolElement(template(blocks)),
                new BoundingBox(10, 64, 10, 10, 70, 14), 1, Rotation.CLOCKWISE_90);

        assertEquals(List.of("10,64,10=minecraft:cobblestone", "10,64,11=minecraft:air", "10,64,12=minecraft:structure_void",
                "10,64,13=minecraft:water", "10,64,14=minecraft:jigsaw"), cells(upper, new BoundingBox(10, 64, 10, 10, 64, 14)));
    }

    @Test
    public void listElementsOverlayTheirCellsInDeclaredOrder() throws Exception {
        InlineLegacyPoolElement legacy = new InlineLegacyPoolElement(template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(1, 0, 0, Blocks.COBBLESTONE.defaultBlockState()))));
        InlineSinglePoolElement single = new InlineSinglePoolElement(template(List.of(
                block(0, 0, 0, Blocks.AIR.defaultBlockState()),
                block(1, 0, 0, Blocks.STONE.defaultBlockState()))));
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new ListPoolElement(List.of(legacy, single), StructureTemplatePool.Projection.RIGID),
                new BoundingBox(0, 64, 0, 1, 70, 0), 1, Rotation.NONE);

        assertEquals(List.of("0,64,0=minecraft:air", "1,64,0=minecraft:stone"), cells(upper, new BoundingBox(0, 64, 0, 1, 64, 0)));
    }

    @Test
    public void laterLegacyAirDoesNotOverlayAnEarlierListSolid() throws Exception {
        ListPoolElement list = new ListPoolElement(List.of(
                new InlineSinglePoolElement(template(List.of(block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new InlineLegacyPoolElement(template(List.of(block(0, 0, 0, Blocks.AIR.defaultBlockState()))))),
                StructureTemplatePool.Projection.RIGID);
        PoolElementStructurePiece upper = rigidTemplatePiece(list, new BoundingBox(0, 64, 0, 0, 70, 0), 1, Rotation.NONE);

        assertEquals(List.of("0,64,0=minecraft:cobblestone"), cells(upper, new BoundingBox(0, 64, 0, 0, 64, 0)));
    }

    @Test
    public void processorCreatedAirAndFluidAreReportedAsProcessed() throws Exception {
        InlineSinglePoolElement element = new InlineSinglePoolElement(template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(1, 0, 0, Blocks.STONE.defaultBlockState()))), List.of(
                replaceBlockProcessor(Blocks.COBBLESTONE.defaultBlockState(), Blocks.AIR.defaultBlockState()),
                replaceBlockProcessor(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState())));
        PoolElementStructurePiece upper = rigidTemplatePiece(element, new BoundingBox(0, 64, 0, 1, 70, 0), 1, Rotation.NONE);

        assertEquals(List.of("0,64,0=minecraft:air", "1,64,0=minecraft:water"), cells(upper, new BoundingBox(0, 64, 0, 1, 64, 0)));
    }

    @Test
    public void aJigsawIsReportedAsItsFinalState() throws Exception {
        CompoundTag jigsawData = new CompoundTag();
        jigsawData.putString("final_state", "minecraft:cobblestone");
        StructureTemplate template = template(List.of(new StructureTemplate.StructureBlockInfo(
                BlockPos.ZERO, Blocks.JIGSAW.defaultBlockState(), jigsawData)));
        PoolElementStructurePiece upper = rigidTemplatePiece(new InlineSinglePoolElement(template),
                new BoundingBox(0, 64, 0, 0, 70, 0), 1, Rotation.NONE);

        assertEquals(List.of("0,64,0=minecraft:cobblestone"), cells(upper, new BoundingBox(0, 64, 0, 0, 64, 0)));
    }

    @Test
    public void templateBackedColumnsUseTheTemplateAirComplement() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.AIR.defaultBlockState()),
                block(0, 1, 0, Blocks.AIR.defaultBlockState()),
                block(0, 2, 0, Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
                block(1, 0, 0, Blocks.STRUCTURE_VOID.defaultBlockState()),
                block(1, 1, 0, Blocks.DEEPSLATE.defaultBlockState()),
                block(1, 2, 0, Blocks.DEEPSLATE.defaultBlockState())));

        Map<Long, int[]> columns = columns(rigidTemplatePiece(new InlineSinglePoolElement(template),
                new BoundingBox(0, 0, 0, 1, 2, 0), 0, Rotation.NONE));

        assertEquals(2, columns.size());
        assertArrayEquals(new int[]{2, 2}, columns.get(0L));
        assertArrayEquals(new int[]{1, 2}, columns.get(1L << 32));
    }

    @Test
    public void fullyVoidTemplateColumnsAreNotCarveSources() throws Exception {
        StructureTemplate template = template(List.of(
                block(0, 0, 0, Blocks.AIR.defaultBlockState()),
                block(0, 1, 0, Blocks.STRUCTURE_VOID.defaultBlockState())));

        assertTrue(columns(rigidTemplatePiece(new InlineSinglePoolElement(template),
                new BoundingBox(0, 0, 0, 0, 1, 0), 0, Rotation.NONE)).isEmpty());
    }

    @Test
    public void nonTemplatePiecesFallBackToTheirBoundingBoxColumns() {
        StructurePiece piece = desertStart().getPieces().getFirst();
        BoundingBox bounds = piece.getBoundingBox();

        Map<Long, int[]> columns = columns(piece);

        assertEquals(bounds.getXSpan() * bounds.getZSpan(), columns.size());
        assertArrayEquals(new int[]{bounds.minY(), bounds.maxY()},
                columns.get((long) bounds.minX() << 32 | bounds.minZ() & 0xffffffffL));
    }

    @Test
    public void rotatedLegacyTemplateAirIsReportedOnlyInsideTheChunk() throws Exception {
        StructureTemplate.StructureBlockInfo clear = block(1, 0, 0, Blocks.AIR.defaultBlockState());
        StructureTemplate.StructureBlockInfo belowFloor = block(0, -1, 0, Blocks.AIR.defaultBlockState());
        StructureTemplate.StructureBlockInfo outsideChunk = block(3, 0, 0, Blocks.AIR.defaultBlockState());
        BlockPos origin = new BlockPos(10, 80, 10);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90);
        BlockPos clearPosition = origin.offset(StructureTemplate.calculateRelativePosition(settings, clear.pos()));
        BlockPos belowFloorPosition = origin.offset(StructureTemplate.calculateRelativePosition(settings, belowFloor.pos()));
        BlockPos outsidePosition = origin.offset(StructureTemplate.calculateRelativePosition(settings, outsideChunk.pos()));
        BoundingBox area = BoundingBox.fromCorners(clearPosition, belowFloorPosition);
        PoolElementStructurePiece piece = new PoolElementStructurePiece(null,
                new InlineLegacyPoolElement(template(List.of(clear, belowFloor, outsideChunk))), origin, 1,
                Rotation.CLOCKWISE_90, new BoundingBox(origin), LiquidSettings.APPLY_WATERLOGGING);

        Set<BlockPos> air = new HashSet<>();
        for (int[] position : bridged(piece).legacyAir(NativeStructureFitBridge.box(area))) {
            air.add(new BlockPos(position[0], position[1], position[2]));
        }

        assertFalse(area.isInside(outsidePosition));
        assertEquals(Set.of(clearPosition, belowFloorPosition), air);
    }

    @Test
    public void runtimeTemplatesResolveWithoutATemplateManager() {
        StructureTemplate runtimeTemplate = new StructureTemplate();

        assertEquals(runtimeTemplate, NativeStructureReflection.resolveTemplateReference(Either.right(runtimeTemplate), null));
    }

    @Test
    public void terrainReachPersistsHorizontalReferenceCoverage() {
        StructureStart generated = desertStart();
        BoundingBox content = generated.getBoundingBox();
        List<NativeStructureReach> reach = List.of(new NativeStructureReach(false, 24));

        StructureStart wrapped = NativeStructureReferenceEnvelope.wrap(generated, generated.getStructure(), 0, reach);
        BoundingBox referenceBounds = NativeStructureReferenceEnvelope.referenceBounds(wrapped, wrapped.getStructure(), reach);

        assertEquals(content, NativeStructureReferenceEnvelope.contentBounds(wrapped));
        assertEquals(content, wrapped.getBoundingBox());
        assertEquals(content.minX() - 24, referenceBounds.minX());
        assertEquals(content.minZ() - 24, referenceBounds.minZ());
        assertEquals(content.maxX() + 24, referenceBounds.maxX());
        assertEquals(content.maxZ() + 24, referenceBounds.maxZ());
        assertEquals(generated.getPieces().size(), wrapped.getPieces().size());
    }

    @Test
    public void theWidestReachOfAllModulesWins() {
        StructureStart generated = desertStart(TerrainAdjustment.NONE);
        BoundingBox content = NativeStructureReferenceEnvelope.contentBounds(generated);

        BoundingBox references = NativeStructureReferenceEnvelope.referenceBounds(generated, generated.getStructure(),
                List.of(new NativeStructureReach(false, 12), new NativeStructureReach(false, 4)));

        assertEquals(content.minX() - 12, references.minX());
        assertEquals(content.maxX() + 12, references.maxX());
        assertEquals(content.minZ() - 12, references.minZ());
        assertEquals(content.maxZ() + 12, references.maxZ());
    }

    @Test
    public void terrainReachClipsOptionalCoverageWithoutDroppingContent() {
        StructureStart generated = desertStart();
        ChunkPos origin = generated.getChunkPos();
        BoundingBox content = NativeStructureReferenceEnvelope.contentBounds(generated);
        List<NativeStructureReach> reach = List.of(new NativeStructureReach(false, 124));

        BoundingBox references = NativeStructureReferenceEnvelope.referenceBounds(
                generated, generated.getStructure(), reach, "test:clipped_terrain");
        StructureStart published = NativeStructureReferenceEnvelope.wrapForPublication(
                generated, generated.getStructure(), 0, reach, "test:clipped_terrain");

        assertEquals(Math.max(content.minX() - 124, (origin.x() - 8) << 4), references.minX());
        assertEquals(Math.min(content.maxX() + 124, ((origin.x() + 8) << 4) + 15), references.maxX());
        assertEquals(Math.max(content.minZ() - 124, (origin.z() - 8) << 4), references.minZ());
        assertEquals(Math.min(content.maxZ() + 124, ((origin.z() + 8) << 4) + 15), references.maxZ());
        assertTrue(references.isInside(content.getCenter()));
        assertTrue(published.isValid());
    }

    @Test
    public void aShiftPastTheWorldFloorClampsInsteadOfAborting() {
        StructureStart start = desertStart();
        int minY = start.getBoundingBox().minY();
        int worldMinY = minY - 4;

        int offset = NativeStructureVerticalPlacer.applyVerticalShift(start, "minecraft:desert_pyramid", -40, worldMinY, 320);

        assertEquals(-4, offset);
        assertEquals(worldMinY, start.getBoundingBox().minY());
    }

    @Test
    public void naturalBlockFollowsTheVanillaDimensionTerrain() {
        assertEquals(Blocks.NETHERRACK.defaultBlockState(), NativeStructureFitBridge.naturalBlock(
                net.minecraft.world.level.Level.NETHER, null, null, 64));
        assertEquals(Blocks.END_STONE.defaultBlockState(), NativeStructureFitBridge.naturalBlock(
                net.minecraft.world.level.Level.END, null, null, 64));
        assertEquals(Blocks.STONE.defaultBlockState(), NativeStructureFitBridge.naturalBlock(
                net.minecraft.world.level.Level.OVERWORLD, null, null, 64));
        assertEquals(Blocks.DEEPSLATE.defaultBlockState(), NativeStructureFitBridge.naturalBlock(
                net.minecraft.world.level.Level.OVERWORLD, null, null, -1));
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> custom = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.parse("iris:custom_nether"));
        assertEquals(Blocks.NETHERRACK.defaultBlockState(), NativeStructureFitBridge.naturalBlock(
                custom, net.minecraft.world.level.dimension.BuiltinDimensionTypes.NETHER, null, 64));
        assertEquals(Blocks.END_STONE.defaultBlockState(), NativeStructureFitBridge.naturalBlock(
                custom, net.minecraft.world.level.dimension.BuiltinDimensionTypes.END, null, 64));
    }

    @Test
    public void piecesReportTheirProjectionAsTheirKind() {
        PoolElementStructurePiece rigid = rigidTemplatePiece(new InlineSinglePoolElement(new StructureTemplate()),
                new BoundingBox(0, 0, 0, 0, 0, 0), 0, Rotation.NONE);
        PoolElementStructurePiece matching = rigidTemplatePiece(new InlineSinglePoolElement(new StructureTemplate(),
                List.of(), StructureTemplatePool.Projection.TERRAIN_MATCHING), new BoundingBox(0, 0, 0, 0, 0, 0), 0, Rotation.NONE);

        assertEquals("RIGID", bridged(rigid).kind());
        assertEquals("TERRAIN_MATCHING", bridged(matching).kind());
        assertEquals("FIXED", bridged(desertStart().getPieces().getFirst()).kind());
    }

    /** Runs the built-in fit through the bridge before the chunk's pieces are placed. */
    private static NativeStructureFitting.Session fit(WorldGenLevel world, BoundingBox area,
                                                      List<NativeStructureFitBridge.FitTarget> targets,
                                                      IntBinaryOperator surfaceHeight) {
        return BuiltInFitting.FITTING.beforePieces(NativeStructureFitBridge.chunk(world, area, targets, surfaceHeight));
    }

    /** The positions the fit changed in {@code blocks}, on the support terrain's surface. */
    private static Set<Long> supportWritten(Map<BlockPos, BlockState> blocks, BoundingBox area,
                                            List<NativeStructureFitBridge.FitTarget> targets) {
        Map<BlockPos, BlockState> before = new HashMap<>(blocks);
        fit(world(blocks), area, targets, (x, z) -> SUPPORT_SURFACE_Y);
        Set<Long> written = new HashSet<>();
        Set<BlockPos> positions = new HashSet<>(before.keySet());
        positions.addAll(blocks.keySet());
        for (BlockPos position : positions) {
            if (!state(before, position.getX(), position.getY(), position.getZ())
                    .equals(state(blocks, position.getX(), position.getY(), position.getZ()))) {
                written.add(position.asLong());
            }
        }
        return written;
    }

    private static NativeStructureFitBridge.FitTarget target(StructureStart start, String terrainMode) {
        return new NativeStructureFitBridge.FitTarget("nova_structures:tavern_oak", start, memo(terrainMode));
    }

    private static NativeStartMemo memo(String terrainMode) {
        return new NativeStartMemo("{\"mode\":\"" + terrainMode + "\"}", "null");
    }

    /** {@code piece} as the bridge hands it to the fit. */
    private static NativeFitPiece<BlockState> bridged(StructurePiece piece) {
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        return NativeStructureFitBridge.chunk(world(new HashMap<>()), piece.getBoundingBox(),
                List.of(target(start, "SOURCE")), (x, z) -> 0).starts().getFirst().pieces().getFirst();
    }

    private static Map<Long, int[]> columns(StructurePiece piece) {
        Map<Long, int[]> columns = new HashMap<>();
        bridged(piece).columns((x, z, minY, maxY) -> columns.put((long) x << 32 | z & 0xffffffffL, new int[]{minY, maxY}));
        return columns;
    }

    /** The processed cells the bridge reports for {@code piece} inside {@code region}, sorted, "removed" for a block a processor removed. */
    private static List<String> cells(PoolElementStructurePiece piece, BoundingBox region) {
        NativeTemplateCells<BlockState> cells = bridged(piece).cells(NativeStructureFitBridge.box(region));
        List<String> described = new ArrayList<>();
        for (int index = 0; index < cells.states().length; index++) {
            BlockState state = cells.states()[index];
            described.add(cells.xs()[index] + "," + cells.ys()[index] + "," + cells.zs()[index] + "="
                    + (state == null ? "removed" : BuiltInRegistries.BLOCK.getKey(state.getBlock())));
        }
        described.sort(null);
        return described;
    }

    private static List<String> legacyAir(PoolElementStructurePiece piece, BoundingBox clip) {
        List<String> air = new ArrayList<>();
        for (int[] position : bridged(piece).legacyAir(NativeStructureFitBridge.box(clip))) {
            air.add(position[0] + "," + position[1] + "," + position[2]);
        }
        air.sort(null);
        return air;
    }

    private static final class BuiltInFitting {
        private static final NativeStructureFitting FITTING = NativeStructureFitting.builtInModules();
    }

    private static PoolElementStructurePiece rigidTemplatePiece(
            StructurePoolElement element, BoundingBox bounds,
            int groundLevelDelta, Rotation rotation) {
        return new PoolElementStructurePiece(
                null, element,
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                groundLevelDelta, rotation, bounds,
                LiquidSettings.APPLY_WATERLOGGING);
    }

    private static StructureStart rigidSurfaceStart(List<? extends StructurePiece> pieces) {
        return rigidSurfaceStart(pieces, TerrainAdjustment.BEARD_BOX);
    }

    private static StructureStart rigidSurfaceStart(
            List<? extends StructurePiece> pieces, TerrainAdjustment adjustment) {
        Structure structure = new DesertPyramidStructure(
                new Structure.StructureSettings(
                        HolderSet.empty(), Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES,
                        adjustment));
        List<StructurePiece> structurePieces = new ArrayList<>(pieces);
        return new StructureStart(
                structure, new ChunkPos(0, 0), 0,
                new PiecesContainer(structurePieces));
    }

    private static StructureTemplate.StructureBlockInfo block(
            int x, int y, int z, BlockState state) {
        return new StructureTemplate.StructureBlockInfo(
                new BlockPos(x, y, z), state, null);
    }

    private static Map<BlockPos, BlockState> supportTerrain(int minimumX, int maximumX) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = minimumX; x <= maximumX; x++) {
            put(blocks, x, 61, 0, Blocks.DIRT.defaultBlockState());
            put(blocks, x, 62, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        }
        return blocks;
    }

    private static Map<BlockPos, BlockState> flatTerrain(BoundingBox area, int surfaceY) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                put(blocks, x, surfaceY - 1, z, Blocks.DIRT.defaultBlockState());
                put(blocks, x, surfaceY, z, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        return blocks;
    }

    private static StructureTemplate template(
            List<StructureTemplate.StructureBlockInfo> blocks) throws Exception {
        Constructor<StructureTemplate.Palette> constructor =
                StructureTemplate.Palette.class.getDeclaredConstructor(List.class);
        assertTrue(constructor.trySetAccessible());
        StructureTemplate.Palette palette = constructor.newInstance(blocks);
        StructureTemplate template = new StructureTemplate();
        template.palettes.add(palette);
        return template;
    }

    private static final class InlineSinglePoolElement extends SinglePoolElement {
        private InlineSinglePoolElement(StructureTemplate template) {
            this(template, List.of());
        }

        private InlineSinglePoolElement(
                StructureTemplate template, List<StructureProcessor> processors) {
            this(template, processors, StructureTemplatePool.Projection.RIGID);
        }

        private InlineSinglePoolElement(
                StructureTemplate template, List<StructureProcessor> processors,
                StructureTemplatePool.Projection projection) {
            super(Either.right(template),
                    Holder.direct(new StructureProcessorList(processors)),
                    projection,
                    Optional.<LiquidSettings>empty());
        }
    }

    private static final class InlineLegacyPoolElement extends LegacySinglePoolElement {
        private InlineLegacyPoolElement(StructureTemplate template) {
            super(Either.right(template),
                    Holder.direct(new StructureProcessorList(List.of())),
                    StructureTemplatePool.Projection.RIGID,
                    Optional.<LiquidSettings>empty());
        }
    }

    private static StructureProcessor replaceBlockProcessor(BlockState source, BlockState replacement) {
        // Vanilla RuleProcessor instead of a custom StructureProcessor subtype: StructureProcessor
        // is an abstract class on 26.1.2 and an interface on 26.2, so a direct subtype cannot
        // compile against both dev bundles. BlockStateMatchTest matches the exact source state,
        // matching the previous custom processor's identity-equality semantics.
        return new RuleProcessor(List.of(new ProcessorRule(
                new BlockStateMatchTest(source), AlwaysTrueTest.INSTANCE, replacement)));
    }

    private static StructureStart desertStart() {
        return desertStart(TerrainAdjustment.NONE);
    }

    private static StructureStart desertStart(TerrainAdjustment adjustment) {
        Structure structure = new DesertPyramidStructure(
                new Structure.StructureSettings(
                        HolderSet.empty(), Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES, adjustment));
        DesertPyramidPiece piece = new DesertPyramidPiece(RandomSource.create(7L), 0, 0);
        return new StructureStart(
                structure, new ChunkPos(0, 0), 0, new PiecesContainer(List.of(piece)));
    }

    private static WorldGenLevel world(Map<BlockPos, BlockState> blocks) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            String methodName = method.getName();
            if (methodName.equals("getBlockState")) {
                BlockPos position = (BlockPos) arguments[0];
                return state(blocks, position.getX(), position.getY(), position.getZ());
            }
            if (methodName.equals("setBlock")) {
                BlockPos position = (BlockPos) arguments[0];
                BlockState blockState = (BlockState) arguments[1];
                put(blocks, position.getX(), position.getY(), position.getZ(), blockState);
                return true;
            }
            if (methodName.equals("getSeed")) {
                return TEST_SEED;
            }
            if (methodName.equals("getHeight") && arguments == null) {
                return WORLD_HEIGHT;
            }
            if (methodName.equals("getHeight")) {
                int x = (int) arguments[1];
                int z = (int) arguments[2];
                int highest = Integer.MIN_VALUE;
                for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                    BlockPos position = entry.getKey();
                    if (position.getX() == x && position.getZ() == z
                            && !entry.getValue().isAir()) {
                        highest = Math.max(highest, position.getY());
                    }
                }
                return highest == Integer.MIN_VALUE ? 0 : highest + 1;
            }
            if (methodName.equals("getMinY")) {
                return WORLD_MIN_Y;
            }
            if (methodName.equals("getLevel")) {
                return null;
            }
            if (methodName.equals("holderLookup")) {
                return BuiltInRegistries.BLOCK;
            }
            if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (methodName.equals("equals")) {
                return proxy == arguments[0];
            }
            if (methodName.equals("toString")) {
                return "fit-bridge-test-world";
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
    }

    private static void put(Map<BlockPos, BlockState> blocks,
                            int x, int y, int z, BlockState state) {
        blocks.put(new BlockPos(x, y, z), state);
    }

    private static BlockState state(Map<BlockPos, BlockState> blocks, int x, int y, int z) {
        return blocks.getOrDefault(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
    }
}
