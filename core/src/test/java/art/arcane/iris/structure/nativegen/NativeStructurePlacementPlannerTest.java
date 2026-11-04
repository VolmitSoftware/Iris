package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.structure.placement.StructurePlacementGrid;

import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalAnswerException;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.bukkit.nms.BukkitStructureStagePolicy;
import art.arcane.iris.structure.placement.IrisStructureAnchorMode;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.volmlib.util.structure.StructureTerrainMode;
import art.arcane.iris.structure.placement.StructureDistribution;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWater;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWaterColumn;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWaterRules;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeStructurePlacementPlannerTest {
    private static final int BED = 140;
    private static final int HEAD = 143;
    private static final long LAND_IDENTITY = 7387932304999188754L;
    private static final List<StructureStagePolicy.Footprint> VILLAGE = List.of(
            new StructureStagePolicy.Footprint(0, 0, 15, 15),
            new StructureStagePolicy.Footprint(16, 0, 39, 15));

    @Test
    public void undergroundPlanIsDeterministicAndUsesConfiguredBand() {
        Engine engine = engine(982374L, -64, 384, 90);
        IrisStructurePlacement placement = nativePlacement()
                .setUnderground(true)
                .setMinHeight(-45)
                .setMaxHeight(-20);

        NativeStructureStartPlan first = NativeStructurePlacementPlanner.planAt(engine, placement, 12, -7);
        NativeStructureStartPlan second = NativeStructurePlacementPlanner.planAt(engine, placement, 12, -7);

        assertNotNull(first);
        assertEquals(first, second);
        assertEquals("minecraft:ancient_city", first.source().getStructure());
        assertEquals(12, first.chunkX());
        assertEquals(-7, first.chunkZ());
        assertTrue(first.baseY() >= -45 && first.baseY() <= -20);
    }

    @Test
    public void undergroundBandOutsideWorldBoundsSkipsTheCandidate() {
        Engine engine = engine(982374L, -64, 384, 90);
        IrisStructurePlacement placement = nativePlacement()
                .setUnderground(true)
                .setMinHeight(500)
                .setMaxHeight(600);

        assertNull(NativeStructurePlacementPlanner.planAt(engine, placement, 12, -7));
    }

    @Test
    public void surfacePlanUsesIrisTerrainAndHonorsHeightGate() {
        Engine engine = engine(77L, -64, 384, 150);
        IrisStructurePlacement placement = nativePlacement()
                .setMinHeight(80)
                .setMaxHeight(90);

        NativeStructureStartPlan plan = NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0);

        assertNotNull(plan);
        assertEquals(86, plan.baseY());

        placement.setMinHeight(87);
        assertNull(NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0));
    }

    @Test
    public void configuredDecisionOverridesSuppressedSourceWithoutLosingTerrain() {
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(StructureTerrainMode.FORCE_CARVE)
                .setHorizontalPadding(24);
        IrisStructurePlacement placement = nativePlacement()
                .setUnderground(true)
                .setTerrain(terrain);
        NativeStructureStartPlan plan = new NativeStructureStartPlan(
                placement, placement.getNativeStructures().getFirst(), 3, 5, -30);

        IrisNativeStructureDecision decision = NativeStructurePlacementPlanner.decisionFor(plan);

        assertEquals(NativeStructureGenerationStatus.GENERATE_NATIVE, decision.status());
        assertSame(terrain, decision.terrain());
    }

    @Test
    public void underwaterFlagRejectsSubmergedColumnsForSurfaceAndUndergroundPlans() {
        Engine engine = engine(77L, -64, 384, 63);
        IrisStructurePlacement placement = nativePlacement();

        assertNull(NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0));

        placement.setUnderground(true).setMinHeight(-40).setMaxHeight(-20);
        assertNull(NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0));

        placement.setUnderwater(true);
        assertNotNull(NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0));
    }

    @Test
    public void fluidHeightBoundaryIsShoreForPlannerAndLocator() {
        Engine engine = engine(77L, -64, 384, 64);
        IrisStructurePlacement placement = nativePlacement();

        assertTrue(!NativeStructurePlacementPlanner.isSubmerged(engine, 8, 8));
        assertNotNull(NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0));

        placement.setUnderground(true).setMinHeight(-40).setMaxHeight(-20);
        assertNotNull(NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0));
    }

    @Test
    public void submergedCheckUsesTheColumnWaterHead() {
        Engine engine = engine(77L, -64, 384, 66);
        IrisComplex complex = mock(IrisComplex.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> waterHead = mock(ProceduralStream.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getWaterHeadStream()).thenReturn(waterHead);
        when(waterHead.get(8, 8)).thenReturn(68D);

        assertTrue(NativeStructurePlacementPlanner.isSubmerged(engine, 8, 8));
        verify(waterHead).get(8, 8);
    }

    @Test
    public void structuresAvoidRivers() {
        Engine engine = engine(77L, -64, 384, 150);
        Map<Long, WaterRole> roles = new HashMap<>();
        waterComplex(engine, roles, false);
        WaterRole[] water = {WaterRole.CHANNEL, WaterRole.POND, WaterRole.CASCADE, WaterRole.WATERFALL};
        for (int x = 36; x <= 39; x++) {
            for (int z = 0; z < 16; z++) {
                roles.put(columnKey(x, z), water[(x + z) & 3]);
            }
        }
        IrisStructurePlacement placement = nativePlacement();
        NativeStructureStartPlan plan = NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0);
        StructureWater stageWater = new BukkitStructureStagePolicy(engine).water(engine);

        assertNotNull(plan);
        assertTrue("64 of 640 piece columns, all beyond the start chunk, are 10% river",
                StructureWaterRules.admitsStart(plan, stageWater, VILLAGE));

        roles.put(columnKey(35, 15), WaterRole.CHANNEL);
        assertFalse("65 of 640 piece columns are more than 10% river",
                StructureWaterRules.admitsStart(plan, stageWater, VILLAGE));

        roles.clear();
        WaterRole[] dry = {WaterRole.MOUTH, WaterRole.SHORE, WaterRole.POND_SHORE, WaterRole.BANK, WaterRole.DAM};
        int column = 0;
        for (int x = 0; x <= 39; x++) {
            for (int z = 0; z < 16; z++) {
                roles.put(columnKey(x, z), dry[column++ % dry.length]);
            }
        }
        assertTrue("mouths, shores, banks and dams do not count as river water",
                StructureWaterRules.admitsStart(plan, stageWater, VILLAGE));

        roles.clear();
        for (int x = 0; x <= 39; x++) {
            for (int z = 0; z < 16; z++) {
                roles.put(columnKey(x, z), x % 2 == 0 ? WaterRole.INLET : WaterRole.BANK);
            }
        }
        assertFalse("a sea inlet under the pieces counts as river water", StructureWaterRules.admitsStart(plan, stageWater, VILLAGE));
    }

    @Test
    public void riverInTheStartChunkLeavesTheDecisionToThePieces() {
        Engine engine = engine(77L, -64, 384, 150);
        Map<Long, WaterRole> roles = new HashMap<>();
        waterComplex(engine, roles, false);
        flood(roles);

        assertNotNull(NativeStructurePlacementPlanner.planAt(engine, nativePlacement(), 0, 0));
    }

    @Test
    public void stageWaterReportsWorldHeights() {
        Engine engine = engine(77L, -64, 384, 150);
        Map<Long, WaterRole> roles = new HashMap<>();
        waterComplex(engine, roles, false);
        roles.put(columnKey(3, 4), WaterRole.MOUTH);
        roles.put(columnKey(5, 4), WaterRole.POND);
        StructureWater stageWater = new BukkitStructureStagePolicy(engine).water(engine);

        assertEquals(new StructureWaterColumn(true, false, BED - 64, HEAD - 64), stageWater.column(3, 4));
        assertEquals(new StructureWaterColumn(true, true, BED - 64, HEAD - 64), stageWater.column(5, 4));
        assertEquals(StructureWaterColumn.DRY, stageWater.column(6, 4));
    }

    @Test
    public void undergroundPlansIgnoreRiversAbove() {
        Engine engine = engine(982374L, -64, 384, 90);
        Map<Long, WaterRole> roles = new HashMap<>();
        waterComplex(engine, roles, false);
        flood(roles);
        IrisStructurePlacement placement = nativePlacement()
                .setUnderground(true)
                .setMinHeight(-45)
                .setMaxHeight(-20);
        NativeStructureStartPlan plan = NativeStructurePlacementPlanner.planAt(engine, placement, 12, -7);

        assertNotNull(plan);
        assertTrue(StructureWaterRules.admitsStart(plan, new BukkitStructureStagePolicy(engine).water(engine), VILLAGE));
    }

    @Test
    public void allowWaterLetsNativeStructuresCoverRivers() {
        Engine engine = engine(77L, -64, 384, 150);
        Map<Long, WaterRole> roles = new HashMap<>();
        waterComplex(engine, roles, false);
        flood(roles);
        StructureWater stageWater = new BukkitStructureStagePolicy(engine).water(engine);
        IrisStructurePlacement placement = nativePlacement();

        assertFalse(StructureWaterRules.admitsStart(
                NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0), stageWater, VILLAGE));

        placement.setAllowWater(true);
        NativeStructureStartPlan plan = NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0);
        assertTrue(plan.allowWater());
        assertTrue(StructureWaterRules.admitsStart(plan, stageWater, VILLAGE));
    }

    @Test
    public void provisionalWaterAnswersNeverPlaceANativeStructure() {
        Engine engine = engine(77L, -64, 384, 150);
        IrisComplex complex = waterComplex(engine, new HashMap<>(), true);
        StructureWater stageWater = new BukkitStructureStagePolicy(engine).water(engine);

        ProvisionalAnswerException failure = assertThrows(ProvisionalAnswerException.class,
                () -> stageWater.column(20, 3));
        assertTrue(failure.getMessage().contains("20,3"));

        when(complex.isPlatformMainThread()).thenReturn(true);
        assertEquals("the server thread answers from what it has, as for heights",
                StructureWaterColumn.DRY, stageWater.column(20, 3));
    }

    @Test
    public void allowWaterSeparatesOtherwiseIdenticalPlacements() {
        IrisStructurePlacement land = nativePlacement();
        IrisStructurePlacement water = nativePlacement().setAllowWater(true);

        assertEquals("placements that keep out of water keep their identity",
                LAND_IDENTITY, StructurePlacementGrid.placementIdentity(land));
        assertNotEquals(StructurePlacementGrid.placementIdentity(land), StructurePlacementGrid.placementIdentity(water));
        assertEquals("a placementId stays the whole identity",
                StructurePlacementGrid.placementIdentity(nativePlacement().setPlacementId("hut")),
                StructurePlacementGrid.placementIdentity(nativePlacement().setPlacementId("hut").setAllowWater(true)));

        Engine engine = engine(77L, -64, 384, 150);
        bindPlacements(engine, land, water);
        IrisStructurePlacement first = NativeStructurePlacementPlanner.plansAt(engine, 0, 0).getFirst().placement();
        bindPlacements(engine, water, land);
        assertSame("the deduplicated start does not depend on list order",
                first, NativeStructurePlacementPlanner.plansAt(engine, 0, 0).getFirst().placement());
    }

    @Test
    public void duplicateSelectedStructureUsesOneDeterministicPlan() {
        Engine engine = engine(77L, -64, 384, 150);
        IrisStructurePlacement first = nativePlacement().setPlacementId("first");
        IrisStructurePlacement second = nativePlacement().setPlacementId("second");
        bindPlacements(engine, first, second);

        KList<NativeStructureStartPlan> plans = NativeStructurePlacementPlanner.plansAt(engine, 0, 0);

        IrisStructurePlacement expected = Long.compareUnsigned(
                StructurePlacementGrid.placementIdentity(first),
                StructurePlacementGrid.placementIdentity(second)) <= 0 ? first : second;
        assertEquals(1, plans.size());
        assertSame(expected, plans.getFirst().placement());
    }

    @Test
    public void transitionBandRejectsNativeStructureStarts() {
        Engine engine = engine(77L, -64, 384, 150);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.allowsNewGenerationChunk(0, 0)).thenReturn(false);
        IrisStructurePlacement placement = nativePlacement();

        assertNull(NativeStructurePlacementPlanner.planAt(engine, placement, 0, 0));
        assertTrue(NativeStructurePlacementPlanner.plansAt(engine, 0, 0).isEmpty());
    }

    @Test
    public void weightedNativeSelectionPreservesAuthoredSourceOrder() {
        KList<IrisNativeStructure> first = new KList<>();
        first.add(new IrisNativeStructure().setStructure("test:a").setWeight(1));
        first.add(new IrisNativeStructure().setStructure("test:b").setWeight(3));
        KList<IrisNativeStructure> reordered = new KList<>();
        reordered.add(first.get(1));
        reordered.add(first.get(0));

        RNG selectsFirstWeightSlot = new RNG(0L) {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        assertEquals("test:a", NativeStructurePlacementPlanner
                .selectSource(first, selectsFirstWeightSlot).getStructure());
        assertEquals("test:b", NativeStructurePlacementPlanner
                .selectSource(reordered, selectsFirstWeightSlot).getStructure());
    }

    @Test
    public void placementMustChooseExactlyOneBackend() {
        assertInvalid(new IrisStructurePlacement());
        IrisStructurePlacement mixed = nativePlacement();
        mixed.getStructures().add("legacy");
        assertInvalid(mixed);
    }

    @Test
    public void nativeBackendRejectsEveryExplicitIrisAnchorMode() {
        assertUnsupportedAnchor(nativePlacement().setAnchor(IrisStructureAnchorMode.SURFACE));
        assertUnsupportedAnchor(nativePlacement().setAnchor(IrisStructureAnchorMode.HEIGHT_BAND));
        assertUnsupportedAnchor(nativePlacement().setAnchor(IrisStructureAnchorMode.CAVE_FLOOR));
    }

    private IrisStructurePlacement nativePlacement() {
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setDistribution(StructureDistribution.DENSITY)
                .setDensity(1D);
        placement.getNativeStructures().add(new IrisNativeStructure()
                .setStructure("minecraft:ancient_city")
                .setWeight(1));
        return placement;
    }

    private Engine engine(long seed, int minHeight, int height, int terrainHeight) {
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(new SeedManager(seed));
        when(engine.getMinHeight()).thenReturn(minHeight);
        when(engine.getHeight()).thenReturn(height);
        when(engine.getHeight(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(terrainHeight);
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getFluidHeight()).thenReturn(64);
        when(engine.getDimension()).thenReturn(dimension);
        return engine;
    }

    private IrisComplex waterComplex(Engine engine, Map<Long, WaterRole> roles, boolean provisional) {
        IrisComplex complex = mock(IrisComplex.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> heads = mock(ProceduralStream.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<WaterRole> roleStream = mock(ProceduralStream.class);
        when(heads.get(anyDouble(), anyDouble())).thenReturn(0D);
        when(heads.getDouble(anyDouble(), anyDouble())).thenReturn((double) HEAD);
        @SuppressWarnings("unchecked")
        ProceduralStream<Integer> heights = mock(ProceduralStream.class);
        when(heights.get(anyDouble(), anyDouble())).thenReturn(BED);
        when(complex.getRoundedHeighteightStream()).thenReturn(heights);
        when(roleStream.get(anyDouble(), anyDouble())).thenAnswer(call -> {
            if (provisional) {
                ProvisionalSampling.mark();
            }
            return roles.getOrDefault(columnKey(block(call.getArgument(0)), block(call.getArgument(1))), WaterRole.NONE);
        });
        when(complex.getWaterHeadStream()).thenReturn(heads);
        when(complex.getWaterRoleStream()).thenReturn(roleStream);
        when(complex.allowsNewGenerationChunk(anyInt(), anyInt())).thenReturn(true);
        when(engine.getComplex()).thenReturn(complex);
        return complex;
    }

    private static void flood(Map<Long, WaterRole> roles) {
        for (int x = 0; x <= 39; x++) {
            for (int z = 0; z < 16; z++) {
                roles.put(columnKey(x, z), WaterRole.CHANNEL);
            }
        }
    }

    private static int block(Object coordinate) {
        return (int) Math.floor(((Number) coordinate).doubleValue());
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private void bindPlacements(Engine engine, IrisStructurePlacement... placements) {
        IrisDimension dimension = engine.getDimension();
        KList<IrisStructurePlacement> configured = new KList<>();
        for (IrisStructurePlacement placement : placements) {
            configured.add(placement);
        }
        when(dimension.getStructures()).thenReturn(configured);
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
    }

    private void assertInvalid(IrisStructurePlacement placement) {
        try {
            NativeStructurePlacementPlanner.validateBackend(placement);
            fail("Expected an invalid structure backend");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("exactly one backend"));
        }
    }

    private void assertUnsupportedAnchor(IrisStructurePlacement placement) {
        try {
            NativeStructurePlacementPlanner.validateBackend(placement);
            fail("Expected native structure anchor rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("do not support the Iris anchor field"));
        }
    }
}
