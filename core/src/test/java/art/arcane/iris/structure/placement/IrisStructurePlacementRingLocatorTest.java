package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.nativegen.IrisNativeStructure;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

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
        IrisStructurePlacement placement = randomPlacement("origin-cell", 24, 6);
        Engine engine = engine(placement);
        long seed = engine.getSeedManager().getMantle();
        int[] expected = StructurePlacementGrid.randomSpreadCellChunk(
                0, 0, placement.getSpacing(), placement.getSeparation(),
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
        IrisStructurePlacement small = randomPlacement("small-grid", 8, 2);
        IrisStructurePlacement large = randomPlacement("large-grid", 40, 10);
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
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setPlacementId("concentric")
                .setDistribution(StructureDistribution.CONCENTRIC_RINGS)
                .setRingCount(1)
                .setRingDistance(32)
                .setRingSpread(1);
        placement.getNativeStructures().add(
                new IrisNativeStructure().setStructure(STRUCTURE_KEY));
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
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setPlacementId("density")
                .setDistribution(StructureDistribution.DENSITY)
                .setDensity(1.0);
        placement.getNativeStructures().add(
                new IrisNativeStructure().setStructure(STRUCTURE_KEY));
        Engine engine = engine(placement);

        IrisStructureLocator.LocateResult result =
                IrisStructureLocator.locateInPlacementRings(
                        engine, STRUCTURE_KEY, 0, 0, 2048,
                        (chunkX, chunkZ) -> false);

        assertEquals(IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED, result.status());
    }

    @Test
    public void concentricPlacementRingSearchStopsAtTheSharedCandidateBudget() {
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setPlacementId("unbounded-concentric-rings")
                .setDistribution(StructureDistribution.CONCENTRIC_RINGS)
                .setRingCount(Integer.MAX_VALUE)
                .setRingDistance(32)
                .setRingSpread(1);
        placement.getNativeStructures().add(
                new IrisNativeStructure().setStructure(STRUCTURE_KEY));
        Engine engine = engine(placement);

        IrisStructureLocator.LocateResult result = IrisStructureLocator.locateInPlacementRings(
                engine, STRUCTURE_KEY, 0, 0, 2048, (chunkX, chunkZ) -> false);

        assertEquals(IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED, result.status());
    }

    @Test
    public void concentricChunkRadiusSearchStopsAtTheSharedCandidateBudget() {
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setPlacementId("unbounded-concentric-chunks")
                .setDistribution(StructureDistribution.CONCENTRIC_RINGS)
                .setRingCount(Integer.MAX_VALUE)
                .setRingDistance(32)
                .setRingSpread(1);
        placement.getNativeStructures().add(
                new IrisNativeStructure().setStructure(STRUCTURE_KEY));
        Engine engine = engine(placement);

        IrisStructureLocator.LocateResult result = IrisStructureLocator.locate(
                engine, STRUCTURE_KEY, 0, 0, 2048, (chunkX, chunkZ) -> false);

        assertEquals(IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED, result.status());
    }

    private static int[] candidate(
            IrisStructurePlacement placement, long seed, int cellX, int cellZ) {
        return StructurePlacementGrid.randomSpreadCellChunk(
                cellX, cellZ, placement.getSpacing(), placement.getSeparation(),
                StructurePlacementGrid.placementSalt(placement), seed);
    }

    private static IrisStructurePlacement randomPlacement(
            String placementId, int spacing, int separation) {
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setPlacementId(placementId)
                .setDistribution(StructureDistribution.RANDOM_SPREAD)
                .setSpacing(spacing)
                .setSeparation(separation);
        placement.getNativeStructures().add(
                new IrisNativeStructure().setStructure(STRUCTURE_KEY));
        return placement;
    }

    private static Engine engine(IrisStructurePlacement... placements) {
        IrisData data = mock(IrisData.class);
        IrisDimension dimension = mock(IrisDimension.class);
        Engine engine = mock(Engine.class);
        KList<IrisStructurePlacement> configured = new KList<>();
        for (IrisStructurePlacement placement : placements) {
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
        when(dimension.getStructures()).thenReturn(configured);
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }
}
