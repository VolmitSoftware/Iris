package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalGraphTest {
    @Test
    public void naturallyFloodedLandDoesNotJoinTheDryDrainageTree() {
        Fixture fixture = fixture(false, true);
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>(fixture.grid().nodes());
        HydrologyGridNode original = nodes.get(12);
        nodes.set(12, new HydrologyGridNode(original.index(), original.gridX(), original.gridZ(),
                original.x(), original.z(), original.id(), HydrologyTerrainSample.openLand(60, 0D, "land")));
        HydrologySampledGrid grid = new HydrologySampledGrid(0, 0, 0, 0, 1280, 5, 256, nodes);
        HydrologyRegionalGraph.Tree tree = fixture.graph().route(grid, fixture.outlets(), false);

        assertEquals(-1, tree.root(12));
        assertEquals(-1, tree.root(14));
    }

    @Test
    public void individuallyLegalRisesCannotAccumulateIntoAnImpossibleDownstreamRidge() {
        Fixture fixture = fixture(false, false);
        HydrologyRegionalGraph.Tree tree = fixture.graph().route(fixture.grid(), fixture.outlets(), false);

        assertEquals(-1, tree.root(14));
        assertTrue(tree.root(12) >= 0);
    }

    @Test
    public void aHigherCostLowValleyPreservesTheGloballyFeasibleDrainageRoute() {
        Fixture fixture = fixture(true, false);
        HydrologyRegionalGraph.Tree tree = fixture.graph().route(fixture.grid(), fixture.outlets(), false);
        List<Integer> path = fixture.graph().path(tree, 14);

        assertTrue(tree.root(14) >= 0);
        assertTrue(path.stream().anyMatch(index -> fixture.grid().node(index).gridZ() == 4));
        assertFalse(path.contains(11));
        assertEquals(path, fixture.graph().path(fixture.graph().route(fixture.grid(), fixture.outlets(), false), 14));
    }

    @Test
    public void downstreamCliffsRemainLegal() {
        Fixture fixture = fixture(false, true);
        HydrologyRegionalGraph.Tree tree = fixture.graph().route(fixture.grid(), fixture.outlets(), false);

        assertEquals(List.of(14, 13, 12, 11, 10), fixture.graph().path(tree, 14));
    }

    @Test
    public void minimumHeadUsesDepthAndTheStricterPolicyIncisionMultiplier() {
        HydrologyPlannerSettings settings = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(settings);
        HydrologyTerrainSample terrain = new HydrologyTerrainSample(80, 0D, false, false, 40, 42,
                true, true, true, false, false, false, 0D, 1D, 1D, 1D, 2D, 0.5D, 1D, 1D,
                "land", "land", "land", "land", "land", "land", List.of("default"), List.of(),
                Double.NaN, null, Double.NaN, true,
                new SurfaceRiverPolicy("limited", null, null, null, null, null, null, 10));

        assertEquals(79, hydraulics.minimumHead(terrain));
        assertEquals(80, hydraulics.maximumHead(terrain));
    }

    @Test
    public void addingALowerHeadOutletPreservesTheShorterFeasibleUpstreamRoute() {
        Fixture fixture = competingOutlets(false);
        HydrologyRegionalGraph.Tree both = fixture.graph().route(fixture.grid(), fixture.outlets(), false);
        HydrologyRegionalGraph.Tree shortOnly = fixture.graph().route(fixture.grid(), List.of(fixture.outlets().getLast()), false);

        assertEquals(List.of(9, 14, 19, 24), fixture.graph().path(shortOnly, 9));
        assertEquals(fixture.graph().path(shortOnly, 9), fixture.graph().path(both, 9));
        assertEquals(768D, both.length(9), 0D);
        assertEquals(1104D, both.cost(9), 0D);
        assertEquals(2L, both.outlets().get(both.root(9)).outlet().id());
        assertEquals(List.of(14, 13, 12, 11, 10), fixture.graph().path(both, 14));
        assertEquals(1024D, both.length(14), 0D);
    }

    @Test
    public void aCheaperLongerRouteCannotHideAnEqualHeadShorterRoute() {
        Fixture fixture = competingOutlets(true);
        HydrologyRegionalGraph.Tree tree = fixture.graph().route(fixture.grid(), fixture.outlets(), false);

        assertEquals(List.of(14, 13, 12, 11, 10), fixture.graph().path(tree, 14));
        assertEquals(List.of(9, 14, 19, 24), fixture.graph().path(tree, 9));
        assertEquals(768D, tree.length(9), 0D);
        assertTrue(tree.cost(9) > tree.cost(14));
    }

    @Test
    public void sharedCoordinatesAccumulateEachSourceOnceAlongItsExactSelectedRoute() {
        Fixture fixture = competingOutlets(false);
        HydrologyRegionalGraph.Tree tree = fixture.graph().route(fixture.grid(), fixture.outlets(), false);

        assertArrayEquals(new int[]{1, 1, 2, 3}, tree.contributions(9));
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, tree.contributions(14));
        assertEquals(8, tree.contributions(10)[0] + tree.contributions(24)[0]);
    }

    @Test
    public void distinctShorePositionsInOneGridCellKeepTheirOwnRouteLengths() {
        Fixture fixture = fixture(false, true);
        OutletCandidate close = new OutletCandidate(10, 5, new RiverOutlet(2L, HydrologyFeatureType.MOUTH, 10,
                new HydrologyPoint(200, 63, 512), new HydrologyPoint(200, 63, 511), 63, true));
        HydrologyRegionalGraph.Tree tree = fixture.graph().route(fixture.grid(), List.of(fixture.outlets().getFirst(), close), false);

        assertEquals(List.of(11, 10), fixture.graph().path(tree, 11));
        assertEquals(56D, tree.length(11), 0D);
        assertEquals(2L, tree.outlets().get(tree.root(11)).outlet().id());
    }

    private static Fixture competingOutlets(boolean equalHead) {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Routing original = base.routing();
        HydrologyPlannerSettings.Routing routing = new HydrologyPlannerSettings.Routing(
                original.tileSize(), original.sampleSpacing(), original.maximumRouteNodes(), 1024,
                original.minimumSurfaceCourseLength(), original.minimumUndergroundCourseLength(),
                original.valleyPreference(), original.uphillPenalty(), original.slopePenalty(),
                original.confluenceAttraction(), original.lengthPreference(), original.tributaries(), original.regional());
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(true, base.surface().sources(),
                4, 8, 2, 4, 16, base.surface().shoreWidth(), base.surface().banks());
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(base.seaLevel(), routing, surface,
                base.hydraulics(), base.underground(), base.outlets(), base.geometry(), base.deepFluids(), base.surfacePools(),
                base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
        HydrologyTerrainSampler sampler = (x, z) -> {
            int gridX = Math.floorDiv(x, 256);
            int gridZ = Math.floorDiv(z, 256);
            if (gridZ == 2 && gridX >= 0 && gridX <= 4) {
                return HydrologyTerrainSample.openLand(gridX == 4 ? 86 : 70, 0D, "land");
            }
            if (gridX == 4 && gridZ == 1) {
                return HydrologyTerrainSample.openLand(110, 0D, "land");
            }
            if (gridX == 4 && gridZ == 3) {
                return HydrologyTerrainSample.openLand(equalHead ? 86 : 100, equalHead ? 1000D : 0D, "land");
            }
            if (gridX == 4 && gridZ == 4) {
                return HydrologyTerrainSample.openLand(63, 0D, "land");
            }
            return HydrologyTerrainSample.ocean(60, "ocean");
        };
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>();
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 5; x++) {
                int index = z * 5 + x;
                nodes.add(new HydrologyGridNode(index, x, z, x * 256, z * 256, index, sampler.sample(x * 256, z * 256)));
            }
        }
        HydrologySampledGrid grid = new HydrologySampledGrid(0, 0, 0, 0, 1280, 5, 256, nodes);
        OutletCandidate low = new OutletCandidate(10, 5, new RiverOutlet(1L, HydrologyFeatureType.MOUTH, 10,
                new HydrologyPoint(0, 63, 512), new HydrologyPoint(0, 63, 511), 63, true));
        OutletCandidate high = new OutletCandidate(24, 23, new RiverOutlet(2L, HydrologyFeatureType.MOUTH, 24,
                new HydrologyPoint(1024, 63, 1024), new HydrologyPoint(1023, 63, 1024), 63, true));
        return new Fixture(new HydrologyRegionalGraph(new HydrologyPlanner(18L, settings, sampler)), grid, List.of(low, high));
    }

    private static Fixture fixture(boolean valley, boolean downhill) {
        HydrologyTerrainSampler sampler = (x, z) -> {
            int gridX = x / 256;
            int gridZ = z / 256;
            if (gridZ == 2) {
                int height = switch (gridX) {
                    case 0 -> 66;
                    case 1 -> downhill ? 66 : 100;
                    case 2 -> downhill ? 80 : 90;
                    case 3 -> downhill ? 95 : 80;
                    default -> downhill ? 110 : 70;
                };
                return HydrologyTerrainSample.openLand(height, 0D, "land");
            }
            if (valley && (gridZ == 4 || gridZ == 3 && (gridX == 0 || gridX == 4))) {
                return HydrologyTerrainSample.openLand(70, 1000D, "valley");
            }
            return HydrologyTerrainSample.ocean(60, "ocean");
        };
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>();
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 5; x++) {
                int index = z * 5 + x;
                nodes.add(new HydrologyGridNode(index, x, z, x * 256, z * 256, index, sampler.sample(x * 256, z * 256)));
            }
        }
        HydrologySampledGrid grid = new HydrologySampledGrid(0, 0, 0, 0, 1280, 5, 256, nodes);
        HydrologyPoint landward = new HydrologyPoint(0, 63, 512);
        List<OutletCandidate> outlets = List.of(new OutletCandidate(10, 5,
                new RiverOutlet(1L, HydrologyFeatureType.MOUTH, 10, landward, new HydrologyPoint(0, 63, 511), 63, true)));
        HydrologyPlanner planner = new HydrologyPlanner(18L, HydrologyRegionalPlannerTest.settings(false, 1), sampler);
        return new Fixture(new HydrologyRegionalGraph(planner), grid, outlets);
    }

    private record Fixture(HydrologyRegionalGraph graph, HydrologySampledGrid grid, List<OutletCandidate> outlets) {
    }
}
