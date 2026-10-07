package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class HydrologySurfaceBudgetCacheTest {
    @Test
    public void repeatedBudgetReadsScanOnceAndPreserveReferenceAreaOrderAndCounts() {
        HydrologySampledGrid grid = spy(grid());
        HydrologyPlannerSettings.Source sources = sources(40);
        HydrologySurfaceBudgets reference = HydrologySurfaceBudgets.sample(grid, sources);
        clearInvocations(grid);

        HydrologySurfaceBudgets cached = grid.surfaceBudgets(sources);
        for (int call = 0; call < 16; call++) {
            assertSame(cached, grid.surfaceBudgets(sources(40)));
        }

        verify(grid, times(1)).nodes();
        assertBudgetsEqual(reference, cached);
        assertEquals(List.of("west", "east"), cached.areas().stream().map(area -> area.policy.areaKey()).toList());
        assertEquals(8, cached.areas().getFirst().landCells);
        assertEquals(8, cached.areas().getFirst().sourceCells);
        assertEquals(7, cached.areas().getLast().landCells);
        assertEquals(7, cached.areas().getLast().sourceCells);
    }

    @Test
    public void changingSourceElevationReplacesOnlyThisGridsSnapshot() {
        HydrologySampledGrid grid = grid();
        HydrologySampledGrid other = grid();
        HydrologySurfaceBudgets initial = grid.surfaceBudgets(sources(40));
        HydrologySurfaceBudgets otherInitial = other.surfaceBudgets(sources(40));
        HydrologySurfaceBudgets elevated = grid.surfaceBudgets(sources(80));

        assertNotSame(initial, elevated);
        assertBudgetsEqual(HydrologySurfaceBudgets.sample(grid, sources(80)), elevated);
        assertEquals(0, elevated.areas().getFirst().sourceCells);
        assertEquals(7, elevated.areas().getLast().sourceCells);
        assertSame(otherInitial, other.surfaceBudgets(sources(40)));
        assertBudgetsEqual(initial, grid.surfaceBudgets(sources(40)));
    }

    @Test(timeout = 10000)
    public void concurrentConsumersPublishOneCompleteBudgetSnapshot() throws Exception {
        HydrologySampledGrid grid = spy(grid());
        HydrologyPlannerSettings.Source sources = sources(40);
        HydrologySurfaceBudgets reference = HydrologySurfaceBudgets.sample(grid, sources);
        clearInvocations(grid);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<HydrologySurfaceBudgets>> tasks = new ArrayList<>();
            for (int call = 0; call < 32; call++) {
                tasks.add(executor.submit(() -> grid.surfaceBudgets(sources)));
            }
            HydrologySurfaceBudgets first = tasks.getFirst().get();
            for (Future<HydrologySurfaceBudgets> task : tasks) {
                assertSame(first, task.get());
            }
            assertBudgetsEqual(reference, first);
            verify(grid, times(1)).nodes();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void seededSourceAndOutletQuotasMatchUncachedBudgets() {
        HydrologySampledGrid grid = grid();
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologySurfaceBudgets reference = HydrologySurfaceBudgets.sample(grid, settings.surface().sources());
        HydrologySurfaceBudgets cached = grid.surfaceBudgets(settings.surface().sources());
        for (long seed : new long[]{0L, 17L, -31L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            HydrologyPlanner planner = new HydrologyPlanner(seed, settings,
                    (x, z) -> HydrologyTerrainSample.openLand(90, 0D, "land"));
            for (HydrologyTileKey tile : List.of(new HydrologyTileKey(0, 0), new HydrologyTileKey(-3, 5))) {
                for (HydrologySurfaceBudgets.Area area : reference.areas()) {
                    HydrologySurfaceBudgets.Area actual = cached.area(area.policy);
                    for (int outlets = 0; outlets <= 4; outlets++) {
                        assertEquals(reference.sourceTarget(area, outlets, planner, tile),
                                cached.sourceTarget(actual, outlets, planner, tile));
                    }
                    assertEquals(reference.outletTarget(area, true, planner, tile),
                            cached.outletTarget(actual, true, planner, tile));
                    assertEquals(reference.outletTarget(area, false, planner, tile),
                            cached.outletTarget(actual, false, planner, tile));
                }
            }
        }
    }

    private static void assertBudgetsEqual(HydrologySurfaceBudgets expected, HydrologySurfaceBudgets actual) {
        assertEquals(expected.overridden(), actual.overridden());
        assertEquals(expected.areas().size(), actual.areas().size());
        for (int index = 0; index < expected.areas().size(); index++) {
            HydrologySurfaceBudgets.Area expectedArea = expected.areas().get(index);
            HydrologySurfaceBudgets.Area actualArea = actual.areas().get(index);
            assertEquals(expectedArea.policy, actualArea.policy);
            assertEquals(expectedArea.landCells, actualArea.landCells);
            assertEquals(expectedArea.sourceCells, actualArea.sourceCells);
            assertSame(actualArea, actual.area(expectedArea.policy));
        }
    }

    private static HydrologyPlannerSettings.Source sources(int minimumElevation) {
        return new HydrologyPlannerSettings.Source(true, 3D, minimumElevation, 0, 64, 160);
    }

    private static HydrologySampledGrid grid() {
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>();
        int width = 6;
        for (int gridZ = 0; gridZ < width; gridZ++) {
            for (int gridX = 0; gridX < width; gridX++) {
                int x = -32 + gridX * 32;
                int z = -32 + gridZ * 32;
                SurfaceRiverPolicy policy = new SurfaceRiverPolicy(x < 64 ? "west" : "east", 8D, 160,
                        1, 3, 4, gridZ % 2 == 0 ? 128 : 256, null);
                HydrologyTerrainSample terrain = gridX == 3 && gridZ == 3
                        ? HydrologyTerrainSample.ocean(30, "land")
                        : HydrologyTerrainSample.openLand(x < 64 ? 60 : 90, 0D, "land");
                nodes.add(new HydrologyGridNode(nodes.size(), gridX, gridZ, x, z, nodes.size(),
                        terrain.withSurfacePolicy(policy)));
            }
        }
        return new HydrologySampledGrid(-32, -32, 0, 0, 128, width, 32, nodes);
    }
}
