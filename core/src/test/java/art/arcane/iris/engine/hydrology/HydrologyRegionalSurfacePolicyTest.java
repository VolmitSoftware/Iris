package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalSurfacePolicyTest {
    private static final HydrologyTileKey TILE = new HydrologyTileKey(0, 0);
    private static final SurfaceRiverPolicy TROPICAL = new SurfaceRiverPolicy("region:tropical", 8D, 160, 0, 3, 4, null, null);

    @Test
    public void sourcesAndOutletsUseOnlyTheirOwnedEligibleArea() {
        HydrologySampledGrid grid = grid((x, z) -> land(x < 512 ? TROPICAL : SurfaceRiverPolicy.INHERIT, false));
        HydrologyPlanner planner = planner(1337L, HydrologyPlannerSettings.defaults());
        HydrologySurfaceBudgets budgets = HydrologySurfaceBudgets.sample(grid, planner.settings.surface().sources());

        assertEquals(4, budgets.sourceTarget(budgets.area(TROPICAL), 0, planner, TILE));
        assertEquals(2, budgets.outletTarget(budgets.area(TROPICAL), true, planner, TILE));
        int inland = budgets.outletTarget(budgets.area(TROPICAL), false, planner, TILE);
        assertTrue(inland == 1 || inland == 2);
        assertTrue(budgets.sourceTarget(budgets.area(SurfaceRiverPolicy.INHERIT), 0, planner, TILE) <= 1);

        HydrologySampledGrid tiny = grid((x, z) -> land(x == 0 && z == 0 ? TROPICAL : SurfaceRiverPolicy.INHERIT, false));
        HydrologySurfaceBudgets tinyBudgets = HydrologySurfaceBudgets.sample(tiny, planner.settings.surface().sources());
        int selected = 0;
        for (int seed = 0; seed < 256; seed++) {
            int target = tinyBudgets.sourceTarget(tinyBudgets.area(TROPICAL), 0,
                    planner(seed, planner.settings), TILE);
            assertTrue(target <= 1);
            selected += target;
        }
        assertTrue("tiny areas acquired a per-tile minimum: " + selected, selected > 0 && selected < 32);
    }

    @Test
    public void geometrySubareasShareSourceAndOutletBudgets() {
        SurfaceRiverPolicy narrow = new SurfaceRiverPolicy("region:tropical", 8D, 160, 0, 3, 4, 128, 24);
        HydrologySampledGrid mixed = grid((x, z) -> land(x < 512 ? TROPICAL : narrow, false));
        HydrologySampledGrid uniform = grid((x, z) -> land(TROPICAL, false));
        HydrologyPlanner planner = planner(1337L, HydrologyPlannerSettings.defaults());
        HydrologySurfaceBudgets mixedBudgets = HydrologySurfaceBudgets.sample(mixed, planner.settings.surface().sources());
        HydrologySurfaceBudgets uniformBudgets = HydrologySurfaceBudgets.sample(uniform, planner.settings.surface().sources());

        assertEquals(1, mixedBudgets.areas().size());
        assertEquals(mixedBudgets.area(TROPICAL), mixedBudgets.area(narrow));
        assertEquals(8, mixedBudgets.sourceTarget(mixedBudgets.area(narrow), 0, planner, TILE));
        assertEquals(uniformBudgets.outletTarget(uniformBudgets.area(TROPICAL), true, planner, TILE),
                mixedBudgets.outletTarget(mixedBudgets.area(narrow), true, planner, TILE));
        assertEquals(select(planner, uniform, routing(uniform, false), true).selectedNodeIndices(),
                select(planner, mixed, routing(mixed, false), true).selectedNodeIndices());
        assertEquals(planner.outletPlanner.limitSurfaceOutlets(TILE, uniform, routing(uniform, false).outlets(), true),
                planner.outletPlanner.limitSurfaceOutlets(TILE, mixed, routing(mixed, false).outlets(), true));
    }

    @Test
    public void aRegionCannotSpendAnotherRegionsUnusedSourceBudget() {
        HydrologySampledGrid grid = grid((x, z) -> land(x < 512 ? TROPICAL : SurfaceRiverPolicy.INHERIT, false));
        HydrologyPlanner planner = planner(1337L, HydrologyPlannerSettings.defaults());
        HydrologyRoutingPlan routing = routing(grid, false);
        SourceSelection both = select(planner, grid, routing, true);
        assertEquals(4, selectedIn(both, grid, TROPICAL));
        assertTrue(selectedIn(both, grid, SurfaceRiverPolicy.INHERIT) <= 1);

        for (HydrologyGridNode node : grid.nodes()) {
            if (node.terrain().surfacePolicy().equals(TROPICAL)) {
                routing.potential()[node.index()] = Double.POSITIVE_INFINITY;
            }
        }
        SourceSelection onlyOrdinary = select(planner, grid, routing, true);
        assertEquals(0, selectedIn(onlyOrdinary, grid, TROPICAL));
        assertTrue(onlyOrdinary.selectedNodeIndices().size() <= 1);
        assertEquals(onlyOrdinary.selectedNodeIndices(), select(planner(1337L, planner.settings), grid, routing, true).selectedNodeIndices());
    }

    @Test
    public void localTributaryCapsApplyToAdmissionAndReplacement() {
        SurfaceRiverPolicy branching = new SurfaceRiverPolicy("region:tropical", 8D, 160, 3, 3, 4, null, null);
        HydrologySampledGrid grid = grid((x, z) -> land(branching, false));
        HydrologyPlanner planner = planner(1337L, HydrologyPlannerSettings.defaults());
        SourceSelection selected = select(planner, grid, routing(grid, true), true);
        assertEquals(4, selected.selectedNodeIndices().size());
        Set<Integer> original = new HashSet<>(selected.selectedNodeIndices());

        assertTrue(selected.advanceAfterPublication(List.of(), grid));
        assertEquals(4, selected.selectedNodeIndices().size());
        assertTrue(selected.selectedNodeIndices().stream().noneMatch(original::contains));

        HydrologySampledGrid unbranched = grid((x, z) -> land(TROPICAL, false));
        assertEquals(1, select(planner, unbranched, routing(unbranched, true), true).selectedNodeIndices().size());
    }

    @Test
    public void requiredHeadwatersKeepTheirOwnMinimumWhenExpectedDensityIsZero() {
        SurfaceRiverPolicy requiredArea = new SurfaceRiverPolicy("biome:headwater", null, 160, null, null, null, null, null);
        SurfaceRiverPolicy disabledArea = new SurfaceRiverPolicy("region:disabled", 0D, 160, null, null, null, null, null);
        HydrologySampledGrid grid = grid((x, z) -> land(x == 0 && z < 256 ? requiredArea : disabledArea, true));
        HydrologyPlanner planner = planner(7L, withSources(new HydrologyPlannerSettings.Source(true, 0D, 88, 2, 2, 384)));
        SourceSelection selected = select(planner, grid, routing(grid, false), true);
        assertEquals(2, selected.selectedNodeIndices().size());
        assertEquals(2, selectedIn(selected, grid, requiredArea));
        Set<Integer> original = new HashSet<>(selected.selectedNodeIndices());

        assertTrue(selected.advanceAfterPublication(List.of(), grid));
        assertEquals(2, selectedIn(selected, grid, requiredArea));
        assertTrue(selected.selectedNodeIndices().stream().noneMatch(original::contains));
        assertEquals(0, selectedIn(selected, grid, disabledArea));

        HydrologySampledGrid inherited = grid((x, z) -> land(x == 0 && z < 256 ? SurfaceRiverPolicy.INHERIT : disabledArea, true));
        assertEquals(2, selectedIn(select(planner, inherited, routing(inherited, false), true),
                inherited, SurfaceRiverPolicy.INHERIT));
    }

    @Test
    public void localSurfaceControlsDoNotAlterUndergroundAdmission() {
        HydrologyPlanner planner = planner(1337L, HydrologyPlannerSettings.defaults());
        HydrologySampledGrid ordinary = grid((x, z) -> land(SurfaceRiverPolicy.INHERIT, false));
        HydrologySampledGrid tropical = grid((x, z) -> land(TROPICAL, false));

        assertEquals(select(planner, ordinary, routing(ordinary, true), false).selectedNodeIndices(),
                select(planner, tropical, routing(tropical, true), false).selectedNodeIndices());
    }

    @Test
    public void coastalBudgetsStayLocalAndCanBeExplicitlyDisabled() {
        SurfaceRiverPolicy volcanic = new SurfaceRiverPolicy("biome:volcanic", 6D, 128, 2, 3, 0, null, null);
        HydrologySampledGrid grid = grid((x, z) -> land(x < 512 ? TROPICAL : volcanic, false));
        HydrologyPlanner planner = planner(1337L, HydrologyPlannerSettings.defaults());
        List<OutletCandidate> candidates = routing(grid, false).outlets();
        List<OutletCandidate> selected = planner.outletPlanner.limitSurfaceOutlets(TILE, grid, candidates, true);

        assertEquals(2, selected.size());
        for (OutletCandidate outlet : selected) {
            assertEquals(TROPICAL, grid.node(outlet.landIndex()).terrain().surfacePolicy());
        }
        assertEquals(selected, planner.outletPlanner.limitSurfaceOutlets(TILE, grid, candidates, true));
    }

    @Test
    public void disabledLocalCoastTargetsDoNotSuppressInlandOutlets() {
        SurfaceRiverPolicy inlandOnly = new SurfaceRiverPolicy("region:inland", 8D, 160, 3, 3, 0, null, null);
        HydrologyTerrainSampler localTerrain = (x, z) -> x >= 512
                ? HydrologyTerrainSample.ocean(50, "ocean") : land(inlandOnly, false);
        HydrologyTerrainSampler ordinaryTerrain = (x, z) -> x >= 512
                ? HydrologyTerrainSample.ocean(50, "ocean") : land(SurfaceRiverPolicy.INHERIT, false);
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Outlets outlets = base.outlets();
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(base.seaLevel(), base.routing(), base.surface(),
                base.hydraulics(), base.underground(), HydrologyPlannerSettings.Outlets.of(outlets.oceanEnabled(),
                outlets.coastalGrotto(), outlets.inlandGrotto(), true, outlets.coastalCliffMinimumHeight(),
                outlets.mouthLevelingDistance(), outlets.maximumOceanApron(), outlets.maximumPerTile(),
                outlets.maximumCoastalPerTile()), base.geometry(), base.deepFluids(), base.surfacePools(),
                base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
        HydrologyPlanner local = new HydrologyPlanner(1337L, settings, localTerrain);
        HydrologyPlanner ordinary = new HydrologyPlanner(1337L, settings, ordinaryTerrain);

        List<OutletCandidate> localOutlets = local.outletPlanner.resolveOutlets(TILE, grid(localTerrain), true, new ArrayList<>());
        assertTrue(localOutlets.toString(), !localOutlets.isEmpty());
        for (OutletCandidate candidate : localOutlets) {
            assertEquals(HydrologyFeatureType.INLAND_GROTTO, candidate.outlet().type());
        }
        List<OutletCandidate> ordinaryOutlets = ordinary.outletPlanner.resolveOutlets(TILE, grid(ordinaryTerrain), true, new ArrayList<>());
        assertTrue(ordinaryOutlets.toString(), !ordinaryOutlets.isEmpty());
        for (OutletCandidate candidate : ordinaryOutlets) {
            assertTrue(candidate.outlet().type() != HydrologyFeatureType.INLAND_GROTTO);
        }
        assertEquals(ordinary.outletPlanner.resolveOutlets(TILE, grid(ordinaryTerrain), false, new ArrayList<>()),
                local.outletPlanner.resolveOutlets(TILE, grid(localTerrain), false, new ArrayList<>()));
    }

    private static SourceSelection select(HydrologyPlanner planner, HydrologySampledGrid grid,
                                          HydrologyRoutingPlan routing, boolean surface) {
        return planner.sourcePlanner.selectSources(TILE, grid, routing, surface, false, new ArrayList<>(), new HashMap<>());
    }

    private static int selectedIn(SourceSelection selection, HydrologySampledGrid grid, SurfaceRiverPolicy policy) {
        int count = 0;
        for (int index : selection.selectedNodeIndices()) {
            if (grid.node(index).terrain().surfacePolicy().equals(policy)) {
                count++;
            }
        }
        return count;
    }

    private static HydrologyPlanner planner(long seed, HydrologyPlannerSettings settings) {
        return new HydrologyPlanner(seed, settings, (x, z) -> land(SurfaceRiverPolicy.INHERIT, false));
    }

    private static HydrologySampledGrid grid(HydrologyTerrainSampler terrain) {
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>(256);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int index = z * 16 + x;
                nodes.add(new HydrologyGridNode(index, x, z, x * 64, z * 64, index + 1L, terrain.sample(x * 64, z * 64)));
            }
        }
        return new HydrologySampledGrid(0, 0, 0, 0, 1024, 16, 64, List.copyOf(nodes));
    }

    private static HydrologyRoutingPlan routing(HydrologySampledGrid grid, boolean sharedOutlet) {
        int count = grid.nodes().size();
        double[] potential = new double[count];
        int[] parent = new int[count];
        int[] outlets = new int[count];
        int[] lengths = new int[count];
        Arrays.fill(potential, 100D);
        Arrays.fill(parent, 0);
        Arrays.fill(lengths, 512);
        ArrayList<OutletCandidate> candidates = new ArrayList<>(count);
        for (HydrologyGridNode node : grid.nodes()) {
            outlets[node.index()] = sharedOutlet ? 0 : node.index();
            HydrologyPoint point = new HydrologyPoint(node.x(), 63, node.z());
            candidates.add(new OutletCandidate(node.index(), -1, new RiverOutlet(node.index() + 1000L,
                    HydrologyFeatureType.MOUTH, node.id(), point, point, 63, true)));
        }
        return new HydrologyRoutingPlan(potential, parent, outlets, lengths, List.copyOf(candidates), false);
    }

    private static HydrologyPlannerSettings withSources(HydrologyPlannerSettings.Source sources) {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(true, sources, 4, 8, 2, 4, 10,
                1.5D, HydrologyPlannerSettings.Banks.defaults());
        return new HydrologyPlannerSettings(base.seaLevel(), base.routing(), surface, base.hydraulics(), base.underground(),
                base.outlets(), base.geometry(), base.deepFluids(), base.surfacePools(), base.widestShoreBiomeWidth(),
                base.seaCaves(), base.surfacePolicyBounds());
    }

    private static HydrologyTerrainSample land(SurfaceRiverPolicy policy, boolean required) {
        return new HydrologyTerrainSample(200, 0D, false, true, 160, 162, true, true, true, required, true, false,
                0D, 1D, 1D, 1D, 1D, 1D, 1D, 1D, "land", "land", "land", "land", "land", "land",
                List.of("default"), List.of(), Double.NaN, null, Double.NaN, true, policy);
    }
}
