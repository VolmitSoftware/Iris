package art.arcane.iris.nativegen;

import art.arcane.iris.engine.mantle.components.StructureCarvingFootprint;
import art.arcane.iris.engine.object.IrisStructureCarveShape;
import art.arcane.iris.engine.object.IrisStructureTerrain;
import art.arcane.iris.engine.object.IrisStructureTerrainMode;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockStateMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckStructure;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeStructurePostProcessorSurfaceTerrainTest {
    private static final int SLAB_DEPTH = 512;
    private static final int SLAB_MIN_Y = 64;
    private static final int SLAB_PADDING = 14;
    private static final int SLAB_WIDTH = 16;
    private static final long TEST_SEED = 8675309L;

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void flattenCutsAndFillsSymmetricallyAcrossItsConfiguredBlend() {
        NativeStructureSurfaceFitter.FlattenAnchor anchor = new NativeStructureSurfaceFitter.FlattenAnchor(
                new NativeStructureSurfaceFitter.SurfaceAnchor(0, 0, 0, 0, 80, 2), 24, 48);
        List<NativeStructureSurfaceFitter.FlattenAnchor> anchors = List.of(anchor);
        assertEquals(80, NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, 0, 0, 128).targetY());
        assertEquals(80, NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, 0, 0, 32).targetY());
        assertEquals(152, NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, 0, 0, 200).targetY());
        assertEquals(48, NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, 0, 0, 0).targetY());
        int previousCut = 80;
        for (int x = 1; x <= 24; x++) {
            int cut = NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, x, 0, 128).targetY();
            int fill = NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, x, 0, 32).targetY();
            assertEquals(160, cut + fill);
            assertTrue(cut >= previousCut);
            assertTrue(cut - previousCut <= 3);
            previousCut = cut;
        }
        assertEquals(128, previousCut);
        assertEquals(0, NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, 24, 0, 128).range());
    }

    @Test
    public void flattenBlendsNeighboringFoundationsWithoutOrderDependentRidges() {
        NativeStructureSurfaceFitter.FlattenAnchor left = new NativeStructureSurfaceFitter.FlattenAnchor(
                new NativeStructureSurfaceFitter.SurfaceAnchor(0, 0, 0, 0, 80, 2), 24, 64);
        NativeStructureSurfaceFitter.FlattenAnchor right = new NativeStructureSurfaceFitter.FlattenAnchor(
                new NativeStructureSurfaceFitter.SurfaceAnchor(8, 8, 0, 0, 80, 2), 24, 64);
        int previous = 80;
        for (int x = 0; x <= 8; x++) {
            NativeStructureSurfaceFitter.FlattenResolution forward = NativeStructureSurfaceFitter.resolveFlattenSurface(
                    List.of(left, right), x, 0, 104);
            assertEquals(forward, NativeStructureSurfaceFitter.resolveFlattenSurface(
                    List.of(right, left), x, 0, 104));
            assertTrue(forward.targetY() <= 82);
            assertTrue(Math.abs(forward.targetY() - previous) <= 1);
            previous = forward.targetY();
        }
    }

    @Test
    public void flattenBlendsAnElevatedRoadJunctionWithNearbyFoundations() {
        List<NativeStructureSurfaceFitter.FlattenAnchor> anchors = new ArrayList<>();
        for (int x : List.of(-3, 3)) {
            for (int z = -1; z <= 1; z++) {
                anchors.add(new NativeStructureSurfaceFitter.FlattenAnchor(
                        new NativeStructureSurfaceFitter.SurfaceAnchor(x, x, z, z, 64, 2), 24, 64));
            }
        }
        anchors.add(new NativeStructureSurfaceFitter.FlattenAnchor(
                new NativeStructureSurfaceFitter.SurfaceAnchor(0, 0, 0, 0, 105, 1), 24, 64));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                NativeStructureSurfaceFitter.FlattenResolution grade =
                        NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, x, z, 96);
                assertTrue(grade.targetY() >= 64 && grade.targetY() <= 72);
                assertEquals(grade, NativeStructureSurfaceFitter.resolveFlattenSurface(
                        anchors.reversed(), x, z, 96));
                if (x < 1) {
                    assertTrue(Math.abs(grade.targetY() - NativeStructureSurfaceFitter.resolveFlattenSurface(
                            anchors, x + 1, z, 96).targetY()) <= 1);
                }
                if (z < 1) {
                    assertTrue(Math.abs(grade.targetY() - NativeStructureSurfaceFitter.resolveFlattenSurface(
                            anchors, x, z + 1, 96).targetY()) <= 1);
                }
            }
        }
    }

    @Test
    public void flattenPreservesAnExactRigidFloorBesideAnElevatedJunction() {
        NativeStructureSurfaceFitter.FlattenAnchor rigid = new NativeStructureSurfaceFitter.FlattenAnchor(
                new NativeStructureSurfaceFitter.SurfaceAnchor(0, 0, 0, 0, 64, 2), 24, 64);
        NativeStructureSurfaceFitter.FlattenAnchor junction = new NativeStructureSurfaceFitter.FlattenAnchor(
                new NativeStructureSurfaceFitter.SurfaceAnchor(0, 0, 0, 0, 105, 1), 24, 64);
        assertEquals(64, NativeStructureSurfaceFitter.resolveFlattenSurface(
                List.of(rigid, junction), 0, 0, 96).targetY());
        assertEquals(64, NativeStructureSurfaceFitter.resolveFlattenSurface(
                List.of(junction, rigid), 0, 0, 96).targetY());
    }

    @Test
    public void flattenRetainsIsolatedJunctionSupportWithinItsConfiguredBounds() {
        for (int radius : List.of(0, 24)) {
            NativeStructureSurfaceFitter.FlattenAnchor junction = new NativeStructureSurfaceFitter.FlattenAnchor(
                    new NativeStructureSurfaceFitter.SurfaceAnchor(0, 0, 0, 0, 105, 1), radius, 8);
            List<NativeStructureSurfaceFitter.FlattenAnchor> anchors = List.of(junction);
            assertEquals(104, NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, 0, 0, 96).targetY());
            assertEquals(8, NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, 0, 0, 96).range());
            int outside = Math.max(1, radius);
            assertEquals(96, NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, outside, 0, 96).targetY());
            assertEquals(0, NativeStructureSurfaceFitter.resolveFlattenSurface(anchors, outside, 0, 96).range());
        }
    }

    @Test
    public void flattenLevelsNonJigsawFoundationsAndRemovesDetachedRoofs() {
        StructureStart start = desertStart();
        int groundY = start.getBoundingBox().minY() - 1;
        BoundingBox area = new BoundingBox(0, 0, 0, 3, 127, 0);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x <= 3; x++) {
            int sourceY = x < 2 ? groundY - 32 : groundY + 32;
            put(blocks, x, 12, 0, Blocks.STONE.defaultBlockState());
            put(blocks, x, sourceY - 1, 0, Blocks.DIRT.defaultBlockState());
            put(blocks, x, sourceY, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        }
        assertTrue(NativeStructureSurfaceFitter.requiresFlattenTerrain(
                surfaceTarget(start, IrisStructureTerrainMode.FLATTEN)));

        NativeStructureSurfaceFitter.prepareSurfaceStructures(world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.FLATTEN)),
                (x, z) -> x < 2 ? groundY - 32 : groundY + 32);

        for (int x = 0; x <= 3; x++) {
            assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, x, groundY, 0));
            for (int y = groundY + 1; y <= groundY + 32; y++) {
                assertTrue("Roof at " + x + "," + y, state(blocks, x, y, 0).isAir());
            }
            for (int y = 12; y < groundY; y++) {
                assertTrue("Missing foundation at " + x + "," + y, state(blocks, x, y, 0).isSolid());
            }
        }
    }

    @Test
    public void flattenRestoresAnUnchangedFoundationSurfaceOverADeepAirGap() {
        StructureStart start = desertStart();
        int groundY = start.getBoundingBox().minY() - 1;
        BoundingBox area = new BoundingBox(0, 0, 0, 0, 127, 0);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        put(blocks, 0, groundY - 19, 0, Blocks.DIRT.defaultBlockState());

        NativeStructureSurfaceFitter.prepareSurfaceStructures(world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.FLATTEN)), (x, z) -> groundY);

        for (int y = groundY - 19; y <= groundY; y++) {
            assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 0, y, 0));
        }
        assertTrue(state(blocks, 0, groundY + 1, 0).isAir());
    }

    @Test
    public void flattenLeavesBuriedAndSubmergedInstancesUntouched() {
        StructureStart start = desertStart();
        int groundY = start.getBoundingBox().minY() - 1;
        BoundingBox area = new BoundingBox(0, 0, 0, 0, 127, 0);
        Map<BlockPos, BlockState> buried = flatTerrain(area, 100);
        Map<BlockPos, BlockState> originalBuried = new HashMap<>(buried);
        Map<BlockPos, BlockState> submerged = flatTerrain(area, groundY - 8);
        put(submerged, 0, groundY - 7, 0, Blocks.WATER.defaultBlockState());
        Map<BlockPos, BlockState> originalSubmerged = new HashMap<>(submerged);

        NativeStructureSurfaceFitter.prepareSurfaceStructures(world(buried), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.FLATTEN)), (x, z) -> 100);
        NativeStructureSurfaceFitter.prepareSurfaceStructures(world(submerged), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.FLATTEN)), (x, z) -> groundY - 8);

        assertEquals(originalBuried, buried);
        assertEquals(originalSubmerged, submerged);
    }

    @Test
    public void flattenExemptsUndergroundAndSubmergedStructureTypes() {
        Structure.StructureSettings surface = new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE);
        Structure.StructureSettings underground = new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.UNDERGROUND_STRUCTURES, TerrainAdjustment.NONE);
        List<Structure> exempt = List.of(new StrongholdStructure(surface), new OceanMonumentStructure(surface),
                new ShipwreckStructure(surface, false), new DesertPyramidStructure(underground));
        for (Structure structure : exempt) {
            StructureStart start = new StructureStart(structure, new ChunkPos(0, 0), 0,
                    new PiecesContainer(desertStart().getPieces()));
            assertFalse(NativeStructureSurfaceFitter.requiresFlattenTerrain(
                    surfaceTarget(start, IrisStructureTerrainMode.FLATTEN)));
        }
        assertFalse(NativeStructureSurfaceFitter.requiresFlattenTerrain(
                surfaceTarget(desertStart(TerrainAdjustment.BURY), IrisStructureTerrainMode.FLATTEN)));
        assertFalse(NativeStructureSurfaceFitter.requiresFlattenTerrain(
                surfaceTarget(desertStart(TerrainAdjustment.ENCAPSULATE), IrisStructureTerrainMode.FLATTEN)));
    }

    @Test
    public void flattenFoundationReadsAndWritesStayInsideConfiguredVerticalRange() throws Exception {
        PoolElementStructurePiece piece = rigidTemplatePiece(new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 128, 0, 0, 136, 0), 0, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece));
        NativeStructureTerrainIntegrator.TerrainTarget target = surfaceTarget(start, IrisStructureTerrainMode.FLATTEN);
        target.terrain().setFlattenRange(32);
        BoundingBox area = new BoundingBox(0, 0, 0, 0, 255, 0);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        put(blocks, 0, 94, 0, Blocks.GOLD_BLOCK.defaultBlockState());
        WorldGenLevel delegate = world(blocks);
        WorldGenLevel bounded = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getBlockState") || method.getName().equals("setBlock")) {
                        BlockPos position = (BlockPos) arguments[0];
                        assertTrue(position.toString(), position.getY() >= 95 && position.getY() <= 159);
                    }
                    return method.invoke(delegate, arguments);
                });

        NativeStructureSurfaceFitter.prepareSurfaceStructures(bounded, area, List.of(target), (x, z) -> 127);

        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), state(blocks, 0, 94, 0));
        assertEquals(Blocks.STONE.defaultBlockState(), state(blocks, 0, 95, 0));
        assertEquals(Blocks.STONE.defaultBlockState(), state(blocks, 0, 127, 0));
        assertTrue(state(blocks, 0, 128, 0).isAir());
    }

    @Test
    public void flattenReferencesIncludeTheMaximumConfiguredFeather() throws Exception {
        PoolElementStructurePiece piece = rigidTemplatePiece(new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 64, 0, 0, 70, 0), 0, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece));
        IrisStructureTerrain terrain = new IrisStructureTerrain().setMode(IrisStructureTerrainMode.FLATTEN)
                .setHorizontalPadding(128).setFlattenRange(128);

        BoundingBox references = NativeStructureReferenceEnvelope.referenceBounds(start, start.getStructure(), terrain);

        assertEquals(-128, references.minX());
        assertEquals(128, references.maxX());
        assertEquals(-128, references.minZ());
        assertEquals(128, references.maxZ());
        NativeStructureSurfaceFitter.FlattenAnchor anchor = new NativeStructureSurfaceFitter.FlattenAnchor(
                new NativeStructureSurfaceFitter.SurfaceAnchor(0, 0, 0, 0, 64, 2), 128, 128);
        assertTrue(NativeStructureSurfaceFitter.resolveFlattenSurface(List.of(anchor), 127, 0, 100).range() > 0);
        assertEquals(0, NativeStructureSurfaceFitter.resolveFlattenSurface(List.of(anchor), 128, 0, 100).range());
    }

    @Test
    public void flattenPreservesNativeVillageSupportBridges() throws Exception {
        PoolElementStructurePiece lower = rigidTemplatePiece(new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 0, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 64, 0, 0, 70, 0), 0, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(lower, upper));
        BoundingBox area = new BoundingBox(0, 58, 0, 0, 72, 0);
        Map<BlockPos, BlockState> blocks = supportTerrain(0, 0);

        Set<Long> written = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.FLATTEN)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertEquals(Set.of(BlockPos.asLong(0, 63, 0)), written);
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 0, 63, 0));
    }

    @Test
    public void flattenDoesNotBridgeACompletelyBuriedVillage() throws Exception {
        PoolElementStructurePiece lower = rigidTemplatePiece(new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 0, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 64, 0, 0, 70, 0), 0, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(lower, upper));
        BoundingBox area = new BoundingBox(0, 58, 0, 0, 90, 0);
        Map<BlockPos, BlockState> blocks = supportTerrain(0, 0);
        put(blocks, 0, 80, 0, Blocks.STONE.defaultBlockState());

        Set<Long> written = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.FLATTEN)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertTrue(written.isEmpty());
        assertTrue(state(blocks, 0, 63, 0).isAir());
    }

    @Test
    public void flattenKeepsPyramidPlacementAboveItsPreparedGround() {
        FlattenDesertPiece piece = new FlattenDesertPiece();
        StructureStart start = new StructureStart(desertStart().getStructure(), new ChunkPos(0, 0), 0,
                new PiecesContainer(List.of(piece)));
        BoundingBox footprint = piece.getBoundingBox();
        NativeStructureVerticalPlacer.applyVerticalPlacement(start, "minecraft:desert_pyramid", 0,
                63, 0, 256, false, false, null, (x, z) -> x == footprint.minX() ? 80 : 112);
        BoundingBox area = new BoundingBox(footprint.minX(), 0, footprint.minZ(),
                footprint.maxX(), 160, footprint.maxZ());
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
            for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                int surfaceY = x == footprint.minX() ? 80 : 112;
                put(blocks, x, surfaceY - 1, z, Blocks.DIRT.defaultBlockState());
                put(blocks, x, surfaceY, z, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }

        NativeStructureSurfaceFitter.prepareSurfaceStructures(world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.FLATTEN)),
                (x, z) -> x == footprint.minX() ? 80 : 112);

        assertEquals(81, piece.getBoundingBox().minY());
        assertTrue(piece.attemptVanillaAlignment());
        assertEquals(81, piece.getBoundingBox().minY());
        assertTrue(state(blocks, footprint.maxX(), 80, footprint.maxZ()).isSolid());
        assertTrue(state(blocks, footprint.maxX(), 81, footprint.maxZ()).isAir());
    }

    @Test
    public void flattenLetsTerrainMatchingPathsFollowThePreparedVillageGrade() throws Exception {
        StructureTemplate foundation = template(List.of(block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece left = rigidTemplatePiece(new InlineSinglePoolElement(foundation),
                new BoundingBox(0, 65, 0, 0, 75, 0), 0, Rotation.NONE);
        PoolElementStructurePiece right = rigidTemplatePiece(new InlineSinglePoolElement(foundation),
                new BoundingBox(8, 65, 0, 8, 75, 0), 0, Rotation.NONE);
        PoolElementStructurePiece path = rigidTemplatePiece(new InlineSinglePoolElement(
                        template(List.of(block(0, 0, 0, Blocks.DIRT_PATH.defaultBlockState()))),
                        List.of(), StructureTemplatePool.Projection.TERRAIN_MATCHING),
                new BoundingBox(4, 65, 0, 4, 75, 0), 0, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(left, path, right));
        NativeStructureTerrainIntegrator.TerrainTarget target = surfaceTarget(start, IrisStructureTerrainMode.FLATTEN);
        target.terrain().setHorizontalPadding(24);
        BoundingBox area = new BoundingBox(0, 0, 0, 8, 128, 0);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 96);
        for (int x : List.of(0, 8)) {
            blocks.remove(new BlockPos(x, 95, 0));
            blocks.remove(new BlockPos(x, 96, 0));
            put(blocks, x, 63, 0, Blocks.DIRT.defaultBlockState());
            put(blocks, x, 64, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        }
        WorldGenLevel world = world(blocks);

        NativeStructureSurfaceFitter.prepareSurfaceStructures(world, area, List.of(target),
                (x, z) -> x == 0 || x == 8 ? 64 : 96);
        NativeStructureTemplateOccupancy.OccupancyResult occupancy = NativeStructureTemplateOccupancy.resolve(
                world, path, BlockPos.ZERO, area,
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager,
                area::isInside, cells -> {});
        Map<Long, NativeStructureTemplateOccupancy.LowestCell> pathGround =
                NativeStructureTemplateOccupancy.lowestProcessedSolidCells(occupancy.cells());

        assertTrue(state(blocks, 4, 96, 0).isAir());
        assertEquals(1, pathGround.size());
        int pathY = pathGround.values().iterator().next().y();
        assertTrue("Path Y " + pathY, pathY >= 64 && pathY <= 67);
    }

    @Test
    public void flattenPublishesCutAndFillHeightsBeforeNativePathProjectionAtFeatures() throws Exception {
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY,
                LevelHeightAccessor.create(0, 256), containerFactory(), null);
        chunk.setPersistedStatus(ChunkStatus.CARVERS);
        for (int x = 0; x <= 8; x++) {
            int originalY = x < 4 ? 96 : 32;
            write(chunk, x, originalY - 1, 0, Blocks.DIRT.defaultBlockState());
            write(chunk, x, originalY, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        }
        WorldgenTerrainHeightmaps.primeTerrain(chunk,
                (x, z) -> x == 15 ? 81 : x < 4 ? 97 : 33,
                (x, z) -> x == 15 ? 61 : x < 4 ? 97 : 33);
        StructureTemplate foundation = template(List.of(block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece left = rigidTemplatePiece(new InlineSinglePoolElement(foundation),
                new BoundingBox(0, 65, 0, 0, 75, 0), 0, Rotation.NONE);
        PoolElementStructurePiece right = rigidTemplatePiece(new InlineSinglePoolElement(foundation),
                new BoundingBox(8, 65, 0, 8, 75, 0), 0, Rotation.NONE);
        PoolElementStructurePiece path = rigidTemplatePiece(new InlineSinglePoolElement(
                        template(List.of(block(0, 0, 0, Blocks.DIRT_PATH.defaultBlockState()),
                                block(8, 0, 0, Blocks.DIRT_PATH.defaultBlockState()))),
                        List.of(), StructureTemplatePool.Projection.TERRAIN_MATCHING),
                new BoundingBox(0, 65, 0, 8, 75, 0), 0, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(left, path, right));
        NativeStructureTerrainIntegrator.TerrainTarget target = surfaceTarget(start, IrisStructureTerrainMode.FLATTEN);
        target.terrain().setHorizontalPadding(24);
        BoundingBox area = new BoundingBox(0, 0, 0, 8, 127, 0);
        WorldGenLevel world = world(chunk);

        NativeStructureSurfaceFitter.SurfaceTerrainPlan plan = NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world, area, List.of(target), (x, z) -> x < 4 ? 96 : 32);

        assertEquals(96, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, 0));
        assertEquals(32, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 0));
        plan.primeHeightmaps(chunk);
        chunk.setBlockState(new BlockPos(0, 85, 0), Blocks.OAK_PLANKS.defaultBlockState(), 2);
        NativeStructureTemplateOccupancy.OccupancyResult occupancy = NativeStructureTemplateOccupancy.resolve(
                world, path, BlockPos.ZERO, area,
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager,
                area::isInside, cells -> {});
        Map<Long, NativeStructureTemplateOccupancy.LowestCell> pathGround =
                NativeStructureTemplateOccupancy.lowestProcessedSolidCells(occupancy.cells());

        assertEquals(2, pathGround.size());
        for (NativeStructureTemplateOccupancy.LowestCell cell : pathGround.values()) {
            assertEquals(64, cell.y());
            chunk.setBlockState(new BlockPos(cell.x(), cell.y(), cell.z()), cell.occupancy().state(), 2);
            assertTrue(chunk.getBlockState(new BlockPos(cell.x(), 63, cell.z())).isSolid());
            assertTrue(chunk.getBlockState(new BlockPos(cell.x(), 65, cell.z())).isAir());
            assertTrue(chunk.getBlockState(new BlockPos(cell.x(), 66, cell.z())).isAir());
            assertEquals(64, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cell.x(), cell.z()));
            assertEquals(64, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, cell.x(), cell.z()));
        }
        assertEquals(80, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 15, 0));
        assertEquals(60, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, 15, 0));
    }

    @Test
    public void flattenSupportsProjectedRoadsAndTheirSurroundingGradeAcrossRoofCavities() throws Exception {
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY,
                LevelHeightAccessor.create(0, 256), containerFactory(), null);
        chunk.setPersistedStatus(ChunkStatus.CARVERS);
        for (int x = 0; x <= 8; x++) {
            int originalY = x == 0 || x == 8 ? 64 : 96;
            write(chunk, x, 20, 0, Blocks.STONE.defaultBlockState());
            write(chunk, x, originalY - 1, 0, Blocks.DIRT.defaultBlockState());
            write(chunk, x, originalY, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        }
        write(chunk, 6, 50, 0, Blocks.WATER.defaultBlockState());
        WorldgenTerrainHeightmaps.primeTerrain(chunk,
                (x, z) -> x == 0 || x == 8 ? 65 : 97,
                (x, z) -> x == 0 || x == 8 ? 65 : 97);
        StructureTemplate foundation = template(List.of(block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece left = rigidTemplatePiece(new InlineSinglePoolElement(foundation),
                new BoundingBox(0, 65, 0, 0, 75, 0), 0, Rotation.NONE);
        PoolElementStructurePiece right = rigidTemplatePiece(new InlineSinglePoolElement(foundation),
                new BoundingBox(8, 65, 0, 8, 75, 0), 0, Rotation.NONE);
        PoolElementStructurePiece path = rigidTemplatePiece(new InlineSinglePoolElement(
                        template(List.of(block(0, 0, 0, Blocks.DIRT_PATH.defaultBlockState()),
                                block(1, 0, 0, Blocks.AIR.defaultBlockState()),
                                block(2, 0, 0, Blocks.DIRT_PATH.defaultBlockState()))),
                        List.of(), StructureTemplatePool.Projection.TERRAIN_MATCHING),
                new BoundingBox(4, 65, 0, 6, 75, 0), 0, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(left, path, right));
        NativeStructureTerrainIntegrator.TerrainTarget target = surfaceTarget(start, IrisStructureTerrainMode.FLATTEN);
        target.terrain().setHorizontalPadding(24).setFlattenRange(32);
        BoundingBox area = new BoundingBox(0, 0, 0, 8, 127, 0);
        WorldGenLevel world = world(chunk);

        NativeStructureSurfaceFitter.SurfaceTerrainPlan plan = NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world, area, List.of(target), (x, z) -> x == 0 || x == 8 ? 64 : 96);
        plan.primeHeightmaps(chunk);
        NativeStructureTemplateOccupancy.OccupancyResult occupancy = NativeStructureTemplateOccupancy.resolve(
                world, path, BlockPos.ZERO, area,
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager,
                area::isInside, cells -> {});
        Map<Long, NativeStructureTemplateOccupancy.LowestCell> pathGround =
                NativeStructureTemplateOccupancy.lowestProcessedSolidCells(occupancy.cells());

        assertEquals(2, pathGround.size());
        for (NativeStructureTemplateOccupancy.LowestCell cell : pathGround.values()) {
            write(chunk, cell.x(), cell.y(), cell.z(), cell.occupancy().state());
            assertTrue(cell.y() > 64 && cell.y() < 72);
            assertTrue(chunk.getBlockState(new BlockPos(cell.x(), cell.y() - 1, cell.z())).isSolid());
            assertTrue(chunk.getBlockState(new BlockPos(cell.x(), cell.y() + 1, cell.z())).isAir());
        }
        int roadY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 4, 0);
        for (int y = roadY - 32; y < roadY; y++) {
            assertTrue("Missing road support at " + y, chunk.getBlockState(new BlockPos(4, y, 0)).isSolid());
        }
        assertTrue(chunk.getBlockState(new BlockPos(4, roadY - 33, 0)).isAir());
        int adjacentY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 5, 0);
        for (int y = adjacentY - 32; y < adjacentY; y++) {
            assertTrue("Missing ground between road cells at " + y,
                    chunk.getBlockState(new BlockPos(5, y, 0)).isSolid());
        }
        assertEquals(Blocks.WATER.defaultBlockState(), chunk.getBlockState(new BlockPos(6, 50, 0)));
        assertTrue(chunk.getBlockState(new BlockPos(6, 49, 0)).isAir());
    }

    @Test
    public void flattenBuildsAContinuousVillageGradeBetweenSparseFoundations() throws Exception {
        StructureTemplate foundation = template(List.of(block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        for (int x : List.of(0, 8)) {
            for (int z : List.of(0, 8)) {
                pieces.add(rigidTemplatePiece(new InlineSinglePoolElement(foundation),
                        new BoundingBox(x, 65, z, x, 75, z), 0, Rotation.NONE));
            }
        }
        StructureStart start = rigidSurfaceStart(pieces);
        NativeStructureTerrainIntegrator.TerrainTarget target = surfaceTarget(start, IrisStructureTerrainMode.FLATTEN);
        target.terrain().setHorizontalPadding(24).setFlattenRange(64);
        BoundingBox area = new BoundingBox(0, 0, 0, 8, 127, 8);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                int originalY = (x == 0 || x == 8) && (z == 0 || z == 8) ? 64 : 96;
                put(blocks, x, 20, z, Blocks.STONE.defaultBlockState());
                put(blocks, x, originalY - 1, z, Blocks.DIRT.defaultBlockState());
                put(blocks, x, originalY, z, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        NativeStructureSurfaceFitter.SurfaceTerrainPlan plan = NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area, List.of(target),
                (x, z) -> (x == 0 || x == 8) && (z == 0 || z == 8) ? 64 : 96);

        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                int groundY = plan.flattenedHeights().get(NativeStructureTemplateOccupancy.columnKey(x, z)) - 1;
                assertTrue("Village grade at " + x + "," + z + ": " + groundY, groundY >= 64 && groundY <= 70);
                for (int y = 20; y <= groundY; y++) {
                    assertTrue("Disconnected village ground at " + x + "," + y + "," + z,
                            state(blocks, x, y, z).isSolid());
                }
                for (int y = groundY + 1; y <= 96; y++) {
                    assertTrue("Roof remains above village at " + x + "," + y + "," + z,
                            state(blocks, x, y, z).isAir());
                }
            }
        }
    }

    @Test
    public void sourceBeardAdjustmentsPrepareOnlyAtTheSurfaceStructuresStep() {
        assertTrue(NativeStructureSurfaceFitter.shouldPrepareSurfaceTerrain(
                TerrainAdjustment.BEARD_THIN, GenerationStep.Decoration.SURFACE_STRUCTURES));
        assertFalse(NativeStructureSurfaceFitter.shouldPrepareSurfaceTerrain(
                TerrainAdjustment.BEARD_BOX, GenerationStep.Decoration.FLUID_SPRINGS));
        assertFalse(NativeStructureSurfaceFitter.shouldPrepareSurfaceTerrain(
                TerrainAdjustment.BEARD_THIN, GenerationStep.Decoration.UNDERGROUND_STRUCTURES));
        assertFalse(NativeStructureSurfaceFitter.shouldPrepareSurfaceTerrain(
                TerrainAdjustment.BEARD_BOX, GenerationStep.Decoration.UNDERGROUND_DECORATION));
        assertFalse(NativeStructureSurfaceFitter.shouldPrepareSurfaceTerrain(
                TerrainAdjustment.BEARD_THIN, GenerationStep.Decoration.STRONGHOLDS));
    }

    @Test
    public void nonBeardAdjustmentsDoNotPrepareSurfaceTerrain() {
        assertFalse(NativeStructureSurfaceFitter.shouldPrepareSurfaceTerrain(
                TerrainAdjustment.BURY, GenerationStep.Decoration.SURFACE_STRUCTURES));
        assertFalse(NativeStructureSurfaceFitter.shouldPrepareSurfaceTerrain(
                TerrainAdjustment.ENCAPSULATE, GenerationStep.Decoration.SURFACE_STRUCTURES));
        assertFalse(NativeStructureSurfaceFitter.shouldPrepareSurfaceTerrain(
                TerrainAdjustment.NONE, GenerationStep.Decoration.FLUID_SPRINGS));
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area, List.of(surfaceTarget(start)),
                (x, z) -> 64);

        assertEquals(before, blocks);
    }

    @Test
    public void explicitTerrainOverrideDisablesSourceBeardFitting() {
        StructureStart start = desertStart(TerrainAdjustment.BEARD_BOX);

        assertTrue(NativeStructureSurfaceFitter.requiresSurfaceTerrain(
                new NativeStructureTerrainIntegrator.TerrainTarget(
                        "test:beard_box", start,
                        new IrisStructureTerrain().setMode(IrisStructureTerrainMode.SOURCE))));
        assertFalse(NativeStructureSurfaceFitter.requiresSurfaceTerrain(
                new NativeStructureTerrainIntegrator.TerrainTarget(
                        "test:beard_box", start,
                        new IrisStructureTerrain().setMode(IrisStructureTerrainMode.PRESERVE))));
    }

    @Test
    public void explicitVacuumUsesIndependentTerrainFittingWithoutAuthoredAdaptation() {
        StructureStart none = desertStart(TerrainAdjustment.NONE);
        StructureStart box = desertStart(TerrainAdjustment.BEARD_BOX);
        NativeStructureTerrainIntegrator.TerrainTarget sourceNone =
                new NativeStructureTerrainIntegrator.TerrainTarget(
                        "test:none", none,
                        new IrisStructureTerrain().setMode(IrisStructureTerrainMode.SOURCE));
        NativeStructureTerrainIntegrator.TerrainTarget vacuumNone =
                new NativeStructureTerrainIntegrator.TerrainTarget(
                        "test:none", none,
                        new IrisStructureTerrain().setMode(IrisStructureTerrainMode.VACUUM));
        NativeStructureTerrainIntegrator.TerrainTarget vacuumBox =
                new NativeStructureTerrainIntegrator.TerrainTarget(
                        "test:box", box,
                        new IrisStructureTerrain().setMode(IrisStructureTerrainMode.VACUUM));

        assertFalse(NativeStructureSurfaceFitter.requiresSurfaceTerrain(sourceNone));
        assertTrue(NativeStructureSurfaceFitter.requiresSurfaceTerrain(vacuumNone));
        assertTrue(NativeStructureSurfaceFitter.requiresSurfaceTerrain(vacuumBox));
        assertFalse(NativeStructureTerrainIntegrator.clearsLegacyTemplateAir(
                none, vacuumNone.terrain()));
    }

    @Test
    public void sourceSurfaceAnchorMeetsOneBlockBelowTheAuthoredGroundPlane() {
        BoundingBox bounds = new BoundingBox(0, 64, 0, 4, 90, 4);
        NativeStructureSurfaceFitter.SurfaceAnchor anchor =
                NativeStructureSurfaceFitter.surfaceAnchor(bounds, 68, 2);

        assertEquals(67, anchor.meetY());
        assertEquals(0, anchor.minX());
        assertEquals(4, anchor.maxX());
        assertEquals(0, anchor.minZ());
        assertEquals(4, anchor.maxZ());
    }

    @Test
    public void rigidFootprintsBoundTerrainAcrossGapsLargerThanTheBeardKernel() {
        NativeStructureSurfaceFitter.SurfaceAnchor rigid = anchor(72, 2);
        NativeStructureSurfaceFitter.SurfaceAnchor junction = anchor(72, 1);

        assertEquals(46, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(rigid), 2, 2, 40));
        assertEquals(46, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(junction), 2, 2, 40));
    }

    @Test
    public void beardSkirtBoundsAndGradesUpwardAndDownwardSymmetrically() {
        int originalY = 64;
        NativeStructureSurfaceFitter.SurfaceAnchor high = anchor(96, 2);
        NativeStructureSurfaceFitter.SurfaceAnchor low = anchor(32, 2);
        int[] raised = new int[13];
        int[] lowered = new int[13];
        for (int outset = 0; outset <= 12; outset++) {
            int x = 4 + outset;
            raised[outset] = NativeStructureSurfaceFitter.resolveSurfaceTarget(
                    List.of(high), x, 2, originalY);
            lowered[outset] = NativeStructureSurfaceFitter.resolveSurfaceTarget(
                    List.of(low), x, 2, originalY);
        }

        assertArrayEquals(
                new int[]{70, 69, 68, 67, 67, 66, 66, 65, 65, 64, 64, 64, 64},
                raised);
        assertArrayEquals(
                new int[]{58, 59, 60, 61, 61, 62, 62, 63, 63, 64, 64, 64, 64},
                lowered);
        for (int outset = 0; outset <= 12; outset++) {
            assertEquals(raised[outset] - originalY, originalY - lowered[outset]);
        }
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

        List<NativeStructureTerrainIntegrator.TerrainTarget> targets = List.of(
                new NativeStructureTerrainIntegrator.TerrainTarget(
                        "nova_structures:tavern_oak", start,
                        new IrisStructureTerrain().setMode(IrisStructureTerrainMode.SOURCE)));
        NativeStructureSurfaceFitter.prepareSurfaceStructures(
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area, List.of(surfaceTarget(start)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area, List.of(surfaceTarget(start)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area, List.of(surfaceTarget(start)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.SurfaceTerrainPlan plan =
                NativeStructureSurfaceFitter.prepareSurfaceStructures(
                        world(blocks), area,
                        List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
                        (x, z) -> 60);
        for (int y = 61; y <= 64; y++) {
            put(blocks, 0, y, 0, Blocks.CAVE_AIR.defaultBlockState());
        }
        put(blocks, 0, 65, 0, Blocks.COBBLESTONE.defaultBlockState());
        put(blocks, 1, 65, 0, Blocks.COBBLESTONE.defaultBlockState());
        put(blocks, 2, 67, 0, Blocks.COBBLESTONE.defaultBlockState());
        BlockState processorAirSupport = state(blocks, 1, 64, 0);
        BlockState authoredAirSupport = state(blocks, 2, 66, 0);

        NativeStructureSurfaceFitter.repairVacuumFoundations(
                world(blocks), area, plan);

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

        NativeStructureSurfaceFitter.SurfaceTerrainPlan plan =
                NativeStructureSurfaceFitter.prepareSurfaceStructures(
                        world(blocks), area,
                        List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
                        (x, z) -> 60);
        put(blocks, 0, 65, 0, Blocks.COBBLESTONE.defaultBlockState());

        NativeStructureSurfaceFitter.repairVacuumFoundations(
                world(blocks), area, plan);

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
        NativeStructureTerrainIntegrator.TerrainTarget target =
                surfaceTarget(start, IrisStructureTerrainMode.VACUUM);
        BoundingBox wideArea = new BoundingBox(0, 54, 0, 31, 72, 15);
        BoundingBox westArea = new BoundingBox(0, 54, 0, 15, 72, 15);
        BoundingBox eastArea = new BoundingBox(16, 54, 0, 31, 72, 15);
        Map<BlockPos, BlockState> wideBlocks = flatTerrain(wideArea, 60);
        Map<BlockPos, BlockState> splitBlocks = flatTerrain(wideArea, 60);

        NativeStructureSurfaceFitter.SurfaceTerrainPlan widePlan =
                NativeStructureSurfaceFitter.prepareSurfaceStructures(
                        world(wideBlocks), wideArea, List.of(target), (x, z) -> 60);
        NativeStructureSurfaceFitter.SurfaceTerrainPlan westPlan =
                NativeStructureSurfaceFitter.prepareSurfaceStructures(
                        world(splitBlocks), westArea, List.of(target), (x, z) -> 60);
        NativeStructureSurfaceFitter.SurfaceTerrainPlan eastPlan =
                NativeStructureSurfaceFitter.prepareSurfaceStructures(
                        world(splitBlocks), eastArea, List.of(target), (x, z) -> 60);
        for (int x = 15; x <= 16; x++) {
            for (int y = 61; y <= 64; y++) {
                put(wideBlocks, x, y, 0, Blocks.CAVE_AIR.defaultBlockState());
                put(splitBlocks, x, y, 0, Blocks.CAVE_AIR.defaultBlockState());
            }
            put(wideBlocks, x, 65, 0, Blocks.COBBLESTONE.defaultBlockState());
            put(splitBlocks, x, 65, 0, Blocks.COBBLESTONE.defaultBlockState());
        }

        NativeStructureSurfaceFitter.repairVacuumFoundations(
                world(wideBlocks), wideArea, widePlan);
        NativeStructureSurfaceFitter.repairVacuumFoundations(
                world(splitBlocks), eastArea, eastPlan);
        NativeStructureSurfaceFitter.repairVacuumFoundations(
                world(splitBlocks), westArea, westPlan);

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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
                (x, z) -> 50);

        assertEquals(originalBlocks, blocks);
    }

    @Test
    public void vacuumBudgetExhaustionSkipsFittingWithoutFailing() throws Exception {
        PoolElementStructurePiece piece = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 66, 0, 0, 70, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(piece), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 54, 0, 15, 72, 15);
        Map<BlockPos, BlockState> blocks = flatTerrain(area, 60);
        Map<BlockPos, BlockState> originalBlocks = new HashMap<>(blocks);

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
                (x, z) -> 60, 1);

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

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(blocks), area,
                List.of(surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
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
        NativeStructureTerrainIntegrator.TerrainTarget target =
                surfaceTarget(start, IrisStructureTerrainMode.VACUUM);
        BoundingBox wideArea = new BoundingBox(0, 54, 0, 31, 74, 15);
        BoundingBox westArea = new BoundingBox(0, 54, 0, 15, 74, 15);
        BoundingBox eastArea = new BoundingBox(16, 54, 0, 31, 74, 15);
        Map<BlockPos, BlockState> wideBlocks = flatTerrain(wideArea, 60);
        Map<BlockPos, BlockState> splitBlocks = flatTerrain(wideArea, 60);
        Map<BlockPos, BlockState> reverseSplitBlocks = flatTerrain(wideArea, 60);

        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(wideBlocks), wideArea, List.of(target), (x, z) -> 60);
        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(splitBlocks), westArea, List.of(target), (x, z) -> 60);
        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(splitBlocks), eastArea, List.of(target), (x, z) -> 60);
        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(reverseSplitBlocks), eastArea, List.of(target), (x, z) -> 60);
        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world(reverseSplitBlocks), westArea, List.of(target), (x, z) -> 60);

        assertEquals(wideBlocks, splitBlocks);
        assertEquals(wideBlocks, reverseSplitBlocks);
    }

    @Test
    public void surfaceAnchorCapsInsideAndIsUnchangedAtRadius() {
        NativeStructureSurfaceFitter.SurfaceAnchor anchor = anchor(72, 2);

        assertEquals(70, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(anchor), 2, 2, 64));
        assertEquals(64, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(anchor), 16, 2, 64));
        assertEquals(64, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(anchor), 17, 2, 64));
    }

    @Test
    public void surfaceAnchorRaisesAndLowersThroughTheTaper() {
        NativeStructureSurfaceFitter.SurfaceAnchor raised = anchor(68, 2);
        NativeStructureSurfaceFitter.SurfaceAnchor lowered = anchor(64, 2);

        assertEquals(65, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(raised), 10, 2, 64));
        assertEquals(67, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(lowered), 10, 2, 68));
    }

    @Test
    public void containingRigidFloorsHaveDeterministicPriority() {
        NativeStructureSurfaceFitter.SurfaceAnchor rigid = anchor(70, 2);
        NativeStructureSurfaceFitter.SurfaceAnchor junction = anchor(74, 1);

        assertEquals(70, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(rigid, junction), 2, 2, 64));
        assertEquals(70, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(junction, rigid), 2, 2, 64));

        NativeStructureSurfaceFitter.SurfaceAnchor weakTie = anchor(58, 1);
        NativeStructureSurfaceFitter.SurfaceAnchor strongTie = anchor(70, 2);
        assertEquals(70, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(weakTie, strongTie), 2, 2, 64));
        assertEquals(70, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(strongTie, weakTie), 2, 2, 64));
    }

    @Test
    public void postClearSupportUsesUpperOccupancyOverLowerLegacyAir() throws Exception {
        StructureTemplate lowerTemplate = template(List.of(
                block(0, 0, 0, Blocks.AIR.defaultBlockState()),
                block(1, 0, 0, Blocks.AIR.defaultBlockState())));
        StructureTemplate upperTemplate = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(1, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(lowerTemplate),
                new BoundingBox(0, 62, 0, 1, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineLegacyPoolElement(upperTemplate),
                new BoundingBox(0, 64, 0, 1, 70, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(lower, upper));
        BoundingBox area = new BoundingBox(0, 58, 0, 1, 72, 0);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x <= 1; x++) {
            put(blocks, x, 61, 0, Blocks.DIRT.defaultBlockState());
            put(blocks, x, 62, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        }
        put(blocks, 1, 63, 0, Blocks.DIRT.defaultBlockState());

        Set<Long> written = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(blocks), area, List.of(surfaceTarget(start)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertEquals(Set.of(BlockPos.asLong(0, 63, 0)), written);
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 0, 63, 0));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 1, 63, 0));
    }

    @Test
    public void rotatedLowestAuthoredVoidFluidAirAndJigsawColumnsRemainOpen() throws Exception {
        List<StructureTemplate.StructureBlockInfo> upperBlocks = new ArrayList<>();
        upperBlocks.add(block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()));
        upperBlocks.add(block(1, 0, 0, Blocks.AIR.defaultBlockState()));
        upperBlocks.add(block(2, 0, 0, Blocks.STRUCTURE_VOID.defaultBlockState()));
        upperBlocks.add(block(3, 0, 0, Blocks.WATER.defaultBlockState()));
        upperBlocks.add(block(4, 0, 0, Blocks.JIGSAW.defaultBlockState()));
        for (int x = 1; x <= 4; x++) {
            upperBlocks.add(block(x, 1, 0, Blocks.STONE.defaultBlockState()));
        }
        StructureTemplate upperTemplate = template(upperBlocks);
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(10, 62, 10, 10, 65, 14), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineSinglePoolElement(upperTemplate),
                new BoundingBox(10, 64, 10, 10, 70, 14), 1,
                Rotation.CLOCKWISE_90);
        StructureStart start = rigidSurfaceStart(List.of(lower, upper));
        BoundingBox area = new BoundingBox(10, 58, 10, 10, 72, 14);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int z = 10; z <= 14; z++) {
            put(blocks, 10, 61, z, Blocks.DIRT.defaultBlockState());
            put(blocks, 10, 62, z, Blocks.GRASS_BLOCK.defaultBlockState());
        }

        Set<Long> written = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(blocks), area, List.of(surfaceTarget(start)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.CLOCKWISE_90);
        BlockPos origin = upper.getPosition();
        BlockPos supportedBase = origin.offset(
                StructureTemplate.calculateRelativePosition(settings, BlockPos.ZERO));
        assertEquals(Set.of(supportedBase.below().asLong()), written);
        assertEquals(Blocks.DIRT.defaultBlockState(), state(
                blocks, supportedBase.getX(), supportedBase.getY() - 1, supportedBase.getZ()));
        for (int localX = 1; localX <= 4; localX++) {
            BlockPos vetoedBase = origin.offset(StructureTemplate.calculateRelativePosition(
                    settings, new BlockPos(localX, 0, 0)));
            assertEquals(Blocks.AIR.defaultBlockState(), state(
                    blocks, vetoedBase.getX(), vetoedBase.getY() - 1, vetoedBase.getZ()));
        }
    }

    @Test
    public void listElementsUseDeclaredOverlayOrderForLowestAuthoredCells() throws Exception {
        InlineLegacyPoolElement legacy = new InlineLegacyPoolElement(template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(1, 0, 0, Blocks.COBBLESTONE.defaultBlockState()))));
        InlineSinglePoolElement single = new InlineSinglePoolElement(template(List.of(
                block(0, 0, 0, Blocks.AIR.defaultBlockState()),
                block(1, 0, 0, Blocks.STONE.defaultBlockState()))));
        ListPoolElement list = new ListPoolElement(
                List.of(legacy, single), StructureTemplatePool.Projection.RIGID);
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 1, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                list, new BoundingBox(0, 64, 0, 1, 70, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(lower, upper));
        BoundingBox area = new BoundingBox(0, 58, 0, 1, 72, 0);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x <= 1; x++) {
            put(blocks, x, 61, 0, Blocks.DIRT.defaultBlockState());
            put(blocks, x, 62, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        }

        Set<Long> written = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(blocks), area, List.of(surfaceTarget(start)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertEquals(Set.of(BlockPos.asLong(1, 63, 0)), written);
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 63, 0));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 1, 63, 0));
    }

    @Test
    public void surfaceSupportIsChunkClippedAndStableAcrossSplitAreas() throws Exception {
        StructureTemplate upperTemplate = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(1, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(15, 62, 0, 16, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineSinglePoolElement(upperTemplate),
                new BoundingBox(15, 64, 0, 16, 70, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(lower, upper));
        BoundingBox wideArea = new BoundingBox(0, 58, 0, 31, 72, 15);
        BoundingBox westArea = new BoundingBox(0, 58, 0, 15, 72, 15);
        BoundingBox eastArea = new BoundingBox(16, 58, 0, 31, 72, 15);
        Map<BlockPos, BlockState> wideBlocks = supportTerrain(15, 16);
        Map<BlockPos, BlockState> splitBlocks = supportTerrain(15, 16);

        Set<Long> wide = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(wideBlocks), wideArea, List.of(surfaceTarget(start)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);
        Set<Long> split = new HashSet<>(
                NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                        world(splitBlocks), westArea, List.of(surfaceTarget(start)),
                        NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager));
        assertEquals(Blocks.AIR.defaultBlockState(), state(splitBlocks, 16, 63, 0));
        split.addAll(NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(splitBlocks), eastArea, List.of(surfaceTarget(start)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager));

        assertEquals(Set.of(
                BlockPos.asLong(15, 63, 0), BlockPos.asLong(16, 63, 0)), wide);
        assertEquals(wide, split);
        assertEquals(wideBlocks, splitBlocks);
    }

    @Test
    public void supportNeverPairsRigidAnchorsAcrossStarts() throws Exception {
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 0, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 64, 0, 0, 70, 0), 1, Rotation.NONE);
        StructureStart lowerStart = rigidSurfaceStart(List.of(lower));
        StructureStart upperStart = rigidSurfaceStart(List.of(upper));
        BoundingBox area = new BoundingBox(0, 58, 0, 0, 72, 0);
        Map<BlockPos, BlockState> blocks = supportTerrain(0, 0);

        assertTrue(NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(blocks), area, List.of(surfaceTarget(lowerStart)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager).isEmpty());
        assertTrue(NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(blocks), area, List.of(surfaceTarget(upperStart)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager).isEmpty());
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 63, 0));
    }

    @Test
    public void vacuumSupportsUnauthoredTerrainModesButLongMeetGapsRemainOpen() throws Exception {
        StructureTemplate upperTemplate = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())));
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 0, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineSinglePoolElement(upperTemplate),
                new BoundingBox(0, 64, 0, 0, 70, 0), 1, Rotation.NONE);
        StructureStart vacuumStart = rigidSurfaceStart(
                List.of(lower, upper), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 58, 0, 0, 72, 0);
        Map<BlockPos, BlockState> vacuumBlocks = supportTerrain(0, 0);

        Set<Long> vacuumWritten = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(vacuumBlocks), area,
                List.of(surfaceTarget(vacuumStart, IrisStructureTerrainMode.VACUUM)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertEquals(Set.of(BlockPos.asLong(0, 63, 0)), vacuumWritten);
        assertTrue(NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(supportTerrain(0, 0)), area,
                List.of(surfaceTarget(vacuumStart, IrisStructureTerrainMode.SOURCE)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager).isEmpty());

        PoolElementStructurePiece distantUpper = rigidTemplatePiece(
                new InlineSinglePoolElement(upperTemplate),
                new BoundingBox(0, 65, 0, 0, 71, 0), 1, Rotation.NONE);
        StructureStart distantStart = rigidSurfaceStart(
                List.of(lower, distantUpper), TerrainAdjustment.NONE);
        assertTrue(NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(supportTerrain(0, 0)), area,
                List.of(surfaceTarget(distantStart, IrisStructureTerrainMode.VACUUM)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager).isEmpty());
    }

    @Test
    public void threeRigidPlanesCannotBuildAnUpwardSupportChain() throws Exception {
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 0, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece middle = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 64, 0, 0, 69, 0), 0, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.STONE.defaultBlockState())))),
                new BoundingBox(0, 65, 0, 0, 70, 0), 0, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(lower, middle, upper));
        BoundingBox area = new BoundingBox(0, 58, 0, 0, 72, 0);
        Map<BlockPos, BlockState> blocks = supportTerrain(0, 0);

        Set<Long> written = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(blocks), area, List.of(surfaceTarget(start)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertEquals(Set.of(BlockPos.asLong(0, 63, 0)), written);
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 0, 63, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 64, 0));
    }

    @Test
    public void reversingTargetsCannotCreateCrossStartSupportChains() throws Exception {
        PoolElementStructurePiece ground = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 0, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece middle = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 64, 0, 0, 69, 0), 0, Rotation.NONE);
        PoolElementStructurePiece raisedGround = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 64, 0, 0, 69, 0), 0, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.STONE.defaultBlockState())))),
                new BoundingBox(0, 65, 0, 0, 70, 0), 0, Rotation.NONE);
        StructureStart lowerStart = rigidSurfaceStart(List.of(ground, middle));
        StructureStart upperStart = rigidSurfaceStart(List.of(raisedGround, upper));
        BoundingBox area = new BoundingBox(0, 58, 0, 0, 72, 0);
        Map<BlockPos, BlockState> forwardBlocks = supportTerrain(0, 0);
        Map<BlockPos, BlockState> reverseBlocks = supportTerrain(0, 0);
        NativeStructureTerrainIntegrator.TerrainTarget lowerTarget = surfaceTarget(lowerStart);
        NativeStructureTerrainIntegrator.TerrainTarget upperTarget = surfaceTarget(upperStart);

        Set<Long> forward = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(forwardBlocks), area, List.of(lowerTarget, upperTarget),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);
        Set<Long> reverse = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(reverseBlocks), area, List.of(upperTarget, lowerTarget),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertEquals(Set.of(BlockPos.asLong(0, 63, 0)), forward);
        assertEquals(forward, reverse);
        assertEquals(forwardBlocks, reverseBlocks);
        assertEquals(Blocks.AIR.defaultBlockState(), state(forwardBlocks, 0, 64, 0));
    }

    @Test
    public void preserveDuplicateCannotSuppressVacuumSupport() throws Exception {
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 0, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineSinglePoolElement(template(List.of(
                        block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())))),
                new BoundingBox(0, 64, 0, 0, 70, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(
                List.of(lower, upper), TerrainAdjustment.NONE);
        BoundingBox area = new BoundingBox(0, 58, 0, 0, 72, 0);
        Map<BlockPos, BlockState> blocks = supportTerrain(0, 0);

        Set<Long> written = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(blocks), area, List.of(
                        surfaceTarget(start, IrisStructureTerrainMode.PRESERVE),
                        surfaceTarget(start, IrisStructureTerrainMode.VACUUM)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertEquals(Set.of(BlockPos.asLong(0, 63, 0)), written);
    }

    @Test
    public void processorCreatedAirAndFluidVetoSurfaceSupport() throws Exception {
        StructureTemplate upperTemplate = template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()),
                block(1, 0, 0, Blocks.STONE.defaultBlockState())));
        InlineSinglePoolElement upperElement = new InlineSinglePoolElement(
                upperTemplate, List.of(
                replaceBlockProcessor(
                        Blocks.COBBLESTONE.defaultBlockState(),
                        Blocks.AIR.defaultBlockState()),
                replaceBlockProcessor(
                        Blocks.STONE.defaultBlockState(),
                        Blocks.WATER.defaultBlockState())));
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 1, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                upperElement, new BoundingBox(0, 64, 0, 1, 70, 0),
                1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(lower, upper));
        BoundingBox area = new BoundingBox(0, 58, 0, 1, 72, 0);
        Map<BlockPos, BlockState> blocks = supportTerrain(0, 1);

        Set<Long> written = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(blocks), area, List.of(surfaceTarget(start)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertTrue(written.isEmpty());
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 0, 63, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 1, 63, 0));
    }

    @Test
    public void solidJigsawFinalStateCreatesSurfaceSupport() throws Exception {
        CompoundTag jigsawData = new CompoundTag();
        jigsawData.putString("final_state", "minecraft:cobblestone");
        StructureTemplate upperTemplate = template(List.of(
                new StructureTemplate.StructureBlockInfo(
                        BlockPos.ZERO, Blocks.JIGSAW.defaultBlockState(), jigsawData)));
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 0, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                new InlineSinglePoolElement(upperTemplate),
                new BoundingBox(0, 64, 0, 0, 70, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(lower, upper));
        BoundingBox area = new BoundingBox(0, 58, 0, 0, 72, 0);
        Map<BlockPos, BlockState> blocks = supportTerrain(0, 0);

        Set<Long> written = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(blocks), area, List.of(surfaceTarget(start)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertEquals(Set.of(BlockPos.asLong(0, 63, 0)), written);
        assertEquals(Blocks.DIRT.defaultBlockState(), state(blocks, 0, 63, 0));
    }

    @Test
    public void laterLegacyAirDoesNotOverlayEarlierListSolid() throws Exception {
        InlineSinglePoolElement solid = new InlineSinglePoolElement(template(List.of(
                block(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()))));
        InlineLegacyPoolElement legacyAir = new InlineLegacyPoolElement(template(List.of(
                block(0, 0, 0, Blocks.AIR.defaultBlockState()))));
        ListPoolElement list = new ListPoolElement(
                List.of(solid, legacyAir), StructureTemplatePool.Projection.RIGID);
        PoolElementStructurePiece lower = rigidTemplatePiece(
                new InlineLegacyPoolElement(template(List.of(
                        block(0, 0, 0, Blocks.AIR.defaultBlockState())))),
                new BoundingBox(0, 62, 0, 0, 65, 0), 1, Rotation.NONE);
        PoolElementStructurePiece upper = rigidTemplatePiece(
                list, new BoundingBox(0, 64, 0, 0, 70, 0), 1, Rotation.NONE);
        StructureStart start = rigidSurfaceStart(List.of(lower, upper));
        BoundingBox area = new BoundingBox(0, 58, 0, 0, 72, 0);
        Map<BlockPos, BlockState> blocks = supportTerrain(0, 0);

        Set<Long> written = NativeStructureSurfaceSupportBuilder.bridgeRigidPieceSupport(
                world(blocks), area, List.of(surfaceTarget(start)),
                NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertEquals(Set.of(BlockPos.asLong(0, 63, 0)), written);
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
        List<NativeStructureTerrainIntegrator.TerrainTarget> targets =
                List.of(surfaceTarget(start));
        NativeStructureSurfaceFitter.prepareSurfaceStructures(
                world, area, targets, (x, z) -> 60);

        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 2, 62, 2));
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, 2, 65, 2));
    }

    @Test
    public void containingFootprintOverridesAnAdjacentPiecesFalloff() {
        NativeStructureSurfaceFitter.SurfaceAnchor local =
                new NativeStructureSurfaceFitter.SurfaceAnchor(0, 4, 0, 4, 66, 2);
        NativeStructureSurfaceFitter.SurfaceAnchor adjacent =
                new NativeStructureSurfaceFitter.SurfaceAnchor(5, 9, 0, 4, 70, 2);

        assertEquals(69, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(adjacent), 4, 2, 64));
        assertEquals(66, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(local, adjacent), 4, 2, 64));
        assertEquals(66, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(adjacent, local), 4, 2, 64));
    }

    @Test
    public void opposingFalloffsBlendWithoutAnAbruptMidpointSeam() {
        NativeStructureSurfaceFitter.SurfaceAnchor high =
                new NativeStructureSurfaceFitter.SurfaceAnchor(0, 0, 0, 0, 70, 2);
        NativeStructureSurfaceFitter.SurfaceAnchor low =
                new NativeStructureSurfaceFitter.SurfaceAnchor(12, 12, 0, 0, 58, 2);
        int previous = NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(high, low), 0, 0, 64);

        for (int x = 1; x <= 12; x++) {
            int forward = NativeStructureSurfaceFitter.resolveSurfaceTarget(
                    List.of(high, low), x, 0, 64);
            int reversed = NativeStructureSurfaceFitter.resolveSurfaceTarget(
                    List.of(low, high), x, 0, 64);
            assertEquals(forward, reversed);
            assertTrue(Math.abs(forward - previous) <= 6);
            previous = forward;
        }
        assertEquals(64, NativeStructureSurfaceFitter.resolveSurfaceTarget(
                List.of(high, low), 6, 0, 64));
    }

    @Test
    public void surfaceColumnsMutateTerrainAndRemoveUnsupportedDecoration() {
        Map<BlockPos, BlockState> lowered = new HashMap<>();
        put(lowered, 0, 61, 0, Blocks.STONE.defaultBlockState());
        put(lowered, 0, 62, 0, Blocks.DIRT.defaultBlockState());
        put(lowered, 0, 63, 0, Blocks.DIRT.defaultBlockState());
        put(lowered, 0, 64, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        put(lowered, 0, 65, 0, Blocks.DANDELION.defaultBlockState());

        NativeStructureSurfaceFitter.applySurfaceColumn(
                world(lowered), new BlockPos.MutableBlockPos(),
                0, 0, 64, 62, -64, 319);

        assertEquals(Blocks.STONE.defaultBlockState(), state(lowered, 0, 61, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(lowered, 0, 62, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(lowered, 0, 63, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(lowered, 0, 64, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), state(lowered, 0, 65, 0));

        Map<BlockPos, BlockState> raised = new HashMap<>();
        put(raised, 0, 63, 0, Blocks.DIRT.defaultBlockState());
        put(raised, 0, 64, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        put(raised, 0, 65, 0, Blocks.DANDELION.defaultBlockState());

        NativeStructureSurfaceFitter.applySurfaceColumn(
                world(raised), new BlockPos.MutableBlockPos(),
                0, 0, 64, 68, -64, 319);

        assertEquals(Blocks.DIRT.defaultBlockState(), state(raised, 0, 65, 0));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(raised, 0, 66, 0));
        assertEquals(Blocks.DIRT.defaultBlockState(), state(raised, 0, 67, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(raised, 0, 68, 0));
    }

    @Test
    public void rigidSurfaceBaseClosesAOneBlockSubsurfaceGap() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        put(blocks, 0, 62, 0, Blocks.STONE.defaultBlockState());
        put(blocks, 0, 64, 0, Blocks.GRASS_BLOCK.defaultBlockState());

        NativeStructureSurfaceFitter.applySurfaceColumn(
                world(blocks), new BlockPos.MutableBlockPos(),
                0, 0, 64, 64, -64, 319, true);

        assertEquals(Blocks.STONE.defaultBlockState(), state(blocks, 0, 63, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), state(blocks, 0, 64, 0));
    }

    @Test
    public void rigidSurfaceBaseReconstructsAMissingMeetLayer() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        put(blocks, 0, 62, 0, Blocks.STONE.defaultBlockState());

        NativeStructureSurfaceFitter.applySurfaceColumn(
                world(blocks), new BlockPos.MutableBlockPos(),
                0, 0, 64, 64, -64, 319, true);

        assertEquals(Blocks.STONE.defaultBlockState(), state(blocks, 0, 63, 0));
        assertEquals(Blocks.STONE.defaultBlockState(), state(blocks, 0, 64, 0));
    }

    @Test
    public void rigidSurfaceBaseReconstructsAMissingMeetLayerAtTheWorldFloor() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        NativeStructureSurfaceFitter.applySurfaceColumn(
                world(blocks), new BlockPos.MutableBlockPos(),
                0, 0, -64, -64, -64, 319, true);

        assertEquals(Blocks.STONE.defaultBlockState(), state(blocks, 0, -64, 0));
    }

    @Test
    public void raisedSurfaceTerrainDoesNotSliceTreeBlocks() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        put(blocks, 0, 63, 0, Blocks.DIRT.defaultBlockState());
        put(blocks, 0, 64, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        put(blocks, 0, 66, 0, log);
        put(blocks, 0, 68, 0, leaves);

        NativeStructureSurfaceFitter.applySurfaceColumn(
                world(blocks), new BlockPos.MutableBlockPos(),
                0, 0, 64, 68, -64, 319);

        assertEquals(log, state(blocks, 0, 66, 0));
        assertEquals(leaves, state(blocks, 0, 68, 0));
    }

    @Test
    public void clearedIntersectingTreeCellsBecomeRaisedTerrainSupport() {
        ProtoChunk chunk = new ProtoChunk(
                new ChunkPos(0, 0), UpgradeData.EMPTY,
                LevelHeightAccessor.create(-64, 384), containerFactory(), null);
        write(chunk, 0, 63, 0, Blocks.DIRT.defaultBlockState());
        write(chunk, 0, 64, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        write(chunk, 0, 66, 0, Blocks.OAK_LOG.defaultBlockState());
        write(chunk, 0, 68, 0, Blocks.OAK_LEAVES.defaultBlockState());
        WorldGenLevel world = world(chunk);
        BoundingBox area = new BoundingBox(0, 63, 0, 0, 68, 0);

        NativeStructureVegetationClearer.clearIntersectingVegetation(
                world, chunk, area, List.of(desertStart()));
        NativeStructureSurfaceFitter.applySurfaceColumn(
                world, new BlockPos.MutableBlockPos(),
                0, 0, 64, 68, -64, 319);

        assertEquals(Blocks.DIRT.defaultBlockState(), chunk.getBlockState(new BlockPos(0, 65, 0)));
        assertEquals(Blocks.DIRT.defaultBlockState(), chunk.getBlockState(new BlockPos(0, 66, 0)));
        assertEquals(Blocks.DIRT.defaultBlockState(), chunk.getBlockState(new BlockPos(0, 67, 0)));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), chunk.getBlockState(new BlockPos(0, 68, 0)));
    }

    @Test
    public void loweredFluidColumnsRemainFluidFilled() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        put(blocks, 0, 62, 0, Blocks.DIRT.defaultBlockState());
        put(blocks, 0, 63, 0, Blocks.DIRT.defaultBlockState());
        put(blocks, 0, 64, 0, Blocks.GRAVEL.defaultBlockState());
        put(blocks, 0, 65, 0, Blocks.WATER.defaultBlockState());

        NativeStructureSurfaceFitter.applySurfaceColumn(
                world(blocks), new BlockPos.MutableBlockPos(),
                0, 0, 64, 62, -64, 319);

        assertEquals(Blocks.WATER.defaultBlockState(), state(blocks, 0, 63, 0));
        assertEquals(Blocks.WATER.defaultBlockState(), state(blocks, 0, 64, 0));
        assertEquals(Blocks.WATER.defaultBlockState(), state(blocks, 0, 65, 0));
        assertEquals(Blocks.GRAVEL.defaultBlockState(), state(blocks, 0, 62, 0));
    }

    @Test
    public void preserveSourceYSkipsBurialAndKeepsTheVanillaStartY() {
        StructureStart start = desertStart();
        int minY = start.getBoundingBox().minY();

        int offset = NativeStructureVerticalPlacer.applyVerticalShift(
                start, 0, -64, 320, true, true, null, (x, z) -> 40);

        assertEquals(0, offset);
        assertEquals(minY, start.getBoundingBox().minY());
    }

    @Test
    public void preserveSourceYStillAppliesAnExplicitShift() {
        StructureStart start = desertStart();
        int minY = start.getBoundingBox().minY();

        int offset = NativeStructureVerticalPlacer.applyVerticalShift(
                start, -8, -64, 320, true, true, null, (x, z) -> 40);

        assertEquals(-8, offset);
        assertEquals(minY - 8, start.getBoundingBox().minY());
    }

    @Test
    public void burialStillSinksTheStructureBelowTheLowestTerrainColumn() {
        StructureStart start = desertStart();
        int minY = start.getBoundingBox().minY();
        int maxY = start.getBoundingBox().maxY();
        int expected = 40 - 1 - maxY;

        int offset = NativeStructureVerticalPlacer.applyVerticalShift(
                start, 0, -64, 320, true, false, null, (x, z) -> 40);

        assertEquals(expected, offset);
        assertEquals(minY + expected, start.getBoundingBox().minY());
    }

    @Test
    public void unfittableBurialClampsToTheWorldFloorInsteadOfAborting() {
        StructureStart start = desertStart();
        int minY = start.getBoundingBox().minY();
        int worldMinY = minY - 4;

        int offset = NativeStructureVerticalPlacer.applyVerticalShift(
                start, 0, worldMinY, 320, true, false, null, (x, z) -> worldMinY);

        assertEquals(-4, offset);
        assertEquals(worldMinY, start.getBoundingBox().minY());
    }

    @Test
    public void nativeVacuumLeavesPieceBlocksForSurfaceFitting() {
        StructureStart start = desertStart();
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        put(blocks, bounds.minX(), bounds.minY(), bounds.minZ(), Blocks.STONE.defaultBlockState());

        NativeStructureTerrainIntegrator.integrateTerrain(
                world(blocks), bounds, "minecraft:desert_pyramid", start,
                new IrisStructureTerrain().setMode(IrisStructureTerrainMode.VACUUM), null);

        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, bounds.minX(), bounds.minY(), bounds.minZ()));
    }

    @Test
    public void nativeForceCarveHonorsConfiguredPadding() {
        StructureStart start = desertStart();
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        BoundingBox area = new BoundingBox(
                bounds.minX() - 1, bounds.minY(), bounds.minZ() - 1,
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        put(blocks, bounds.maxX() + 1, bounds.minY(), bounds.maxZ(),
                Blocks.STONE.defaultBlockState());
        put(blocks, bounds.maxX(), bounds.maxY() + 1, bounds.maxZ(),
                Blocks.STONE.defaultBlockState());

        NativeStructureTerrainIntegrator.integrateTerrain(
                world(blocks), area, "minecraft:desert_pyramid", start,
                new IrisStructureTerrain()
                        .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                        .setHorizontalPadding(1)
                        .setCeilingPadding(1), null);

        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, bounds.maxX() + 1, bounds.minY(), bounds.maxZ()));
        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, bounds.maxX(), bounds.maxY() + 1, bounds.maxZ()));
    }

    @Test
    public void nativeForceCarveUsesPieceUnionInsteadOfCombinedBounds() {
        Structure structure = new DesertPyramidStructure(
                new Structure.StructureSettings(HolderSet.empty()));
        DesertPyramidPiece first = new DesertPyramidPiece(RandomSource.create(7L), 0, 0);
        DesertPyramidPiece second = new DesertPyramidPiece(RandomSource.create(8L), 0, 0);
        second.move(64, 0, 0);
        StructureStart start = new StructureStart(
                structure, new ChunkPos(0, 0), 0,
                new PiecesContainer(List.of(first, second)));
        BoundingBox firstBounds = first.getBoundingBox();
        BoundingBox secondBounds = second.getBoundingBox();
        int gapX = (firstBounds.maxX() + secondBounds.minX()) / 2;
        int y = firstBounds.minY();
        int z = firstBounds.minZ();
        BoundingBox area = new BoundingBox(
                firstBounds.minX(), y, z,
                secondBounds.maxX(), y, z);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        put(blocks, firstBounds.minX(), y, z, Blocks.STONE.defaultBlockState());
        put(blocks, gapX, y, z, Blocks.STONE.defaultBlockState());

        NativeStructureTerrainIntegrator.integrateTerrain(
                world(blocks), area, "minecraft:ancient_city", start,
                new IrisStructureTerrain().setMode(IrisStructureTerrainMode.FORCE_CARVE), null);

        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, firstBounds.minX(), y, z));
        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, gapX, y, z));
    }

    @Test
    public void templateBackedColumnsUseTheTemplateAirComplement() throws Exception {
        StructureTemplate template = template(List.of(
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(0, 0, 0), Blocks.AIR.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(0, 1, 0), Blocks.AIR.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(0, 2, 0), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(1, 0, 0), Blocks.STRUCTURE_VOID.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(1, 1, 0), Blocks.DEEPSLATE.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(1, 2, 0), Blocks.DEEPSLATE.defaultBlockState(), null)));
        Map<Long, int[]> columns = new HashMap<>();

        assertTrue(NativeStructureTerrainIntegrator.emitTemplateColumns(
                List.of(template), new BlockPos(0, 0, 0), Rotation.NONE,
                new BoundingBox(0, 0, 0, 1, 2, 0),
                (x, z, minY, maxY) -> columns.put((long) x << 32 | z & 0xffffffffL,
                        new int[]{minY, maxY})));

        assertEquals(2, columns.size());
        assertArrayEquals(new int[]{2, 2}, columns.get(0L));
        assertArrayEquals(new int[]{1, 2}, columns.get(1L << 32));
    }

    @Test
    public void fullyVoidTemplateColumnsAreNotCarveSources() throws Exception {
        StructureTemplate template = template(List.of(
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(0, 0, 0), Blocks.AIR.defaultBlockState(), null),
                new StructureTemplate.StructureBlockInfo(
                        new BlockPos(0, 1, 0), Blocks.STRUCTURE_VOID.defaultBlockState(), null)));
        Map<Long, int[]> columns = new HashMap<>();

        assertTrue(NativeStructureTerrainIntegrator.emitTemplateColumns(
                List.of(template), new BlockPos(0, 0, 0), Rotation.NONE,
                new BoundingBox(0, 0, 0, 0, 1, 0),
                (x, z, minY, maxY) -> columns.put((long) x << 32 | z & 0xffffffffL,
                        new int[]{minY, maxY})));

        assertTrue(columns.isEmpty());
    }

    @Test
    public void nonTemplatePiecesFallBackToTheirBoundingBoxColumns() {
        StructureStart start = desertStart();
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();

        StructureCarvingFootprint footprint = NativeStructureTerrainIntegrator.carveFootprint(
                start, 4, NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertEquals(bounds.minX() - 4, footprint.minX());
        assertEquals(bounds.maxX() + 4, footprint.maxX());
        assertEquals(bounds.minZ() - 4, footprint.minZ());
        assertEquals(bounds.maxZ() + 4, footprint.maxZ());
        assertEquals(0L, footprint.distanceSquaredAt(bounds.minX(), bounds.minZ()));
        assertEquals(32L, footprint.distanceSquaredAt(bounds.minX() - 4, bounds.minZ() - 4));
        assertEquals(bounds.minY(), footprint.sourceMinYAt(bounds.minX(), bounds.minZ()));
        assertEquals(bounds.maxY(), footprint.sourceMaxYAt(bounds.minX(), bounds.minZ()));
    }

    @Test
    public void carveFootprintIsComputedOncePerStartAndPadding() {
        StructureStart start = desertStart();

        StructureCarvingFootprint first = NativeStructureTerrainIntegrator.carveFootprint(
                start, 6, NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);
        StructureCarvingFootprint repeated = NativeStructureTerrainIntegrator.carveFootprint(
                start, 6, NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);
        StructureCarvingFootprint widened = NativeStructureTerrainIntegrator.carveFootprint(
                start, 7, NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);
        StructureCarvingFootprint other = NativeStructureTerrainIntegrator.carveFootprint(
                desertStart(), 6, NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);

        assertSame(first, repeated);
        assertNotSame(first, widened);
        assertNotSame(first, other);
    }

    @Test
    public void concurrentCarveRequestsShareOneFootprintBuild() throws Exception {
        StructureStart start = desertStart();
        int requestCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<StructureCarvingFootprint>> futures = new ArrayList<>();
        try {
            for (int request = 0; request < requestCount; request++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    release.await();
                    return NativeStructureTerrainIntegrator.carveFootprint(
                            start, 11,
                            NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            release.countDown();
            StructureCarvingFootprint expected = futures.getFirst().get();
            for (Future<StructureCarvingFootprint> future : futures) {
                assertSame(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void carveFootprintCacheStaysInsideItsCellBudget() {
        for (int index = 0; index < 40; index++) {
            NativeStructureTerrainIntegrator.carveFootprint(
                    desertStart(), 128,
                    NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager);
        }

        assertTrue(NativeStructureTerrainIntegrator.cachedCarveFootprintCells()
                <= NativeStructureTerrainIntegrator.maximumCachedCarveFootprintCells());
    }

    @Test
    public void organicCarveNeverCutsBelowTheColumnSupportingFloor() {
        StructureStart start = desertStart();
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        NativeStructureTerrainIntegrator.OrganicCarve carve = organicCarve(start, 6);
        BoundingBox area = new BoundingBox(
                bounds.minX() - 6, bounds.minY() - 4, bounds.minZ() - 6,
                bounds.maxX() + 6, bounds.maxY() + 12, bounds.maxZ() + 6);
        Map<BlockPos, BlockState> blocks = fill(area);

        NativeStructureTerrainIntegrator.carveOrganicColumns(world(blocks), area, carve);

        int centerX = bounds.minX() + bounds.getXSpan() / 2;
        int centerZ = bounds.minZ() + bounds.getZSpan() / 2;
        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, centerX, bounds.minY(), centerZ));
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.minY(); y < bounds.minY(); y++) {
                    assertEquals(Blocks.STONE.defaultBlockState(), state(blocks, x, y, z));
                }
            }
        }
    }

    @Test
    public void lobedCarveStaysInsideTheUniformCarveAndRemovesLess() {
        StructureStart start = desertStart();
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        BoundingBox area = new BoundingBox(
                bounds.minX() - 10, bounds.minY(), bounds.minZ() - 10,
                bounds.maxX() + 10, bounds.maxY() + 12, bounds.maxZ() + 10);
        Map<BlockPos, BlockState> uniformBlocks = fill(area);
        Map<BlockPos, BlockState> lobedBlocks = fill(area);

        NativeStructureTerrainIntegrator.carveOrganicColumns(
                world(uniformBlocks), area, organicCarve(start, 10, 0D));
        NativeStructureTerrainIntegrator.carveOrganicColumns(
                world(lobedBlocks), area, organicCarve(start, 10, 0.85D));

        int uniform = 0;
        int lobed = 0;
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.minY(); y <= area.maxY(); y++) {
                    boolean uniformAir = state(uniformBlocks, x, y, z).isAir();
                    boolean lobedAir = state(lobedBlocks, x, y, z).isAir();
                    assertTrue("lobe carved outside the uniform padding at "
                            + x + "," + y + "," + z, uniformAir || !lobedAir);
                    uniform += uniformAir ? 1 : 0;
                    lobed += lobedAir ? 1 : 0;
                }
            }
        }

        assertTrue(lobed > 0);
        assertTrue("uniform " + uniform + " lobed " + lobed, lobed < uniform);
    }

    @Test
    public void lobedCarveDepthWandersAlongAStraightFootprintEdge() {
        int[] uniform = slabEdgeCarveDepths(0D);
        int[] lobed = slabEdgeCarveDepths(0.85D);

        assertTrue("uniform depths wander " + span(uniform), span(uniform) <= 2);
        assertTrue("lobed depths wander " + span(lobed), span(lobed) >= 6);
        for (int depth : lobed) {
            assertTrue(depth >= 0 && depth <= SLAB_PADDING);
        }
    }

    @Test
    public void organicCarveIsIdenticalAcrossNeighboringChunkContexts() {
        StructureStart start = desertStart();
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        BoundingBox wide = new BoundingBox(
                bounds.minX() - 6, bounds.minY(), bounds.minZ() - 6,
                bounds.maxX() + 6, bounds.maxY() + 12, bounds.maxZ() + 6);
        BoundingBox narrow = new BoundingBox(
                bounds.maxX() - 3, bounds.minY(), bounds.maxZ() - 3,
                bounds.maxX() + 6, bounds.maxY() + 12, bounds.maxZ() + 6);
        Map<BlockPos, BlockState> wideBlocks = fill(wide);
        Map<BlockPos, BlockState> narrowBlocks = fill(narrow);

        // Each chunk context rebuilds its own noise channels from the shared start identity.
        NativeStructureTerrainIntegrator.carveOrganicColumns(
                world(wideBlocks), wide, organicCarve(start, 6));
        NativeStructureTerrainIntegrator.carveOrganicColumns(
                world(narrowBlocks), narrow, organicCarve(start, 6));

        int carved = 0;
        for (int x = narrow.minX(); x <= narrow.maxX(); x++) {
            for (int z = narrow.minZ(); z <= narrow.maxZ(); z++) {
                for (int y = narrow.minY(); y <= narrow.maxY(); y++) {
                    BlockState expected = state(wideBlocks, x, y, z);
                    assertEquals(expected, state(narrowBlocks, x, y, z));
                    if (expected.isAir()) {
                        carved++;
                    }
                }
            }
        }
        assertTrue(carved > 0);
    }

    @Test
    public void erodedForceCarveShrinkwrapsWithoutUnderminingTheFloor() {
        StructureStart start = desertStart();
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        BoundingBox area = new BoundingBox(
                bounds.minX() - 6, bounds.minY() - 2, bounds.minZ() - 6,
                bounds.maxX() + 6, bounds.maxY() + 12, bounds.maxZ() + 6);
        Map<BlockPos, BlockState> blocks = fill(area);
        int centerX = bounds.minX() + bounds.getXSpan() / 2;
        int centerZ = bounds.minZ() + bounds.getZSpan() / 2;

        NativeStructureTerrainIntegrator.integrateTerrain(
                world(blocks), area, "minecraft:ancient_city", start,
                new IrisStructureTerrain()
                        .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                        .setShape(IrisStructureCarveShape.ERODED)
                        .setHorizontalPadding(6)
                        .setCeilingPadding(8)
                        .setFloorPadding(0)
                        .setErosionStrength(1D)
                        .setErosionFrequency(0.05D), null);

        assertEquals(Blocks.AIR.defaultBlockState(), state(blocks, centerX, bounds.minY(), centerZ));
        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, centerX, bounds.minY() - 1, centerZ));
        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, area.minX(), area.maxY(), area.minZ()));
    }

    @Test
    public void sparseStiltGridIsDeterministicAndPreflightsGround() {
        assertTrue(NativeStructureFoundationBuilder.isStiltColumn(0, 0, 4));
        assertTrue(NativeStructureFoundationBuilder.isStiltColumn(-4, 8, 4));
        assertFalse(NativeStructureFoundationBuilder.isStiltColumn(1, 0, 4));
        assertTrue(NativeStructureFoundationBuilder.isStiltColumn(1, 1, 1));

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        put(blocks, 0, 7, 0, Blocks.DEEPSLATE.defaultBlockState());
        put(blocks, 0, 8, 0, Blocks.SCULK_VEIN.defaultBlockState());
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

        assertEquals(7, NativeStructureFoundationBuilder.findStiltAnchorY(
                world(blocks), 0, 0, 10, 2, -64, -64, position));
        assertEquals(Integer.MIN_VALUE, NativeStructureFoundationBuilder.findStiltAnchorY(
                world(blocks), 0, 0, 10, 1, -64, -64, position));
        assertEquals(Integer.MIN_VALUE, NativeStructureFoundationBuilder.findStiltAnchorY(
                world(new HashMap<>()), 0, 0, 10, 64, -64, -64, position));
    }

    @Test
    public void nativeTerrainEnvelopePersistsHorizontalReferenceCoverage() {
        StructureStart generated = desertStart();
        BoundingBox content = generated.getBoundingBox();
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setHorizontalPadding(24);

        StructureStart wrapped = NativeStructureReferenceEnvelope.wrap(
                generated,
                generated.getStructure(),
                0,
                terrain);
        BoundingBox referenceBounds = NativeStructureReferenceEnvelope.referenceBounds(
                wrapped, wrapped.getStructure(), terrain);

        assertEquals(content, NativeStructureReferenceEnvelope.contentBounds(wrapped));
        assertEquals(content, wrapped.getBoundingBox());
        assertEquals(content.minX() - 24, referenceBounds.minX());
        assertEquals(content.minZ() - 24, referenceBounds.minZ());
        assertEquals(content.maxX() + 24, referenceBounds.maxX());
        assertEquals(content.maxZ() + 24, referenceBounds.maxZ());
        assertEquals(generated.getPieces().size(), wrapped.getPieces().size());
    }

    @Test
    public void nativeVacuumReservesItsFixedSurfaceFalloff() {
        StructureStart generated = desertStart(TerrainAdjustment.NONE);
        BoundingBox content = NativeStructureReferenceEnvelope.contentBounds(generated);
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.VACUUM)
                .setHorizontalPadding(64);

        BoundingBox references = NativeStructureReferenceEnvelope.referenceBounds(
                generated, generated.getStructure(), terrain);

        assertEquals(content.minX() - NativeStructureSurfaceFitter.surfaceTerrainRadius(),
                references.minX());
        assertEquals(content.maxX() + NativeStructureSurfaceFitter.surfaceTerrainRadius(),
                references.maxX());
        assertEquals(content.minZ() - NativeStructureSurfaceFitter.surfaceTerrainRadius(),
                references.minZ());
        assertEquals(content.maxZ() + NativeStructureSurfaceFitter.surfaceTerrainRadius(),
                references.maxZ());
    }

    @Test
    public void nativeTerrainEnvelopeClipsOptionalCoverageWithoutDroppingContent() {
        StructureStart generated = desertStart();
        ChunkPos origin = generated.getChunkPos();
        BoundingBox content = NativeStructureReferenceEnvelope.contentBounds(generated);
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setHorizontalPadding(124);

        BoundingBox references = NativeStructureReferenceEnvelope.referenceBounds(
                generated, generated.getStructure(), terrain, "test:clipped_terrain");
        StructureStart published = NativeStructureReferenceEnvelope.wrapForPublication(
                generated, generated.getStructure(), 0, terrain, "test:clipped_terrain");

        assertEquals(Math.max(content.minX() - 124, (origin.x() - 8) << 4), references.minX());
        assertEquals(Math.min(content.maxX() + 124, ((origin.x() + 8) << 4) + 15), references.maxX());
        assertEquals(Math.max(content.minZ() - 124, (origin.z() - 8) << 4), references.minZ());
        assertEquals(Math.min(content.maxZ() + 124, ((origin.z() + 8) << 4) + 15), references.maxZ());
        assertTrue(references.isInside(content.getCenter()));
        assertTrue(published.isValid());
        assertEquals(1, generated.getPieces().size());
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
    public void runtimeTemplatesAndLegacyAirUseTheExactContract() {
        StructureTemplate runtimeTemplate = new StructureTemplate();

        assertEquals(runtimeTemplate, NativeStructureReflection.resolveTemplateReference(
                Either.right(runtimeTemplate), null));
        assertFalse(NativeStructureTerrainIntegrator.shouldClearLegacyAir(79, 80, false));
        assertTrue(NativeStructureTerrainIntegrator.shouldClearLegacyAir(80, 80, false));
        assertTrue(NativeStructureTerrainIntegrator.shouldClearLegacyAir(96, 80, false));
        assertFalse(NativeStructureTerrainIntegrator.shouldClearLegacyAir(96, 80, true));
    }

    @Test
    public void rotatedTemplateAirClearsOnlyInsideTheChunkAndAboveTheFloor() throws Exception {
        StructureTemplate.StructureBlockInfo clear = new StructureTemplate.StructureBlockInfo(
                new BlockPos(1, 0, 0), Blocks.AIR.defaultBlockState(), null);
        StructureTemplate.StructureBlockInfo belowFloor = new StructureTemplate.StructureBlockInfo(
                new BlockPos(0, -1, 0), Blocks.AIR.defaultBlockState(), null);
        StructureTemplate.StructureBlockInfo outsideChunk = new StructureTemplate.StructureBlockInfo(
                new BlockPos(3, 0, 0), Blocks.AIR.defaultBlockState(), null);
        StructureTemplate template = template(List.of(clear, belowFloor, outsideChunk));
        BlockPos origin = new BlockPos(10, 80, 10);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90);
        BlockPos clearPosition = origin.offset(StructureTemplate.calculateRelativePosition(settings, clear.pos()));
        BlockPos belowFloorPosition = origin.offset(
                StructureTemplate.calculateRelativePosition(settings, belowFloor.pos()));
        BlockPos outsidePosition = origin.offset(
                StructureTemplate.calculateRelativePosition(settings, outsideChunk.pos()));
        BoundingBox area = new BoundingBox(
                Math.min(clearPosition.getX(), belowFloorPosition.getX()),
                Math.min(clearPosition.getY(), belowFloorPosition.getY()),
                Math.min(clearPosition.getZ(), belowFloorPosition.getZ()),
                Math.max(clearPosition.getX(), belowFloorPosition.getX()),
                Math.max(clearPosition.getY(), belowFloorPosition.getY()),
                Math.max(clearPosition.getZ(), belowFloorPosition.getZ()));
        settings.setBoundingBox(area);
        assertFalse(area.isInside(outsidePosition));
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(clearPosition, Blocks.DIRT.defaultBlockState());
        blocks.put(belowFloorPosition, Blocks.DIRT.defaultBlockState());
        blocks.put(outsidePosition, Blocks.DIRT.defaultBlockState());

        NativeStructureTerrainIntegrator.clearTemplateAir(
                world(blocks), template, origin, 80, settings);

        assertEquals(Blocks.AIR.defaultBlockState(), blocks.get(clearPosition));
        assertEquals(Blocks.DIRT.defaultBlockState(), blocks.get(belowFloorPosition));
        assertEquals(Blocks.DIRT.defaultBlockState(), blocks.get(outsidePosition));
    }

    @Test
    public void unrelatedPieceBoundsAreRejectedBeforeTemplateScanning() {
        BoundingBox area = new BoundingBox(0, -64, 0, 15, 319, 15);

        assertTrue(NativeStructureTerrainIntegrator.intersects(
                new BoundingBox(15, 60, 15, 30, 90, 30), area));
        assertFalse(NativeStructureTerrainIntegrator.intersects(
                new BoundingBox(16, 60, 16, 30, 90, 30), area));
        assertFalse(NativeStructureTerrainIntegrator.intersects(
                new BoundingBox(0, 320, 0, 15, 350, 15), area));
    }

    @Test
    public void templateAirDoesNotEraseTreesInsideVillagePieces() throws Exception {
        BlockPos origin = new BlockPos(0, 80, 0);
        StructureTemplate template = template(List.of(
                new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, Blocks.AIR.defaultBlockState(), null)));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        blocks.put(origin, log);

        NativeStructureTerrainIntegrator.clearTemplateAir(
                world(blocks), template, origin, 80, settings);

        assertEquals(log, blocks.get(origin));
    }

    private static NativeStructureSurfaceFitter.SurfaceAnchor anchor(int meetY, int strength) {
        return new NativeStructureSurfaceFitter.SurfaceAnchor(0, 4, 0, 4, meetY, strength);
    }

    private static PoolElementStructurePiece rigidPiece(BoundingBox bounds, int groundLevelDelta) {
        StructurePoolElement element = StructurePoolElement.single(
                "minecraft:empty").apply(StructureTemplatePool.Projection.RIGID);
        return new PoolElementStructurePiece(
                null, element,
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                groundLevelDelta, Rotation.NONE, bounds,
                LiquidSettings.APPLY_WATERLOGGING);
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

    private static StructureStart rigidSurfaceStart(List<PoolElementStructurePiece> pieces) {
        return rigidSurfaceStart(pieces, TerrainAdjustment.BEARD_BOX);
    }

    private static StructureStart rigidSurfaceStart(
            List<PoolElementStructurePiece> pieces, TerrainAdjustment adjustment) {
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

    private static NativeStructureTerrainIntegrator.TerrainTarget surfaceTarget(
            StructureStart start) {
        return surfaceTarget(start, IrisStructureTerrainMode.SOURCE);
    }

    private static NativeStructureTerrainIntegrator.TerrainTarget surfaceTarget(
            StructureStart start, IrisStructureTerrainMode mode) {
        return new NativeStructureTerrainIntegrator.TerrainTarget(
                "nova_structures:tavern_oak", start,
                new IrisStructureTerrain().setMode(mode));
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

    private static NativeStructureTerrainIntegrator.OrganicCarve organicCarve(
            StructureStart start, int horizontalPadding) {
        return organicCarve(start, horizontalPadding, 0.85D);
    }

    private static NativeStructureTerrainIntegrator.OrganicCarve organicCarve(
            StructureStart start, int horizontalPadding, double lobeStrength) {
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setShape(IrisStructureCarveShape.ERODED)
                .setHorizontalPadding(horizontalPadding)
                .setCeilingPadding(8)
                .setFloorPadding(0)
                .setErosionStrength(1D)
                .setErosionFrequency(0.05D)
                .setLobeStrength(lobeStrength);
        return NativeStructureTerrainIntegrator.organicCarve(
                NativeStructureTerrainIntegrator.carveFootprint(start, horizontalPadding,
                        NativeStructurePostProcessorSurfaceTerrainTest::forbiddenTemplateManager),
                terrain, IrisStructureCarveShape.ERODED, TEST_SEED);
    }

    /**
     * Outward carve depth for every column along the straight +X edge of a slab footprint, measured
     * inside the source vertical span so the boundary is decided purely by the horizontal threshold.
     * The slab spans several lobe wavelengths so a lobed boundary is distinguishable from a uniform one.
     */
    private static int[] slabEdgeCarveDepths(double lobeStrength) {
        StructureCarvingFootprint footprint = StructureCarvingFootprint.fromColumns(sink -> {
            for (int x = 0; x < SLAB_WIDTH; x++) {
                for (int z = 0; z < SLAB_DEPTH; z++) {
                    sink.column(x, z, SLAB_MIN_Y, SLAB_MIN_Y + 8);
                }
            }
            return true;
        }, SLAB_PADDING, 1_000_000);
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setShape(IrisStructureCarveShape.ERODED)
                .setHorizontalPadding(SLAB_PADDING)
                .setCeilingPadding(8)
                .setFloorPadding(0)
                .setErosionStrength(1D)
                .setErosionFrequency(0.05D)
                .setLobeStrength(lobeStrength);
        int transectY = SLAB_MIN_Y + 4;
        BoundingBox area = new BoundingBox(
                SLAB_WIDTH, transectY, 0,
                SLAB_WIDTH - 1 + SLAB_PADDING, transectY, SLAB_DEPTH - 1);
        Map<BlockPos, BlockState> blocks = fill(area);

        NativeStructureTerrainIntegrator.carveOrganicColumns(world(blocks), area,
                NativeStructureTerrainIntegrator.organicCarve(
                        footprint, terrain, IrisStructureCarveShape.ERODED, TEST_SEED));

        int[] depths = new int[SLAB_DEPTH];
        for (int z = 0; z < SLAB_DEPTH; z++) {
            int depth = 0;
            while (depth < SLAB_PADDING
                    && state(blocks, SLAB_WIDTH + depth, transectY, z).isAir()) {
                depth++;
            }
            depths[z] = depth;
        }
        return depths;
    }

    private static int span(int[] values) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int value : values) {
            lowest = Math.min(lowest, value);
            highest = Math.max(highest, value);
        }
        return highest - lowest;
    }

    private static StructureTemplateManager forbiddenTemplateManager() {
        throw new AssertionError("Bounding-box carve columns must not resolve templates");
    }

    private static Map<BlockPos, BlockState> fill(BoundingBox area) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.minY(); y <= area.maxY(); y++) {
                    put(blocks, x, y, z, Blocks.STONE.defaultBlockState());
                }
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

    private static final class FlattenDesertPiece extends DesertPyramidPiece {
        private FlattenDesertPiece() {
            super(RandomSource.create(29L), 0, 0);
        }

        private boolean attemptVanillaAlignment() {
            return updateHeightPositionToLowestGroundHeight((LevelAccessor) null, -2);
        }
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
                return "surface-test-world";
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
    }

    private static WorldGenLevel world(ChunkAccess chunk) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            String methodName = method.getName();
            if (methodName.equals("getBlockState")) {
                return chunk.getBlockState((BlockPos) arguments[0]);
            }
            if (methodName.equals("setBlock")) {
                return chunk.setBlockState(
                        ((BlockPos) arguments[0]).immutable(),
                        (BlockState) arguments[1], (int) arguments[2]) != null;
            }
            if (methodName.equals("getSeed")) {
                return TEST_SEED;
            }
            if (methodName.equals("getHeight") && arguments != null && arguments.length == 3) {
                return chunk.getHeight((Heightmap.Types) arguments[0], (int) arguments[1], (int) arguments[2]) + 1;
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
                return "surface-test-chunk-world";
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
    }

    private static void write(ProtoChunk chunk, int x, int y, int z, BlockState state) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(x, y & 15, z, state, false);
    }

    private static PalettedContainerFactory containerFactory() {
        Strategy<BlockState> blockStates = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        BlockState air = Blocks.AIR.defaultBlockState();
        IdMapper<Holder<Biome>> biomeIds = new IdMapper<>();
        Holder<Biome> defaultBiome = Holder.direct((Biome) null);
        biomeIds.add(defaultBiome);
        Strategy<Holder<Biome>> biomes = Strategy.createForBiomes(biomeIds);
        Codec<Holder<Biome>> biomeCodec = Codec.STRING.xmap(
                name -> defaultBiome, holder -> "iris-test-biome");
        return new PalettedContainerFactory(
                blockStates,
                air,
                PalettedContainer.codecRW(BlockState.CODEC, blockStates, air),
                biomes,
                defaultBiome,
                PalettedContainer.codecRO(biomeCodec, biomes, defaultBiome),
                PalettedContainer.codecRW(biomeCodec, biomes, defaultBiome));
    }

    private static void put(Map<BlockPos, BlockState> blocks,
                            int x, int y, int z, BlockState state) {
        blocks.put(new BlockPos(x, y, z), state);
    }

    private static BlockState state(Map<BlockPos, BlockState> blocks, int x, int y, int z) {
        return blocks.getOrDefault(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
    }
}
