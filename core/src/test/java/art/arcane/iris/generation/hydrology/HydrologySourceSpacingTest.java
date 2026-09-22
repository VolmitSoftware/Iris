package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HydrologySourceSpacingTest {
    private static final HydrologyTileKey TILE = new HydrologyTileKey(0, 0);
    private static final int WIDTH = 11;
    private static final int SPACING = 64;
    /** One lattice step (64, or 91 diagonally) is inside the spacing; two steps (128) are outside it. */
    private static final int MINIMUM_SPACING = 100;
    private static final int HIGH = 200;
    private static final int ROUTE = 512;
    private static final int LONG_ROUTE = 4096;

    @Test
    public void aRejectedSourceNoLongerCostsItsLowerRankedNeighbourTheSlot() {
        // A > B > C by weight, A one step from B, B one step from C, A two steps from C.
        Field withMiddle = field(Map.of(pack(3, 4), 3D, pack(4, 4), 2D, pack(5, 4), 1D));
        assertTrue(withMiddle.admits(3, 4));
        assertFalse(withMiddle.admits(4, 4));
        assertTrue(withMiddle.admits(5, 4));

        Field withoutMiddle = field(Map.of(pack(3, 4), 3D, pack(5, 4), 1D));
        assertEquals(withoutMiddle.accepted(), withMiddle.accepted());
    }

    /**
     * Route length and potential belong to the routing plan of the tile that owns a node, so a contest scored with
     * them ranks neighbours either side of a tile seam against different outlet sets and hands the slot to whoever
     * owns the node. The contest is terrain only, and must not move when the plan under it does.
     */
    @Test
    public void theSpacingContestIgnoresTheOwnerTileRoutingPlan() {
        int[] pair = hashInvertedPair(planner());
        long left = pack(pair[0], pair[1]);
        long right = pack(pair[0] + 1, pair[1]);
        Map<Long, Double> weights = Map.of(left, 1D, right, 1D);

        Set<Long> terrainOnly = field(weights).accepted();
        assertEquals(Set.of(left), terrainOnly);
        assertEquals(terrainOnly, field(weights, Map.of(right, LONG_ROUTE)).accepted());
        assertEquals(terrainOnly, field(weights, Map.of(left, LONG_ROUTE)).accepted());
    }

    @Test
    public void greedyAdmissionIsDeterministicSeparatedAndMaximal() {
        Set<Long> first = denseField().accepted();
        Set<Long> second = denseField().accepted();
        assertEquals(first, second);
        assertFalse(first.isEmpty());

        for (long accepted : first) {
            for (long other : first) {
                if (accepted != other) {
                    assertTrue("accepted sources " + accepted + " and " + other + " sit inside the spacing",
                            separation(accepted, other) >= MINIMUM_SPACING);
                }
            }
        }
        for (int gridZ = 1; gridZ < WIDTH - 1; gridZ++) {
            for (int gridX = 1; gridX < WIDTH - 1; gridX++) {
                long node = pack(gridX, gridZ);
                if (first.contains(node)) {
                    continue;
                }
                boolean blocked = false;
                for (long accepted : first) {
                    blocked |= separation(node, accepted) < MINIMUM_SPACING;
                }
                assertTrue("rejected source " + node + " is separated from every accepted source", blocked);
            }
        }
    }

    @Test
    public void lowerPriorityHaloSamplesDoNotBuildNeighbourOwnerRouting() {
        HydrologyPlanner planner = planner();
        HydrologySampledGrid grid = haloGrid(3D, 1D);
        HydrologyRoutingPlan routing = routing(grid, Map.of());
        HashMap<HydrologyTileKey, SourceRoutingContext> contexts = new HashMap<>();
        contexts.put(TILE, new SourceRoutingContext(grid, routing, routing, List.of()));

        assertTrue(planner.sourcePlanner.globallyAdmittedSource(grid.nodeAt(5, 5),
                planner.settings.surface().sources(), HydrologySourcePlanner.SURFACE_SOURCE_SALT,
                true, contexts, new HashMap<>()));

        assertEquals(Set.of(TILE), contexts.keySet());
        assertEquals(0L, planner.routingContexts.estimatedSize());
    }

    @Test
    public void higherPriorityHaloSourcesStillDemandTheirOwnerRouting() {
        HydrologyPlanner planner = planner();
        HydrologySampledGrid grid = haloGrid(1D, 3D);
        HydrologyRoutingPlan routing = routing(grid, Map.of());
        SourceRoutingContext context = new SourceRoutingContext(grid, routing, routing, List.of());
        HydrologyTileKey westernOwner = new HydrologyTileKey(-1, 0);
        planner.routingContexts.put(westernOwner, context);
        HashMap<HydrologyTileKey, SourceRoutingContext> contexts = new HashMap<>();
        contexts.put(TILE, context);

        assertFalse(planner.sourcePlanner.globallyAdmittedSource(grid.nodeAt(5, 5),
                planner.settings.surface().sources(), HydrologySourcePlanner.SURFACE_SOURCE_SALT,
                true, contexts, new HashMap<>()));

        assertEquals(Set.of(TILE, westernOwner), contexts.keySet());
    }

    @Test
    public void requestDependentSamplersAlwaysUseTheOwnerGridForSourcePriority() {
        HydrologyRoutingTerrainSampler routingSampler = new HydrologyRoutingTerrainSampler() {
            @Override
            public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
                throw new AssertionError("The owner grids are already cached");
            }

            @Override
            public NaturalClassification classifyNatural(int x, int z) {
                throw new AssertionError("Source spacing uses sampled routing grids");
            }
        };
        HydrologyPlannerSettings settings = planner().settings;
        HydrologyTerrainSampler terrain = (x, z) -> land(HIGH, 0D);
        HydrologyPlanner planner = new HydrologyPlanner(1337L, settings, terrain, routingSampler,
                HydrologyGeometrySampler.deterministic(terrain), -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(terrain, settings.seaLevel(), -4096, 4096));
        HydrologySampledGrid primaryGrid = haloGrid(1D, 0D);
        HydrologySampledGrid ownerGrid = haloGrid(1D, 3D);
        HydrologyRoutingPlan primaryRouting = routing(primaryGrid, Map.of());
        HydrologyRoutingPlan ownerRouting = routing(ownerGrid, Map.of());
        SourceRoutingContext primaryContext = new SourceRoutingContext(primaryGrid, primaryRouting, primaryRouting, List.of());
        SourceRoutingContext ownerContext = new SourceRoutingContext(ownerGrid, ownerRouting, ownerRouting, List.of());
        for (HydrologyTileKey key : List.of(new HydrologyTileKey(-1, -1), new HydrologyTileKey(-1, 0),
                new HydrologyTileKey(0, -1))) {
            planner.routingContexts.put(key, ownerContext);
        }
        HashMap<HydrologyTileKey, SourceRoutingContext> contexts = new HashMap<>();
        contexts.put(TILE, primaryContext);

        assertFalse(routingSampler.supportsSharedGridSamples());
        assertFalse(planner.sourcePlanner.globallyAdmittedSource(primaryGrid.nodeAt(5, 5),
                settings.surface().sources(), HydrologySourcePlanner.SURFACE_SOURCE_SALT,
                true, contexts, new HashMap<>()));
        assertTrue(contexts.containsKey(new HydrologyTileKey(-1, 0)));
    }

    private static HydrologySampledGrid haloGrid(double centerWeight, double westernWeight) {
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>(WIDTH * WIDTH);
        for (int gridZ = 0; gridZ < WIDTH; gridZ++) {
            for (int gridX = 0; gridX < WIDTH; gridX++) {
                int index = gridZ * WIDTH + gridX;
                int x = (gridX - 5) * SPACING;
                int z = (gridZ - 5) * SPACING;
                double weight = z != 0 ? 0D : x == 0 ? centerWeight : x == -SPACING ? westernWeight : 0D;
                nodes.add(new HydrologyGridNode(index, gridX, gridZ, x, z, index + 1L, land(HIGH, weight)));
            }
        }
        return new HydrologySampledGrid(-5 * SPACING, -5 * SPACING, 0, 0,
                HydrologyPlannerSettings.defaults().routing().tileSize(), WIDTH, SPACING, List.copyOf(nodes));
    }

    private static Field denseField() {
        HashMap<Long, Double> weights = new HashMap<>();
        for (int gridZ = 1; gridZ < WIDTH - 1; gridZ++) {
            for (int gridX = 1; gridX < WIDTH - 1; gridX++) {
                weights.put(pack(gridX, gridZ), 1D + (gridX * 7 + gridZ * 13) % 5);
            }
        }
        return field(weights);
    }

    /** The first adjacent pair whose raw source hashes rank the left node above the right one. */
    private static int[] hashInvertedPair(HydrologyPlanner planner) {
        for (int gridZ = 1; gridZ < WIDTH - 1; gridZ++) {
            for (int gridX = 1; gridX < WIDTH - 2; gridX++) {
                long left = planner.sourcePlanner.sourceStableId(HydrologySourcePlanner.SURFACE_SOURCE_SALT,
                        gridX * SPACING, gridZ * SPACING);
                long right = planner.sourcePlanner.sourceStableId(HydrologySourcePlanner.SURFACE_SOURCE_SALT,
                        (gridX + 1) * SPACING, gridZ * SPACING);
                if (Long.compareUnsigned(left, right) > 0) {
                    return new int[]{gridX, gridZ};
                }
            }
        }
        fail("No hash-inverted adjacent source pair exists on this lattice");
        return null;
    }

    private static double separation(long first, long second) {
        return StrictMath.hypot((double) (unpackX(first) - unpackX(second)) * SPACING,
                (double) (unpackZ(first) - unpackZ(second)) * SPACING);
    }

    private static Field field(Map<Long, Double> weights) {
        return field(weights, Map.of());
    }

    private static Field field(Map<Long, Double> weights, Map<Long, Integer> routeLengths) {
        HydrologyPlanner planner = planner();
        HydrologySampledGrid grid = grid(weights);
        HydrologyRoutingPlan routing = routing(grid, routeLengths);
        HashMap<HydrologyTileKey, SourceRoutingContext> contexts = new HashMap<>();
        contexts.put(TILE, new SourceRoutingContext(grid, routing, routing, List.of()));
        return new Field(planner, grid, weights.keySet(), contexts, new HashMap<>());
    }

    private static HydrologyPlanner planner() {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(true,
                new HydrologyPlannerSettings.Source(true, 0.5D, 88, 0, 1, MINIMUM_SPACING),
                4, 8, 2, 4, 10, 1.5D, HydrologyPlannerSettings.Banks.defaults());
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(base.seaLevel(), base.routing(), surface,
                base.hydraulics(), base.underground(), base.outlets(), base.geometry(), base.deepFluids(),
                base.surfacePools(), base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
        return new HydrologyPlanner(1337L, settings, (x, z) -> land(HIGH, 0D));
    }

    private static HydrologySampledGrid grid(Map<Long, Double> weights) {
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>(WIDTH * WIDTH);
        for (int gridZ = 0; gridZ < WIDTH; gridZ++) {
            for (int gridX = 0; gridX < WIDTH; gridX++) {
                int index = gridZ * WIDTH + gridX;
                long node = pack(gridX, gridZ);
                nodes.add(new HydrologyGridNode(index, gridX, gridZ, gridX * SPACING, gridZ * SPACING, index + 1L,
                        land(HIGH, weights.getOrDefault(node, 0D))));
            }
        }
        return new HydrologySampledGrid(0, 0, 0, 0, WIDTH * SPACING, WIDTH, SPACING, List.copyOf(nodes));
    }

    private static HydrologyRoutingPlan routing(HydrologySampledGrid grid, Map<Long, Integer> routeLengths) {
        int count = grid.nodes().size();
        double[] potential = new double[count];
        int[] parent = new int[count];
        int[] outlets = new int[count];
        int[] lengths = new int[count];
        Arrays.fill(potential, 100D);
        Arrays.fill(parent, 0);
        ArrayList<OutletCandidate> candidates = new ArrayList<>(count);
        for (HydrologyGridNode node : grid.nodes()) {
            outlets[node.index()] = node.index();
            lengths[node.index()] = routeLengths.getOrDefault(pack(node.gridX(), node.gridZ()), ROUTE);
            HydrologyPoint point = new HydrologyPoint(node.x(), 63, node.z());
            candidates.add(new OutletCandidate(node.index(), -1, new RiverOutlet(node.index() + 1000L,
                    HydrologyFeatureType.MOUTH, node.id(), point, point, 63, true)));
        }
        return new HydrologyRoutingPlan(potential, parent, outlets, lengths, List.copyOf(candidates), false, null);
    }

    private static HydrologyTerrainSample land(int naturalHeight, double weight) {
        return new HydrologyTerrainSample(naturalHeight, 0D, false, false, naturalHeight - 32, naturalHeight - 30,
                true, true, true, false, false, false,
                0D, weight, 0D, 1D, 1D, 1D, 1D, 1D, "land", "land", "land", "land", "land", "land",
                List.of("default"), List.of(), Double.NaN, null, Double.NaN, true, SurfaceRiverPolicy.INHERIT);
    }

    private static long pack(int gridX, int gridZ) {
        return RiverFootprint.pack(gridX, gridZ);
    }

    private static int unpackX(long packed) {
        return RiverFootprint.unpackX(packed);
    }

    private static int unpackZ(long packed) {
        return RiverFootprint.unpackZ(packed);
    }

    private record Field(
            HydrologyPlanner planner,
            HydrologySampledGrid grid,
            Set<Long> eligible,
            Map<HydrologyTileKey, SourceRoutingContext> contexts,
            Map<Long, Boolean> admissions
    ) {
        boolean admits(int gridX, int gridZ) {
            return planner.sourcePlanner.globallyAdmittedSource(
                    grid.nodeAt(gridX, gridZ),
                    planner.settings.surface().sources(),
                    HydrologySourcePlanner.SURFACE_SOURCE_SALT,
                    true,
                    contexts,
                    admissions
            );
        }

        Set<Long> accepted() {
            LinkedHashSet<Long> selected = new LinkedHashSet<>();
            for (long node : new TreeSet<>(eligible)) {
                if (admits(unpackX(node), unpackZ(node))) {
                    selected.add(node);
                }
            }
            return selected;
        }
    }
}
