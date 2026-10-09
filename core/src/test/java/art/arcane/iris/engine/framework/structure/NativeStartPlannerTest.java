package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.framework.StructurePlacementGrid;
import art.arcane.iris.engine.object.structure.IrisStructureStiltSettings;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConfiguration;
import art.arcane.iris.engine.object.structure.vanilla.NativeStructureSuppression;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.StructureTerrain;
import com.volmit.vanilla.VanillaStructureStart;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import art.arcane.iris.engine.object.structure.vanilla.IrisNativeStructure;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.structure.IrisStructureAnchorMode;
import art.arcane.iris.engine.object.structure.IrisStructurePlacement;
import art.arcane.iris.engine.object.structure.IrisStructureTerrain;
import art.arcane.iris.engine.object.structure.IrisStructureTerrainMode;
import art.arcane.iris.engine.object.structure.StructureDistribution;
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
import static org.mockito.Mockito.when;

/** Where the pack's nativeStructures placements start platform structures (was the core planner's test). */
public class NativeStartPlannerTest {
    @Test
    public void undergroundPlanIsDeterministicAndUsesConfiguredBand() {
        Engine engine = engine(982374L, -64, 384, 90);
        IrisStructurePlacement placement = nativePlacement()
                .setUnderground(true)
                .setMinHeight(-45)
                .setMaxHeight(-20);

        NativeStartPlanner.Plan first = NativeStartPlanner.planAt(engine, placement, 12, -7);
        NativeStartPlanner.Plan second = NativeStartPlanner.planAt(engine, placement, 12, -7);

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

        assertNull(NativeStartPlanner.planAt(engine, placement, 12, -7));
    }

    @Test
    public void surfacePlanUsesIrisTerrainAndHonorsHeightGate() {
        Engine engine = engine(77L, -64, 384, 150);
        IrisStructurePlacement placement = nativePlacement()
                .setMinHeight(80)
                .setMaxHeight(90);

        NativeStartPlanner.Plan plan = NativeStartPlanner.planAt(engine, placement, 0, 0);

        assertNotNull(plan);
        assertEquals(86, plan.baseY());

        placement.setMinHeight(87);
        assertNull(NativeStartPlanner.planAt(engine, placement, 0, 0));
    }

    @Test
    public void aPlanBecomesAStartWithHeightsAboveTheBottomAndThePlacementsFitAndIdentity() {
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setHorizontalPadding(24);
        IrisStructurePlacement placement = nativePlacement()
                .setUnderground(true)
                .setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE)
                .setTerrain(terrain)
                .setStilt(new IrisStructureStiltSettings().setSpacing(3));
        placement.getNativeStructures().getFirst().setJigsaw(new IrisJigsawConfiguration().setMaxDepth(4));
        NativeStartPlanner.Plan plan = new NativeStartPlanner.Plan(
                placement, placement.getNativeStructures().getFirst(), 3, 5, -30);

        VanillaStructureStart start = StructureVanillaHook.start(plan, -64);

        assertEquals(ResourceId.parse("minecraft:ancient_city"), start.structure());
        assertEquals(34, start.y());
        assertTrue(start.isUnderground());
        assertTrue(start.replacesPlatformStart());
        assertEquals(StructurePlacementGrid.placementIdentity(placement), start.identity());
        assertEquals(StructureTerrain.Mode.FORCE_CARVE, start.fit().terrain().orElseThrow().mode());
        assertEquals(24, start.fit().terrain().orElseThrow().horizontalPadding());
        assertEquals(3, start.fit().stilts().orElseThrow().spacing());
        assertEquals(OptionalInt.of(4), start.jigsaw().orElseThrow().maxDepth());
        assertEquals("a source without jigsaw overrides has none", Optional.empty(),
                StructureVanillaHook.start(new NativeStartPlanner.Plan(nativePlacement(),
                        nativePlacement().getNativeStructures().getFirst(), 0, 0, 70), -64).jigsaw());
    }

    @Test
    public void underwaterFlagRejectsSubmergedColumnsForSurfaceAndUndergroundPlans() {
        Engine engine = engine(77L, -64, 384, 63);
        IrisStructurePlacement placement = nativePlacement();

        assertNull(NativeStartPlanner.planAt(engine, placement, 0, 0));

        placement.setUnderground(true).setMinHeight(-40).setMaxHeight(-20);
        assertNull(NativeStartPlanner.planAt(engine, placement, 0, 0));

        placement.setUnderwater(true);
        assertNotNull(NativeStartPlanner.planAt(engine, placement, 0, 0));
    }

    @Test
    public void fluidHeightBoundaryIsShoreForPlannerAndLocator() {
        Engine engine = engine(77L, -64, 384, 64);
        IrisStructurePlacement placement = nativePlacement();

        assertTrue(!NativeStartPlanner.isSubmerged(engine, 8, 8));
        assertNotNull(NativeStartPlanner.planAt(engine, placement, 0, 0));

        placement.setUnderground(true).setMinHeight(-40).setMaxHeight(-20);
        assertNotNull(NativeStartPlanner.planAt(engine, placement, 0, 0));
    }

    @Test
    public void duplicateSelectedStructureUsesOneDeterministicPlan() {
        Engine engine = engine(77L, -64, 384, 150);
        IrisStructurePlacement first = nativePlacement().setPlacementId("first");
        IrisStructurePlacement second = nativePlacement().setPlacementId("second");
        bindPlacements(engine, first, second);

        List<NativeStartPlanner.Plan> plans = NativeStartPlanner.plansAt(engine, 0, 0);

        IrisStructurePlacement expected = Long.compareUnsigned(
                StructurePlacementGrid.placementIdentity(first),
                StructurePlacementGrid.placementIdentity(second)) <= 0 ? first : second;
        assertEquals(1, plans.size());
        assertSame(expected, plans.getFirst().placement());
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
        assertEquals("test:a", NativeStartPlanner
                .selectSource(first, selectsFirstWeightSlot).getStructure());
        assertEquals("test:b", NativeStartPlanner
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
            NativeStartPlanner.validateBackend(placement);
            fail("Expected an invalid structure backend");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("exactly one backend"));
        }
    }

    private void assertUnsupportedAnchor(IrisStructurePlacement placement) {
        try {
            NativeStartPlanner.validateBackend(placement);
            fail("Expected native structure anchor rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("do not support the Iris anchor field"));
        }
    }
}
