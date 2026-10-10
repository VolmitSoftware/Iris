package art.arcane.iris.engine.framework.structure.fit;

import art.arcane.iris.builtin.structure.StructureCarveShape;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import art.arcane.iris.engine.framework.structure.NativeStructureFit;
import art.arcane.iris.engine.mantle.components.StructureCarvingFootprint;
import com.google.gson.Gson;
import com.volmit.block.BlockState;
import com.volmit.vanilla.PieceKind;
import com.volmit.vanilla.StartMemo;
import com.volmit.vanilla.StructureStep;
import com.volmit.vanilla.TerrainAdaptation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static art.arcane.iris.engine.framework.structure.fit.FitFixture.AIR;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.DIRT;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.OAK_LOG;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.STONE;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.WATER;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.state;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The terrain integration of every mode but VACUUM, on in-memory terrain. */
public class TerrainIntegrationTest {
    private static final long SEED = 1234L;
    private static final long CARVE_SEED = 8675309L;
    private static final int SLAB_DEPTH = 128;
    private static final int SLAB_MIN_Y = 64;
    private static final int SLAB_PADDING = 14;
    private static final int SLAB_WIDTH = 16;
    private static final Gson JSON = new Gson();
    private static final IntFunction<BlockState> NATURAL = y -> y < 0 ? state("deepslate") : STONE;
    private static final FitPalettes NO_PALETTES = (palette, seed, x, y, z) -> {
        throw new AssertionError("no palette expected");
    };
    /** The box of a desert pyramid piece at y 64: 21 wide, 15 high. */
    private static final Box PYRAMID = new Box(0, 64, 0, 20, 78, 20);

