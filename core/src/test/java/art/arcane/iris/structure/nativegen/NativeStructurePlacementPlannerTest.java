package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.structure.placement.StructurePlacementGrid;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.placement.IrisStructureAnchorMode;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureTerrainMode;
import art.arcane.iris.structure.placement.StructureDistribution;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeStructurePlacementPlannerTest {
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
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
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
    public void submergedCheckUsesTheColumnRiverHead() {
        Engine engine = engine(77L, -64, 384, 66);
        IrisComplex complex = mock(IrisComplex.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> riverHead = mock(ProceduralStream.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getRiverWaterSurfaceStream()).thenReturn(riverHead);
        when(riverHead.get(8, 8)).thenReturn(68D);

        assertTrue(NativeStructurePlacementPlanner.isSubmerged(engine, 8, 8));
        verify(riverHead).get(8, 8);
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
