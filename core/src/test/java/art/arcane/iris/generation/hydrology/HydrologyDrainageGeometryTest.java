package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

public class HydrologyDrainageGeometryTest {
    @Test
    public void sourceBranchesShareExactlyTheSameDescendingTrunk() {
        HydrologyTerrainSampler terrain = (x, z) -> land(120 - x / 8);
        Fixture fixture = fixture(terrain);
        HydrologyDrainageGeometry geometry = fixture.geometry();
        List<HydrologyPoint> first = geometry.edge(0);
        List<HydrologyPoint> tributary = geometry.edge(3);
        List<HydrologyPoint> trunk = geometry.edge(1);
        assertFalse(first.isEmpty());
        assertFalse(tributary.isEmpty());
        assertFalse(trunk.isEmpty());
        assertEquals(first.getLast(), trunk.getFirst());
        assertEquals(tributary.getLast(), trunk.getFirst());
        for (List<HydrologyPoint> edge : List.of(first, tributary, trunk)) {
            for (int index = 1; index < edge.size(); index++) {
                assertTrue(edge.get(index - 1).y() >= edge.get(index).y());
            }
        }
        assertEquals(geometry.head(1), trunk.getFirst().y());
    }

    @Test
    public void repeatedAndConcurrentSourcesReuseOneGeometryComputation() {
        AtomicInteger sampled = new AtomicInteger();
        Fixture fixture = fixture((x, z) -> {
            sampled.incrementAndGet();
            return land(120 - x / 8);
        });
        ArrayList<CompletableFuture<List<HydrologyPoint>>> tasks = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            tasks.add(CompletableFuture.supplyAsync(() -> fixture.geometry().edge(0)));
        }
        List<HydrologyPoint> expected = tasks.getFirst().join();
        assertFalse(expected.isEmpty());
        for (CompletableFuture<List<HydrologyPoint>> task : tasks) {
            assertSame(expected, task.join());
        }
        int samples = sampled.get();
        assertSame(expected, fixture.geometry().edge(0));
        assertEquals(samples, sampled.get());
    }

    @Test
    public void aFailedRequiredSampleCanBeRetriedWithoutCachingItsFailure() {
        AtomicInteger attempts = new AtomicInteger();
        IllegalStateException unavailable = new IllegalStateException("Terrain unavailable");
        Fixture fixture = fixture((x, z) -> {
            if (x == 31 && attempts.incrementAndGet() == 1) {
                throw unavailable;
            }
            return land(120 - x / 8);
        });
        assertSame(unavailable, assertThrows(IllegalStateException.class, () -> fixture.geometry().edge(0)));
        assertFalse(fixture.geometry().edge(0).isEmpty());
    }

    @Test
    public void detailedReliefDoesNotSendTheSharedRiverSearchingForAnotherRoute() {
        Fixture flat = fixture((x, z) -> land(120 - x / 8));
        Fixture broken = fixture((x, z) -> land(x > 24 && x < 40 ? 90 : 120 - x / 8));
        assertEquals(flat.geometry().edge(0), broken.geometry().edge(0));
        assertFalse(broken.geometry().edge(0).isEmpty());
    }

    @Test
    public void aThinExcludedPolicyBandStillBlocksAnEdge() {
        HydrologyTerrainSample blocked = spy(land(116));
        doReturn(false).when(blocked).transitAllowed();
        Fixture fixture = fixture((x, z) -> x == 31 ? blocked : land(120 - x / 8));
        assertTrue(fixture.geometry().edge(0).isEmpty());
    }

    @Test
    public void coastalRootKeepsItsLandwardPointAndDescendingHeadAboveTheSea() {
        Fixture fixture = fixture((x, z) -> land(120 - x / 8));
        HydrologyPoint root = fixture.geometry().anchor(2);
        assertEquals(128, root.x());
        assertEquals(0, root.z());
        assertTrue(root.y() >= fixture.planner().settings.seaLevel());
        assertEquals(root, fixture.geometry().edge(1).getLast());
    }

    @Test
    public void everyOutletTrialReusesTheSameGridCostsAndPolicyEdges() {
        Fixture fixture = fixture((x, z) -> land(120 - x / 8));
        HydrologySourcePlanner sources = fixture.planner().sourcePlanner;
        HydrologyDrainageGraph graph = fixture.grid().drainage(sources);
        sources.buildRouting(fixture.grid(), List.of(fixture.outlet()), true);
        sources.buildRouting(fixture.grid(), List.of(fixture.outlet()), false);
        assertSame(graph, fixture.grid().drainage(sources));
        for (HydrologyGridNode downstream : fixture.grid().nodes()) {
            for (int direction = 0; direction < HydrologySourcePlanner.ROUTING_OFFSETS.size(); direction++) {
                HydrologyGridOffset offset = HydrologySourcePlanner.ROUTING_OFFSETS.get(direction);
                HydrologyGridNode upstream = fixture.grid().nodeAt(downstream.gridX() + offset.x(), downstream.gridZ() + offset.z());
                if (upstream == null) {
                    assertEquals(-1, graph.upstream(downstream.index(), direction, false));
                } else {
                    assertEquals(upstream.index(), graph.upstream(downstream.index(), direction, false));
                    assertEquals(sources.routeCost(upstream, downstream, offset,
                            sources.minimumNeighborHeight(fixture.grid(), downstream)), graph.cost(downstream.index(), direction), 0D);
                }
            }
        }
    }

    @Test
    public void aCuttableRidgeDoesNotLiftTheSharedUpstreamRiverAboveItsSpring() {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Surface surface = base.surface();
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(base.seaLevel(), base.routing(),
                new HydrologyPlannerSettings.Surface(surface.enabled(), surface.sources(), surface.minimumWidth(),
                        surface.maximumWidth(), surface.minimumDepth(), surface.maximumDepth(), 64,
                        surface.shoreWidth(), surface.banks()), base.hydraulics(), base.underground(),
                base.outlets(), base.geometry(), base.deepFluids(), base.surfacePools(),
                base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
        HydrologyTerrainSampler terrain = (x, z) -> land(x == 64 ? 154 : 120 - x / 8);
        Fixture fixture = fixture(terrain, settings, terrain);
        HydrologyDrainageGeometry geometry = fixture.geometry();
        int depth = (surface.minimumDepth() + surface.maximumDepth()) / 2;

        assertEquals(120 - surface.banks().sink() - depth, geometry.head(0));
        assertTrue(geometry.head(0) >= geometry.head(1));
        assertTrue(geometry.head(1) >= geometry.head(2));
        assertTrue(154 - geometry.head(1) + depth <= 64);
        assertEquals(geometry.edge(0).getLast(), geometry.edge(1).getFirst());
    }

    @Test
    public void aSourceExcludedDepressionDoesNotLowerTheSharedOutletHead() {
        HydrologyTerrainSample excluded = spy(land(90));
        doReturn(false).when(excluded).surfaceSourceAllowed();
        HydrologyTerrainSampler terrain = (x, z) -> z == 0 ? land(120 - x / 8) : excluded;
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        Fixture fixture = fixture(terrain, settings, terrain);
        HydrologyPlannerSettings.Surface surface = settings.surface();
        int depth = (surface.minimumDepth() + surface.maximumDepth()) / 2;

        assertEquals(104 - surface.banks().sink() - depth, fixture.geometry().head(2));
        assertTrue(fixture.geometry().head(0) >= fixture.geometry().head(1));
        assertTrue(fixture.geometry().head(1) >= fixture.geometry().head(2));
        assertEquals(fixture.geometry().edge(0).getLast(), fixture.geometry().edge(1).getFirst());
    }

    private Fixture fixture(HydrologyTerrainSampler terrain) {
        return fixture(terrain, HydrologyPlannerSettings.defaults(), (x, z) -> land(120 - x / 8));
    }

    private Fixture fixture(HydrologyTerrainSampler terrain, HydrologyPlannerSettings settings,
                            HydrologyTerrainSampler coarseTerrain) {
        HydrologyPlanner planner = new HydrologyPlanner(91L, settings, terrain);
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            int x = index % 3 * 64;
            int z = index / 3 * 64;
            nodes.add(new HydrologyGridNode(index, index % 3, index / 3, x, z, index + 1L, coarseTerrain.sample(x, z)));
        }
        HydrologySampledGrid grid = new HydrologySampledGrid(0, 0, 0, 0, 192, 3, 64, nodes);
        OutletCandidate outlet = new OutletCandidate(2, -1, new RiverOutlet(10L, HydrologyFeatureType.MOUTH,
                3L, new HydrologyPoint(128, 104, 0), new HydrologyPoint(129, 63, 0), 63, true));
        int[] parent = {1, 2, -1, 1, 1, 2, 3, 4, 5};
        HydrologyDrainageGeometry geometry = new HydrologyDrainageGeometry(planner,
                new HydrologyDrainageGeometry.Basin(grid, parent, new int[9], List.of(outlet)));
        return new Fixture(planner, grid, outlet, geometry);
    }

    private static HydrologyTerrainSample land(int height) {
        return HydrologyTerrainSample.openLand(height, 0D, "land");
    }

    private record Fixture(HydrologyPlanner planner, HydrologySampledGrid grid,
                           OutletCandidate outlet, HydrologyDrainageGeometry geometry) {
    }
}