    @Test
    public void encaseFillsAirAndLiquidWithoutOverwritingExistingTerrain() {
        Box bounds = PYRAMID;
        Box area = new Box(bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1,
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
        FitFixture.World world = new FitFixture.World(area);
        world.put(bounds.minX(), bounds.minY(), bounds.minZ(), DIRT);
        world.put(bounds.maxX(), bounds.minY(), bounds.maxZ(), WATER);

        integrate(world, area, start("{\"mode\":\"ENCASE\",\"horizontalPadding\":1,\"ceilingPadding\":1,\"floorPadding\":1}",
                TerrainAdaptation.BURY, FitFixture.Piece.fixed(bounds)));

        assertEquals(DIRT, world.at(bounds.minX(), bounds.minY(), bounds.minZ()));
        assertEquals(STONE, world.at(bounds.maxX(), bounds.minY(), bounds.maxZ()));
        assertEquals(STONE, world.at(bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1));
        assertEquals(STONE, world.at(bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1));
    }

    @Test
    public void defaultEncaseUsesTheDimensionsNaturalBlockPerHeight() {
        Box bounds = new Box(0, -1, 0, 20, 13, 20);
        FitFixture.World world = new FitFixture.World(bounds);

        integrate(world, bounds, start("{\"mode\":\"ENCASE\"}", TerrainAdaptation.BURY, FitFixture.Piece.fixed(bounds)));

        assertEquals(state("deepslate"), world.at(0, -1, 0));
        assertEquals(STONE, world.at(0, 0, 0));
    }

    @Test
    public void configuredEncasePaletteReplacesTheDefaultMaterialSeededPerStructure() {
        FitFixture.World world = new FitFixture.World(PYRAMID);
        List<Long> seeds = new ArrayList<>();
        FitPalettes tuff = (palette, seed, x, y, z) -> {
            seeds.add(seed);
            return state("tuff");
        };
        FitStart start = start("{\"mode\":\"ENCASE\",\"encasePalette\":{\"palette\":[{\"block\":\"tuff\"}]}}",
                TerrainAdaptation.BURY, FitFixture.Piece.fixed(PYRAMID));

        TerrainIntegration.integrateTerrain(world.fitBlocks(), PYRAMID, start, SEED, NATURAL, tuff, null);

        assertEquals(state("tuff"), world.at(0, 64, 0));
        assertEquals(SEED ^ "test:structure".hashCode(), (long) seeds.getFirst());
    }

    @Test
    public void sourceMaterialFollowsNearbyTerrainAndIgnoresBlocksOutsideTheSnapshot() {
        Box area = new Box(0, 64, 0, 15, 64, 15);
        FitFixture.World world = new FitFixture.World(area);
        world.put(-1, 64, 8, state("oak_planks"));
        world.put(3, 64, 8, state("tuff"));
        TerrainIntegration.SourceTerrainSnapshot snapshot = snapshot(world, area);
        world.put(1, 64, 8, state("oak_planks"));

        assertEquals(state("tuff"), TerrainIntegration.sourceEncaseBlock(0, 64, 8, NATURAL, snapshot));
    }

    @Test
    public void sourceMaterialRejectsStructureBlocksAndOresBeforeTheNaturalFallback() {
        Box area = new Box(0, -20, 0, 15, -20, 15);
        FitFixture.World world = new FitFixture.World(area);
        world.put(3, -20, 8, state("oak_planks"));
        world.put(5, -20, 8, state("stone_bricks"));
        world.put(4, -20, 7, state("iron_ore"));

        assertEquals(state("deepslate"), TerrainIntegration.sourceEncaseBlock(4, -20, 8, NATURAL, snapshot(world, area)));
    }

    @Test
    public void sourceMaterialSnapshotOnlyAllocatesActualFillLayers() {
        Box area = new Box(0, -2032, 0, 15, 2030, 15);
        FitFixture.World world = new FitFixture.World(area);

        TerrainIntegration.SourceTerrainSnapshot snapshot = TerrainIntegration.captureSourceTerrain(world.fitBlocks(), area,
                List.of(start("{\"mode\":\"SOURCE\"}", TerrainAdaptation.BURY, FitFixture.Piece.fixed(PYRAMID))));

        assertNotNull(snapshot);
        assertEquals(23 * 16 * 16, snapshot.sampledCells());
    }

    @Test
    public void sourceBuryUsesTheVanillaSixByTwelveInfluenceEnvelope() {
        int groundY = PYRAMID.minY();
        Box area = new Box(PYRAMID.minX() - 7, groundY - 13, PYRAMID.minZ(), PYRAMID.maxX(), groundY + 13, PYRAMID.maxZ());
        FitFixture.World world = new FitFixture.World(area);

        integrate(world, area, start("{\"mode\":\"SOURCE\"}", TerrainAdaptation.BURY, FitFixture.Piece.fixed(PYRAMID)));

        assertEquals(STONE, world.at(PYRAMID.minX() - 5, groundY, PYRAMID.minZ()));
        assertEquals(STONE, world.at(PYRAMID.minX(), groundY + 11, PYRAMID.minZ()));
        assertEquals(AIR, world.at(PYRAMID.minX() - 6, groundY, PYRAMID.minZ()));
        assertEquals(AIR, world.at(PYRAMID.minX(), groundY + 12, PYRAMID.minZ()));
        assertEquals(AIR, world.at(PYRAMID.minX() - 7, groundY, PYRAMID.minZ()));
    }

    @Test
    public void sourceEncapsulateWrapsTheFullPieceBounds() {
        Box area = new Box(PYRAMID.minX() - 13, PYRAMID.minY(), PYRAMID.minZ(), PYRAMID.maxX(), PYRAMID.maxY() + 13, PYRAMID.maxZ());
        FitFixture.World world = new FitFixture.World(area);

        integrate(world, area, start("{\"mode\":\"SOURCE\"}", TerrainAdaptation.ENCAPSULATE, FitFixture.Piece.fixed(PYRAMID)));

        assertEquals(STONE, world.at(PYRAMID.minX() - 11, PYRAMID.minY(), PYRAMID.minZ()));
        assertEquals(STONE, world.at(PYRAMID.minX(), PYRAMID.maxY() + 11, PYRAMID.minZ()));
        assertEquals(AIR, world.at(PYRAMID.minX() - 12, PYRAMID.minY(), PYRAMID.minZ()));
        assertEquals(AIR, world.at(PYRAMID.minX(), PYRAMID.maxY() + 12, PYRAMID.minZ()));
    }

    @Test
    public void sourceTerrainDensityOnlyUsesRigidAndFixedPieces() {
        assertFalse(new FitPiece(new FitFixture.Piece(PYRAMID, PieceKind.TERRAIN_MATCHING, 0), FitFixture.BOTTOM_Y).isRigidOrFixed());
        assertTrue(new FitPiece(new FitFixture.Piece(PYRAMID, PieceKind.RIGID, 0), FitFixture.BOTTOM_Y).isRigidOrFixed());
        assertTrue(new FitPiece(new FitFixture.Piece(PYRAMID, PieceKind.FIXED, 0), FitFixture.BOTTOM_Y).isRigidOrFixed());
    }

    @Test
    public void sourceEnvelopesAreTheVanillaOnes() {
        assertTrue(TerrainIntegration.insideSourceJunctionEnvelope(0, -1, 0));
        assertTrue(TerrainIntegration.insideSourceJunctionEnvelope(0, -11, 0));
        assertFalse(TerrainIntegration.insideSourceJunctionEnvelope(0, 0, 0));
        assertFalse(TerrainIntegration.insideSourceJunctionEnvelope(0, -12, 0));
        assertFalse(TerrainIntegration.insideSourceJunctionEnvelope(12, -1, 0));
        assertTrue(TerrainIntegration.insideBurialEnvelope(5, 0, 0));
        assertFalse(TerrainIntegration.insideBurialEnvelope(6, 0, 0));
        assertTrue(TerrainIntegration.insideEncapsulationEnvelope(11, 0, 0));
        assertFalse(TerrainIntegration.insideEncapsulationEnvelope(12, 0, 0));
    }

    @Test
    public void omittedTerrainRetainsSourceSemanticsAndAnExplicitTerrainWins() {
        assertEquals(StructureTerrainMode.SOURCE, NativeStructureFit.NONE.resolvedTerrain().resolvedMode());
        assertEquals(StructureTerrainMode.PRESERVE,
                NativeStructureFit.of(new StartMemo("{\"mode\":\"PRESERVE\"}", "null")).resolvedTerrain().resolvedMode());
    }

    @Test
    public void legacyTemplateAirIsClearedAfterEveryTerrainFillPath() {
        FitFixture.Piece piece = FitFixture.Piece.fixed(PYRAMID);

        assertTrue(TerrainIntegration.clearsLegacyTemplateAir(start("{\"mode\":\"SOURCE\"}", TerrainAdaptation.BURY, piece)));
        assertTrue(TerrainIntegration.clearsLegacyTemplateAir(start("{\"mode\":\"SOURCE\"}", TerrainAdaptation.BEARD_BOX, piece)));
        assertTrue(TerrainIntegration.clearsLegacyTemplateAir(start("{\"mode\":\"ENCASE\"}", TerrainAdaptation.NONE, piece)));
        assertFalse(TerrainIntegration.clearsLegacyTemplateAir(start("{\"mode\":\"SOURCE\"}", TerrainAdaptation.NONE, piece)));
        assertFalse(TerrainIntegration.clearsLegacyTemplateAir(start("{\"mode\":\"PRESERVE\"}", TerrainAdaptation.BURY, piece)));
        assertFalse(TerrainIntegration.shouldClearLegacyAir(79, 80, false));
        assertTrue(TerrainIntegration.shouldClearLegacyAir(80, 80, false));
        assertTrue(TerrainIntegration.shouldClearLegacyAir(96, 80, false));
        assertFalse(TerrainIntegration.shouldClearLegacyAir(96, 80, true));
    }

    @Test
    public void legacyTemplateAirClearsAboveTheGroundAndSparesTrees() {
        Box area = new Box(0, 70, 0, 15, 90, 15);
        FitFixture.World world = new FitFixture.World(area);
        world.put(1, 80, 0, DIRT);
        world.put(2, 79, 0, DIRT);
        world.put(3, 80, 0, OAK_LOG);
        FitFixture.Piece piece = FitFixture.Piece.rigid(new Box(0, 79, 0, 4, 85, 0), 1)
                .legacyAir(1, 80, 0).legacyAir(2, 79, 0).legacyAir(3, 80, 0);

        TerrainIntegration.clearLegacyTemplateAir(world.fitBlocks(), area,
                start("{\"mode\":\"SOURCE\"}", TerrainAdaptation.BEARD_THIN, piece));

        assertEquals(AIR, world.at(1, 80, 0));
        assertEquals(DIRT, world.at(2, 79, 0));
        assertEquals(OAK_LOG, world.at(3, 80, 0));
    }

    @Test
    public void vacuumLeavesPieceBlocksForTheSurfaceFitting() {
        FitFixture.World world = new FitFixture.World(PYRAMID);
        world.put(PYRAMID.minX(), PYRAMID.minY(), PYRAMID.minZ(), STONE);

        integrate(world, PYRAMID, start("{\"mode\":\"VACUUM\"}", TerrainAdaptation.NONE, FitFixture.Piece.fixed(PYRAMID)));

        assertEquals(STONE, world.at(PYRAMID.minX(), PYRAMID.minY(), PYRAMID.minZ()));
    }

    @Test
    public void forceCarveHonorsConfiguredPadding() {
        Box area = new Box(PYRAMID.minX() - 1, PYRAMID.minY(), PYRAMID.minZ() - 1,
                PYRAMID.maxX() + 1, PYRAMID.maxY() + 1, PYRAMID.maxZ() + 1);
        FitFixture.World world = new FitFixture.World(area);
        world.put(PYRAMID.maxX() + 1, PYRAMID.minY(), PYRAMID.maxZ(), STONE);
        world.put(PYRAMID.maxX(), PYRAMID.maxY() + 1, PYRAMID.maxZ(), STONE);

        integrate(world, area, start("{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":1,\"ceilingPadding\":1}",
                TerrainAdaptation.NONE, FitFixture.Piece.fixed(PYRAMID)));

        assertEquals(AIR, world.at(PYRAMID.maxX() + 1, PYRAMID.minY(), PYRAMID.maxZ()));
        assertEquals(AIR, world.at(PYRAMID.maxX(), PYRAMID.maxY() + 1, PYRAMID.maxZ()));
    }

    @Test
    public void forceCarveUsesThePieceUnionInsteadOfTheCombinedBounds() {
        Box second = new Box(64, 64, 0, 84, 78, 20);
        int gapX = (PYRAMID.maxX() + second.minX()) / 2;
        Box area = new Box(PYRAMID.minX(), 64, 0, second.maxX(), 64, 0);
        FitFixture.World world = new FitFixture.World(area);
        world.put(PYRAMID.minX(), 64, 0, STONE);
        world.put(gapX, 64, 0, STONE);

        integrate(world, area, start("{\"mode\":\"FORCE_CARVE\"}", TerrainAdaptation.NONE,
                FitFixture.Piece.fixed(PYRAMID), FitFixture.Piece.fixed(second)));

        assertEquals(AIR, world.at(PYRAMID.minX(), 64, 0));
        assertEquals(STONE, world.at(gapX, 64, 0));
    }

    @Test
    public void carveFootprintIsComputedOncePerStartAndPadding() {
        FitStart start = start("{\"mode\":\"FORCE_CARVE\"}", TerrainAdaptation.NONE, FitFixture.Piece.fixed(PYRAMID));

        StructureCarvingFootprint first = CarveFootprints.of(start, 6);

        assertSame(first, CarveFootprints.of(start, 6));
        assertNotSame(first, CarveFootprints.of(start, 7));
        assertNotSame(first, CarveFootprints.of(
                start("{\"mode\":\"FORCE_CARVE\"}", TerrainAdaptation.NONE, FitFixture.Piece.fixed(PYRAMID)), 6));
        assertEquals(PYRAMID.minX() - 6, first.minX());
        assertEquals(0L, first.distanceSquaredAt(PYRAMID.minX(), PYRAMID.minZ()));
        assertEquals(PYRAMID.minY(), first.sourceMinYAt(PYRAMID.minX(), PYRAMID.minZ()));
        assertEquals(PYRAMID.maxY(), first.sourceMaxYAt(PYRAMID.minX(), PYRAMID.minZ()));
    }

    @Test
    public void concurrentCarveRequestsShareOneFootprintBuild() throws Exception {
        FitStart start = start("{\"mode\":\"FORCE_CARVE\"}", TerrainAdaptation.NONE, FitFixture.Piece.fixed(PYRAMID));
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
                    return CarveFootprints.of(start, 11);
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
            CarveFootprints.of(start("{\"mode\":\"FORCE_CARVE\"}", TerrainAdaptation.NONE, FitFixture.Piece.fixed(PYRAMID)), 128);
        }

        assertTrue(CarveFootprints.cachedCells() <= CarveFootprints.maximumCachedCells());
    }

