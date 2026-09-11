package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalSkippedWaypointTest {
    @Test
    public void aSkippableHillDoesNotBlockAnEarlierValleyAcceptedByTheFullPlanner() throws Exception {
        Set<Integer> hills = Set.of(4);
        HydrologyTerrainSampler terrain = terrain(hills, true);
        HydrologyPlanner planner = planner(terrain);
        List<HydrologyPoint> guide = guide(terrain, hills);
        assertCoarseFeasible(planner, terrain, guide);
        List<HydrologyPoint> refined = new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, terrain);

        assertFalse(refined.isEmpty());
        assertFalse(refined.contains(guide.get(4)));
        Object context = context(guide, terrain);
        HydrologyRegionalRoute.Attempt selected = new HydrologyRegionalRoute(planner).select(guide, "default", false, terrain,
                candidate -> attempt(planner, context, candidate));

        assertTrue(selected.toString(), selected.accepted());
        assertEquals(1, selected.network().courses().size());
        assertTrue(selected.network().courses().getFirst().hydraulicallyNonRising());
        assertEquals(16, planner.settings.surface().maximumIncision());
    }

    @Test
    public void theTerminalHeadRemainsMandatory() {
        HydrologyTerrainSampler highTerminal = terrain(Set.of(8), false);
        HydrologyPlanner planner = planner(highTerminal);
        List<HydrologyPoint> guide = guide(highTerminal, Set.of(8));
        assertCoarseFeasible(planner, highTerminal, guide);

        assertTrue(new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, highTerminal).isEmpty());

        HydrologyTerrainSampler lowTerminal = terrain(Set.of(), false);
        assertFalse(new HydrologyRegionalTerrainRefiner(planner(lowTerminal))
                .refine(guide(lowTerminal, Set.of()), "default", false, lowTerminal).isEmpty());
    }

    @Test
    public void threeConsecutiveHighWaypointsCannotBeSkipped() {
        Set<Integer> hills = Set.of(4, 5, 6);
        HydrologyTerrainSampler terrain = terrain(hills, true);
        HydrologyPlanner planner = planner(terrain);
        List<HydrologyPoint> guide = guide(terrain, hills);
        assertCoarseFeasible(planner, terrain, guide);

        assertTrue(new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, terrain).isEmpty());

        Set<Integer> twoHills = Set.of(4, 5);
        HydrologyTerrainSampler skippable = terrain(twoHills, true);
        assertFalse(new HydrologyRegionalTerrainRefiner(planner(skippable))
                .refine(guide(skippable, twoHills), "default", false, skippable).isEmpty());
    }

    @Test
    public void aSampledRidgeCannotRaiseTheHeadAfterAnEarlierValley() {
        HydrologyTerrainSampler base = terrain(Set.of(), true);
        HydrologyTerrainSampler ridge = (x, z) -> x >= 200 && x <= 224
                ? HydrologyTerrainSample.openLand(95, 0D, "land") : base.sample(x, z);
        HydrologyPlanner planner = planner(ridge);
        List<HydrologyPoint> guide = guide(ridge, Set.of());
        assertCoarseFeasible(planner, ridge, guide);

        assertTrue(new HydrologyRegionalTerrainRefiner(planner).refine(guide, "default", false, ridge).isEmpty());
        AtomicInteger admitted = new AtomicInteger();
        HydrologyRegionalRoute.Attempt selected = new HydrologyRegionalRoute(planner).select(guide, "default", false, ridge,
                candidate -> {
                    admitted.incrementAndGet();
                    return new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null);
                });

        assertFalse(selected.accepted());
        assertEquals(0, admitted.get());
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, selected.refinement().rejection());
    }

    private static void assertCoarseFeasible(HydrologyPlanner planner, HydrologyTerrainSampler terrain, List<HydrologyPoint> guide) {
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(planner.settings);
        int available = Integer.MAX_VALUE;
        for (HydrologyPoint point : guide) {
            HydrologyTerrainSample sample = terrain.sample(point.x(), point.z());
            available = Math.min(available, hydraulics.maximumHead(sample));
            assertTrue(point.toString(), available >= hydraulics.minimumHead(sample));
        }
    }

    private static HydrologyRegionalRoute.Attempt attempt(HydrologyPlanner planner, Object context,
                                                          HydrologyRegionalRoute.Refinement candidate) {
        try {
            Method attempt = HydrologyRegionalPlanner.class.getDeclaredMethod("attempt", context.getClass(), HydrologyRegionalRoute.Refinement.class);
            attempt.setAccessible(true);
            return (HydrologyRegionalRoute.Attempt) attempt.invoke(planner.regional, context, candidate);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Object context(List<HydrologyPoint> guide, HydrologyTerrainSampler terrain) throws Exception {
        Class<?> type = Class.forName(HydrologyRegionalPlanner.class.getName() + "$BuildContext");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        int[] contributions = new int[guide.size()];
        Arrays.fill(contributions, 1);
        RiverOutlet outlet = new RiverOutlet(2L, HydrologyFeatureType.MOUTH, 8L, new HydrologyPoint(1024, 63, 0),
                new HydrologyPoint(1025, 63, 0), 63, true);
        return constructor.newInstance(new HydrologyGridNode(0, 0, 0, 0, 0, 100L, terrain.sample(0, 0)),
                null, 123L, outlet, "default", new HydrologyRegionalFlow(guide, contributions),
                HydrologyRegionalMorphology.sample(guide, terrain, HydrologyRegionalPlannerTest.settings(false, 1)));
    }

    private static List<HydrologyPoint> guide(HydrologyTerrainSampler terrain, Set<Integer> hills) {
        ArrayList<HydrologyPoint> guide = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            int x = index * 128;
            int z = index != 8 && hills.contains(index) ? 128 : 0;
            guide.add(new HydrologyPoint(x, terrain.sample(x, z).naturalHeight(), z));
        }
        return List.copyOf(guide);
    }

    private static HydrologyTerrainSampler terrain(Set<Integer> hills, boolean coast) {
        return (x, z) -> {
            if (coast && x >= 1025) {
                return HydrologyTerrainSample.ocean(60, "ocean");
            }
            int height = coast ? 80 - Math.max(0, x - 752) / 16 : 80;
            if (x >= 32 && x <= 96) {
                height = 75;
            }
            for (int hill : hills) {
                if (Math.abs(x - hill * 128) <= 24 && Math.abs(z - (hill == 8 ? 0 : 128)) <= 24) {
                    height = 93;
                }
            }
            return HydrologyTerrainSample.openLand(height, 0D, "land");
        };
    }

    private static HydrologyPlanner planner(HydrologyTerrainSampler terrain) {
        HydrologyPlannerSettings base = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyPlannerSettings.Surface surface = base.surface();
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(base.seaLevel(), base.routing(),
                new HydrologyPlannerSettings.Surface(surface.enabled(), surface.sources(), surface.minimumWidth(), surface.maximumWidth(),
                        surface.minimumDepth(), surface.maximumDepth(), 16, surface.shoreWidth(), surface.banks()),
                base.hydraulics(), base.underground(), base.outlets(), base.geometry(), base.deepFluids(), base.surfacePools(),
                base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
        HydrologyGeometrySampler geometry = request -> switch (request.field()) {
            case SURFACE_WIDTH -> 8;
            case SURFACE_DEPTH -> 2;
            default -> request.minimum();
        };
        return new HydrologyPlanner(71L, settings, terrain, geometry, -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(terrain, 63, -4096, 4096));
    }
}
