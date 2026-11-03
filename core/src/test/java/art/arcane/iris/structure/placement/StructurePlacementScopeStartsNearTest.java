package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StructurePlacementScopeStartsNearTest {
    private static final long MANTLE_SEED = 1337L;

    @Test
    public void reportsAStartWithinTheReachAndNothingOutsideIt() {
        IrisStructurePlacement dense = new IrisStructurePlacement().setDistribution(StructureDistribution.DENSITY).setDensity(1D);
        assertTrue(StructurePlacementScope.startsNear(engine(new KList<>(dense)), 3, -2, 1));

        IrisStructurePlacement never = new IrisStructurePlacement().setDistribution(StructureDistribution.DENSITY).setDensity(0D);
        assertFalse(StructurePlacementScope.startsNear(engine(new KList<>(never)), 3, -2, 8));
        assertFalse(StructurePlacementScope.startsNear(engine(new KList<>()), 3, -2, 8));
    }

    @Test
    public void aStartCountsExactlyUpToTheReachOnEverySide() {
        IrisStructurePlacement sparse = new IrisStructurePlacement().setDistribution(StructureDistribution.DENSITY).setDensity(0.0005D);
        Engine engine = engine(new KList<>(sparse));
        int[] start = loneStart(sparse);
        for (int reach : new int[]{1, 3, 8}) {
            for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}}) {
                int chunkX = start[0] + side[0] * reach;
                int chunkZ = start[1] + side[1] * reach;
                assertTrue(StructurePlacementScope.startsNear(engine, chunkX, chunkZ, reach));
                assertFalse(StructurePlacementScope.startsNear(engine, chunkX, chunkZ, reach - 1));
            }
        }
    }

    private static int[] loneStart(IrisStructurePlacement placement) {
        for (int x = 0; x < 4096; x++) {
            for (int z = 0; z < 4096; z++) {
                if (StructurePlacementGrid.startsInChunk(placement, x, z, MANTLE_SEED) && alone(placement, x, z)) {
                    return new int[]{x, z};
                }
            }
        }
        throw new AssertionError("No lone start in the searched chunks");
    }

    private static boolean alone(IrisStructurePlacement placement, int startX, int startZ) {
        for (int x = startX - 16; x <= startX + 16; x++) {
            for (int z = startZ - 16; z <= startZ + 16; z++) {
                if ((x != startX || z != startZ) && StructurePlacementGrid.startsInChunk(placement, x, z, MANTLE_SEED)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Engine engine(KList<IrisStructurePlacement> placements) {
        Engine engine = mock(Engine.class);
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getMantle()).thenReturn(MANTLE_SEED);
        when(engine.getSeedManager()).thenReturn(seeds);
        IrisDimension dimension = new IrisDimension();
        dimension.setStructures(placements);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getAllBiomes()).thenReturn(new KList<>());
        return engine;
    }
}
