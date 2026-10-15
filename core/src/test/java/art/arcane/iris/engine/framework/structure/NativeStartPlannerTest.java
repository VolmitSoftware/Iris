package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.framework.StructurePlacementGrid;
import art.arcane.iris.builtin.structure.StructureStilts;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import com.google.gson.Gson;
import com.volmit.vanilla.JigsawOverrides;
import art.arcane.iris.builtin.structure.NativeStructureSuppression;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.VanillaStructureStart;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;
import art.arcane.iris.engine.object.TestSettings;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.placement;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.nativeSource;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.declaring;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.withAdded;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Where the pack's nativeStructures placements start platform structures (was the core planner's test). */
public class NativeStartPlannerTest {
    @Test
    public void undergroundPlanIsDeterministicAndUsesConfiguredBand() {
        Engine engine = engine(982374L, -64, 384, 90);
        StructurePlacement placement = TestSettings.with(nativePlacement(), "underground", true, "minHeight", -45, "maxHeight", -20);

        NativeStartPlanner.Plan first = NativeStartPlanner.planAt(engine, placement, 12, -7);
        NativeStartPlanner.Plan second = NativeStartPlanner.planAt(engine, placement, 12, -7);

        assertNotNull(first);
        assertEquals(first, second);
        assertEquals("minecraft:ancient_city", first.source().structure());
        assertEquals(12, first.chunkX());
        assertEquals(-7, first.chunkZ());
        assertTrue(first.baseY() >= -45 && first.baseY() <= -20);
    }

    @Test
    public void undergroundBandOutsideWorldBoundsSkipsTheCandidate() {
        Engine engine = engine(982374L, -64, 384, 90);
        StructurePlacement placement = TestSettings.with(nativePlacement(), "underground", true, "minHeight", 500, "maxHeight", 600);

        assertNull(NativeStartPlanner.planAt(engine, placement, 12, -7));
    }

    @Test
    public void surfacePlanUsesIrisTerrainAndHonorsHeightGate() {
        Engine engine = engine(77L, -64, 384, 150);
        StructurePlacement placement = TestSettings.with(nativePlacement(), "minHeight", 80, "maxHeight", 90);

        NativeStartPlanner.Plan plan = NativeStartPlanner.planAt(engine, placement, 0, 0);

        assertNotNull(plan);
        assertEquals(86, plan.baseY());

        TestSettings.with(placement, "minHeight", 87);
        assertNull(NativeStartPlanner.planAt(engine, placement, 0, 0));
    }

    @Test
    public void aPlanBecomesAStartWithHeightsAboveTheBottomAndThePlacementsFitAndIdentity() {
        Gson json = new Gson();
        StructureTerrain terrain = json.fromJson("{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":24}", StructureTerrain.class);
        StructurePlacement placement = TestSettings.with(nativePlacement(), "underground", true, "nativeSuppression", NativeStructureSuppression.REPLACE_SOURCE, "terrain", terrain, "stilt", json.fromJson("{\"spacing\":3}", StructureStilts.class));
        TestSettings.with(placement.nativeStructures().getFirst(), "jigsaw", JigsawOverrides.none().withMaxDepth(4));
        NativeStartPlanner.Plan plan = new NativeStartPlanner.Plan(
                placement, placement.nativeStructures().getFirst(), 3, 5, -30);

        VanillaStructureStart start = StructureVanillaStructures.start(plan, -64);

        assertEquals(ResourceId.parse("minecraft:ancient_city"), start.structure());
        assertEquals(34, start.y());
        assertTrue(start.isUnderground());
        assertTrue(start.replacesPlatformStart());
        assertEquals(StructurePlacementGrid.placementIdentity(placement), start.identity());
        NativeStructureFit fit = NativeStructureFit.of(start.memo().orElseThrow());
        assertEquals(StructureTerrainMode.FORCE_CARVE, fit.terrain().resolvedMode());
        assertEquals(24, fit.terrain().horizontalPadding());
        assertEquals(3, fit.stilts().spacing());
        assertEquals(OptionalInt.of(4), start.jigsaw().orElseThrow().maxDepth());
        assertEquals("a source without jigsaw overrides has none", Optional.empty(),
                StructureVanillaStructures.start(new NativeStartPlanner.Plan(nativePlacement(),
                        nativePlacement().nativeStructures().getFirst(), 0, 0, 70), -64).jigsaw());
    }