    @Test
    public void organicCarveNeverCutsBelowTheColumnSupportingFloor() {
        Box area = new Box(PYRAMID.minX() - 6, PYRAMID.minY() - 4, PYRAMID.minZ() - 6,
                PYRAMID.maxX() + 6, PYRAMID.maxY() + 12, PYRAMID.maxZ() + 6);
        FitFixture.World world = filled(area);

        TerrainIntegration.carveOrganicColumns(world.fitBlocks(), area, organicCarve(6, 0.85D));

        assertEquals(AIR, world.at(PYRAMID.minX() + 10, PYRAMID.minY(), PYRAMID.minZ() + 10));
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.minY(); y < PYRAMID.minY(); y++) {
                    assertEquals(STONE, world.at(x, y, z));
                }
            }
        }
    }

    @Test
    public void lobedCarveStaysInsideTheUniformCarveAndRemovesLess() {
        Box area = new Box(PYRAMID.minX() - 10, PYRAMID.minY(), PYRAMID.minZ() - 10,
                PYRAMID.maxX() + 10, PYRAMID.maxY() + 12, PYRAMID.maxZ() + 10);
        FitFixture.World uniform = filled(area);
        FitFixture.World lobed = filled(area);

        TerrainIntegration.carveOrganicColumns(uniform.fitBlocks(), area, organicCarve(10, 0D));
        TerrainIntegration.carveOrganicColumns(lobed.fitBlocks(), area, organicCarve(10, 0.85D));

        int uniformAir = 0;
        int lobedAir = 0;
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.minY(); y <= area.maxY(); y++) {
                    boolean uniformCarved = uniform.at(x, y, z).equals(AIR);
                    boolean lobedCarved = lobed.at(x, y, z).equals(AIR);
                    assertTrue("lobe carved outside the uniform padding at " + x + "," + y + "," + z,
                            uniformCarved || !lobedCarved);
                    uniformAir += uniformCarved ? 1 : 0;
                    lobedAir += lobedCarved ? 1 : 0;
                }
            }
        }
        assertTrue(lobedAir > 0);
        assertTrue("uniform " + uniformAir + " lobed " + lobedAir, lobedAir < uniformAir);
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
        Box wide = new Box(PYRAMID.minX() - 6, PYRAMID.minY(), PYRAMID.minZ() - 6,
                PYRAMID.maxX() + 6, PYRAMID.maxY() + 12, PYRAMID.maxZ() + 6);
        Box narrow = new Box(PYRAMID.maxX() - 3, PYRAMID.minY(), PYRAMID.maxZ() - 3,
                PYRAMID.maxX() + 6, PYRAMID.maxY() + 12, PYRAMID.maxZ() + 6);
        FitFixture.World wideWorld = filled(wide);
        FitFixture.World narrowWorld = filled(narrow);

        TerrainIntegration.carveOrganicColumns(wideWorld.fitBlocks(), wide, organicCarve(6, 0.85D));
        TerrainIntegration.carveOrganicColumns(narrowWorld.fitBlocks(), narrow, organicCarve(6, 0.85D));

        int carved = 0;
        for (int x = narrow.minX(); x <= narrow.maxX(); x++) {
            for (int z = narrow.minZ(); z <= narrow.maxZ(); z++) {
                for (int y = narrow.minY(); y <= narrow.maxY(); y++) {
                    BlockState expected = wideWorld.at(x, y, z);
                    assertEquals(expected, narrowWorld.at(x, y, z));
                    carved += expected.equals(AIR) ? 1 : 0;
                }
            }
        }
        assertTrue(carved > 0);
    }

    @Test
    public void erodedForceCarveShrinkwrapsWithoutUnderminingTheFloor() {
        Box area = new Box(PYRAMID.minX() - 6, PYRAMID.minY() - 2, PYRAMID.minZ() - 6,
                PYRAMID.maxX() + 6, PYRAMID.maxY() + 12, PYRAMID.maxZ() + 6);
        FitFixture.World world = filled(area);
        int centerX = PYRAMID.minX() + PYRAMID.xSpan() / 2;
        int centerZ = PYRAMID.minZ() + PYRAMID.zSpan() / 2;

        integrate(world, area, start("{\"mode\":\"FORCE_CARVE\",\"shape\":\"ERODED\",\"horizontalPadding\":6,"
                + "\"ceilingPadding\":8,\"floorPadding\":0,\"erosionStrength\":1.0,\"erosionFrequency\":0.05}",
                TerrainAdaptation.NONE, FitFixture.Piece.fixed(PYRAMID)));

        assertEquals(AIR, world.at(centerX, PYRAMID.minY(), centerZ));
        assertEquals(STONE, world.at(centerX, PYRAMID.minY() - 1, centerZ));
        assertEquals(STONE, world.at(area.minX(), area.maxY(), area.minZ()));
    }

    private static void integrate(FitFixture.World world, Box area, FitStart start) {
        TerrainIntegration.prepareTerrain(world.fitBlocks(), area, List.of(start), SEED, NATURAL, NO_PALETTES);
    }

    private static FitStart start(String terrainJson, TerrainAdaptation adaptation, FitFixture.Piece... pieces) {
        return FitFixture.start(terrainJson, adaptation, StructureStep.STRONGHOLDS, pieces);
    }

    private static TerrainIntegration.SourceTerrainSnapshot snapshot(FitFixture.World world, Box area) {
        BitSet layers = new BitSet(area.ySpan());
        layers.set(0, area.ySpan());
        return TerrainIntegration.SourceTerrainSnapshot.capture(world.fitBlocks(), area, layers);
    }

    private static FitFixture.World filled(Box area) {
        FitFixture.World world = new FitFixture.World(area);
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.minY(); y <= area.maxY(); y++) {
                    world.put(x, y, z, STONE);
                }
            }
        }
        return world;
    }

    private static TerrainIntegration.OrganicCarve organicCarve(int horizontalPadding, double lobeStrength) {
        StructureTerrain terrain = erodedTerrain(horizontalPadding, lobeStrength);
        FitStart start = start(JSON.toJson(terrain), TerrainAdaptation.NONE, FitFixture.Piece.fixed(PYRAMID));
        return TerrainIntegration.organicCarve(CarveFootprints.of(start, horizontalPadding), terrain,
                StructureCarveShape.ERODED, CARVE_SEED);
    }

    private static StructureTerrain erodedTerrain(int horizontalPadding, double lobeStrength) {
        return JSON.fromJson("{\"mode\":\"FORCE_CARVE\",\"shape\":\"ERODED\",\"horizontalPadding\":" + horizontalPadding
                + ",\"ceilingPadding\":8,\"floorPadding\":0,\"erosionStrength\":1.0,\"erosionFrequency\":0.05,"
                + "\"lobeStrength\":" + lobeStrength + "}", StructureTerrain.class);
    }

    /**
     * Outward carve depth for every column along the straight +X edge of a slab footprint, measured inside the source
     * vertical span so the boundary is decided purely by the horizontal threshold.
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
        int transectY = SLAB_MIN_Y + 4;
        Box area = new Box(SLAB_WIDTH, transectY, 0, SLAB_WIDTH - 1 + SLAB_PADDING, transectY, SLAB_DEPTH - 1);
        FitFixture.World world = filled(area);

        TerrainIntegration.carveOrganicColumns(world.fitBlocks(), area, TerrainIntegration.organicCarve(
                footprint, erodedTerrain(SLAB_PADDING, lobeStrength), StructureCarveShape.ERODED, CARVE_SEED));

        int[] depths = new int[SLAB_DEPTH];
        for (int z = 0; z < SLAB_DEPTH; z++) {
            int depth = 0;
            while (depth < SLAB_PADDING && world.at(SLAB_WIDTH + depth, transectY, z).equals(AIR)) {
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
}
