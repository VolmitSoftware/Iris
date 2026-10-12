package art.arcane.iris.engine.framework;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.placement;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.nativeSource;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.declaring;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.withAdded;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisStructurePlacementRingLocatorTest {
    private static final String STRUCTURE_KEY = "test:manual_native";

    @Test
    public void zeroVanillaRingSearchesTheOriginPlacementCell() {
        StructurePlacement placement = randomPlacement("origin-cell", 24, 6);
        Engine engine = engine(placement);
        long seed = engine.getSeedManager().getMantle();
        int[] expected = StructurePlacementGrid.randomSpreadCellChunk(
                0, 0, placement.spacing(), placement.separation(),
                StructurePlacementGrid.placementSalt(placement), seed);
        assertTrue(expected[0] != 0 || expected[1] != 0);

        IrisStructureLocator.LocateResult chunkRadius = IrisStructureLocator.locate(
                engine, STRUCTURE_KEY, 0, 0, 0);
        IrisStructureLocator.LocateResult placementRadius =
                IrisStructureLocator.locateInPlacementRings(
                        engine, STRUCTURE_KEY, 0, 0, 0, (chunkX, chunkZ) -> true);

        assertEquals(IrisStructureLocator.LocateStatus.NOT_FOUND, chunkRadius.status());
        assertEquals(IrisStructureLocator.LocateStatus.FOUND, placementRadius.status());
        assertEquals(expected[0] << 4, placementRadius.originX());
        assertEquals(expected[1] << 4, placementRadius.originZ());
    }

    @Test
    public void mixedSpacingPlacementsShareTheSameVanillaRingIndex() {
        StructurePlacement small = randomPlacement("small-grid", 8, 2);
        StructurePlacement large = randomPlacement("large-grid", 40, 10);
        Engine engine = engine(small, large);
        long seed = engine.getSeedManager().getMantle();
        int[] smallRingZero = candidate(small, seed, 0, 0);
        int[] largeRingZero = candidate(large, seed, 0, 0);
        int[] smallRingOne = candidate(small, seed, -1, -1);
        AtomicBoolean visitedLaterSmallRing = new AtomicBoolean();

        IrisStructureLocator.LocateResult result =
                IrisStructureLocator.locateInPlacementRings(
                        engine, STRUCTURE_KEY, 0, 0, 1,
                        (chunkX, chunkZ) -> {
                            if (chunkX == smallRingOne[0] && chunkZ == smallRingOne[1]) {
                                visitedLaterSmallRing.set(true);
                            }
                            return chunkX == largeRingZero[0] && chunkZ == largeRingZero[1];
                        });

        assertTrue(smallRingZero[0] != largeRingZero[0]
                || smallRingZero[1] != largeRingZero[1]);
        assertEquals(IrisStructureLocator.LocateStatus.FOUND, result.status());
        assertEquals(largeRingZero[0] << 4, result.originX());
        assertEquals(largeRingZero[1] << 4, result.originZ());
        assertFalse(visitedLaterSmallRing.get());
    }

    @Test
    public void concentricPlacementsIgnoreTheRandomSpreadRingRadius() {
        StructurePlacement placement = placement("placementId", "concentric", "distribution", StructureDistribution.CONCENTRIC_RINGS, "ringCount", 1, "ringDistance", 32, "ringSpread", 1);
        withAdded(placement, "nativeStructures", 
                nativeSource(STRUCTURE_KEY));
        Engine engine = engine(placement);

        IrisStructureLocator.LocateResult result =
                IrisStructureLocator.locateInPlacementRings(
                        engine, STRUCTURE_KEY, 0, 0, 0, (chunkX, chunkZ) -> true);

        assertEquals(IrisStructureLocator.LocateStatus.FOUND, result.status());
        long chunkX = result.originX() >> 4;
        long chunkZ = result.originZ() >> 4;
        assertTrue(chunkX * chunkX + chunkZ * chunkZ >= 31L * 31L);
    }

    @Test
    public void sparseDensityPlacementRingSearchStopsAtTheCandidateBudget() {
        StructurePlacement placement = placement("placementId", "density", "distribution", StructureDistribution.DENSITY, "density", 1.0);
        withAdded(placement, "nativeStructures", 
                nativeSource(STRUCTURE_KEY));
        Engine engine = engine(placement);

        IrisStructureLocator.LocateResult result =
                IrisStructureLocator.locateInPlacementRings(
                        engine, STRUCTURE_KEY, 0, 0, 2048,
                        (chunkX, chunkZ) -> false);

        assertEquals(IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED, result.status());
    }

    @Test
    public void concentricPlacementRingSearchStopsAtTheSharedCandidateBudget() {
        StructurePlacement placement = placement("placementId", "unbounded-concentric-rings", "distribution", StructureDistribution.CONCENTRIC_RINGS, "ringCount", Integer.MAX_VALUE, "ringDistance", 32, "ringSpread", 1);
        withAdded(placement, "nativeStructures", 
                nativeSource(STRUCTURE_KEY));
        Engine engine = engine(placement);

        IrisStructureLocator.LocateResult result = IrisStructureLocator.locateInPlacementRings(
                engine, STRUCTURE_KEY, 0, 0, 2048, (chunkX, chunkZ) -> false);

        assertEquals(IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED, result.status());
    }

    @Test
    public void concentricChunkRadiusSearchStopsAtTheSharedCandidateBudget() {
        StructurePlacement placement = placement("placementId", "unbounded-concentric-chunks", "distribution", StructureDistribution.CONCENTRIC_RINGS, "ringCount", Integer.MAX_VALUE, "ringDistance", 32, "ringSpread", 1);
        withAdded(placement, "nativeStructures", 
                nativeSource(STRUCTURE_KEY));
        Engine engine = engine(placement);

        IrisStructureLocator.LocateResult result = IrisStructureLocator.locate(
                engine, STRUCTURE_KEY, 0, 0, 2048, (chunkX, chunkZ) -> false);

        assertEquals(IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED, result.status());
    }

    private static int[] candidate(
            StructurePlacement placement, long seed, int cellX, int cellZ) {
        return StructurePlacementGrid.randomSpreadCellChunk(
                cellX, cellZ, placement.spacing(), placement.separation(),
                StructurePlacementGrid.placementSalt(placement), seed);
    }

    private static StructurePlacement randomPlacement(
            String placementId, int spacing, int separation) {
        StructurePlacement placement = placement("placementId", placementId, "distribution", StructureDistribution.RANDOM_SPREAD, "spacing", spacing, "separation", separation);
        withAdded(placement, "nativeStructures", 
                nativeSource(STRUCTURE_KEY));
        return placement;
    }

    private static Engine engine(StructurePlacement... placements) {
        IrisData data = mock(IrisData.class);
        IrisDimension dimension = mock(IrisDimension.class);
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        KList<StructurePlacement> configured = new KList<>();
        for (StructurePlacement placement : placements) {
            configured.add(placement);
        }
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getSeedManager()).thenReturn(new SeedManager(1337L));
        when(engine.getComplex()).thenReturn(null);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(engine.getHeight(anyInt(), anyInt(), anyBoolean())).thenReturn(128);
        when(dimension.getFluidHeight()).thenReturn(63);
        when(dimension.getModuleSettings()).thenReturn(declaring(configured));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }
}