    @Test
    public void underwaterFlagRejectsSubmergedColumnsForSurfaceAndUndergroundPlans() {
        Engine engine = engine(77L, -64, 384, 63);
        StructurePlacement placement = nativePlacement();

        assertNull(NativeStartPlanner.planAt(engine, placement, 0, 0));

        TestSettings.with(placement, "underground", true, "minHeight", -40, "maxHeight", -20);
        assertNull(NativeStartPlanner.planAt(engine, placement, 0, 0));

        TestSettings.with(placement, "underwater", true);
        assertNotNull(NativeStartPlanner.planAt(engine, placement, 0, 0));
    }

    @Test
    public void fluidHeightBoundaryIsShoreForPlannerAndLocator() {
        Engine engine = engine(77L, -64, 384, 64);
        StructurePlacement placement = nativePlacement();

        assertTrue(!NativeStartPlanner.isSubmerged(engine, 8, 8));
        assertNotNull(NativeStartPlanner.planAt(engine, placement, 0, 0));

        TestSettings.with(placement, "underground", true, "minHeight", -40, "maxHeight", -20);
        assertNotNull(NativeStartPlanner.planAt(engine, placement, 0, 0));
    }

    @Test
    public void duplicateSelectedStructureUsesOneDeterministicPlan() {
        Engine engine = engine(77L, -64, 384, 150);
        StructurePlacement first = TestSettings.with(nativePlacement(), "placementId", "first");
        StructurePlacement second = TestSettings.with(nativePlacement(), "placementId", "second");
        bindPlacements(engine, first, second);

        List<NativeStartPlanner.Plan> plans = NativeStartPlanner.plansAt(engine, 0, 0);

        StructurePlacement expected = Long.compareUnsigned(
                StructurePlacementGrid.placementIdentity(first),
                StructurePlacementGrid.placementIdentity(second)) <= 0 ? first : second;
        assertEquals(1, plans.size());
        assertSame(expected, plans.getFirst().placement());
    }

    @Test
    public void weightedNativeSelectionPreservesAuthoredSourceOrder() {
        KList<NativeStructureSource> first = new KList<>();
        first.add(nativeSource("test:a", 1));
        first.add(nativeSource("test:b", 3));
        KList<NativeStructureSource> reordered = new KList<>();
        reordered.add(first.get(1));
        reordered.add(first.get(0));

        RNG selectsFirstWeightSlot = new RNG(0L) {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        assertEquals("test:a", NativeStartPlanner
                .selectSource(first, selectsFirstWeightSlot).structure());
        assertEquals("test:b", NativeStartPlanner
                .selectSource(reordered, selectsFirstWeightSlot).structure());
    }

    @Test
    public void placementMustChooseExactlyOneBackend() {
        assertInvalid(new StructurePlacement());
        StructurePlacement mixed = nativePlacement();
        withAdded(mixed, "structures", "legacy");
        assertInvalid(mixed);
    }

    @Test
    public void nativeBackendRejectsEveryExplicitIrisAnchorMode() {
        assertUnsupportedAnchor(TestSettings.with(nativePlacement(), "anchor", StructureAnchorMode.SURFACE));
        assertUnsupportedAnchor(TestSettings.with(nativePlacement(), "anchor", StructureAnchorMode.HEIGHT_BAND));
        assertUnsupportedAnchor(TestSettings.with(nativePlacement(), "anchor", StructureAnchorMode.CAVE_FLOOR));
    }

    private StructurePlacement nativePlacement() {
        StructurePlacement placement = placement("distribution", StructureDistribution.DENSITY, "density", 1D);
        withAdded(placement, "nativeStructures", nativeSource("minecraft:ancient_city", 1));
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

    private void bindPlacements(Engine engine, StructurePlacement... placements) {
        IrisDimension dimension = engine.getDimension();
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(dimension.getModuleSettings()).thenReturn(declaring(placements));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
    }

    private void assertInvalid(StructurePlacement placement) {
        try {
            NativeStartPlanner.validateBackend(placement);
            fail("Expected an invalid structure backend");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("exactly one backend"));
        }
    }

    private void assertUnsupportedAnchor(StructurePlacement placement) {
        try {
            NativeStartPlanner.validateBackend(placement);
            fail("Expected native structure anchor rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("do not support the Iris anchor field"));
        }
    }
}
