package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalCandidateSelectionTest {
    @Test
    public void lowerMeanderCandidatePassesTheFullPlannerAfterTheFirstBankCutFails() throws Exception {
        HydrologyTerrainSampler terrain = coast(true);
        HydrologyPlanner planner = planner(terrain, 16, 2);
        List<HydrologyPoint> guide = guide(terrain);
        Object context = context(guide, terrain);
        ArrayList<HydrologyRegionalRoute.Attempt> attempted = new ArrayList<>();
        HydrologyRegionalRoute.Attempt selected = new HydrologyRegionalRoute(planner).select(guide, "default", false, terrain,
                candidate -> {
                    HydrologyRegionalRoute.Attempt result = attempt(planner, context, candidate);
                    attempted.add(result);
                    return result;
                });

        assertTrue(selected.toString(), selected.accepted());
        assertEquals(2, attempted.size());
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, attempted.getFirst().rejection().rejection());
        assertEquals(18, attempted.getFirst().rejection().detail());
        assertEquals(1, selected.network().courses().size());
        assertEquals(selected, attempted.getLast());
        RiverCourse course = selected.network().courses().getFirst();
        assertTrue(course.hydraulicallyNonRising());
        assertEquals(selected.network().outlets().getFirst().id(), course.outletId().orElseThrow());
        assertFalse(selected.network().nodes().isEmpty());
        assertFalse(selected.network().edges().isEmpty());
        assertNull(selected.rejection());
    }

    @Test
    public void successfulFullAdmissionStopsAllLaterCurveEvaluation() throws Exception {
        AtomicBoolean accepted = new AtomicBoolean();
        HydrologyTerrainSampler source = coast(false);
        HydrologyTerrainSampler terrain = (x, z) -> {
            assertFalse("No later curve may sample terrain after final admission", accepted.get());
            return source.sample(x, z);
        };
        HydrologyPlanner planner = planner(terrain, 16, 2);
        List<HydrologyPoint> guide = guide(terrain);
        Object context = context(guide, terrain);
        AtomicInteger attempts = new AtomicInteger();
        HydrologyRegionalRoute.Attempt selected = new HydrologyRegionalRoute(planner).select(guide, "default", false, terrain,
                candidate -> {
                    attempts.incrementAndGet();
                    HydrologyRegionalRoute.Attempt result = attempt(planner, context, candidate);
                    accepted.set(result.accepted());
                    return result;
                });

        assertTrue(selected.toString(), selected.accepted());
        assertEquals(1, attempts.get());
    }

    @Test
    public void exhaustedCurveSelectionDeduplicatesGeometryAndKeepsItsFinalFailure() {
        HydrologyTerrainSampler terrain = coast(false);
        HydrologyPlanner planner = planner(terrain, 16, 2);
        Set<HydrologyRegionalRoute.Refinement> attempted = new HashSet<>();
        HydrologyRegionalRoute.Attempt selected = new HydrologyRegionalRoute(planner).select(guide(terrain), "default", false, terrain,
                candidate -> {
                    assertTrue("An identical candidate must not repeat final validation", attempted.add(candidate));
                    return new HydrologyRegionalRoute.Attempt(candidate, HydrologyRegionalNetwork.EMPTY,
                            new HydrologyDiagnosticCandidate(99L, HydrologyCandidateKind.REGIONAL_SOURCE,
                                    HydrologyFeatureType.SURFACE_POOL, candidate.points().getFirst(),
                                    HydrologyCandidateRejection.SURFACE_BANK_BUDGET, 7));
                });

        assertFalse(selected.accepted());
        assertTrue(attempted.size() > 1 && attempted.size() <= 18);
        assertEquals(HydrologyCandidateRejection.SURFACE_BANK_BUDGET, selected.rejection().rejection());
        assertEquals(7, selected.rejection().detail());
    }

    @Test
    public void failedSourcePublishesOneFinalDiagnosticAfterSeveralCandidateAttempts() throws Exception {
        HydrologyTerrainSampler terrain = coast(false);
        AtomicInteger sourceDepthSamples = new AtomicInteger();
        HydrologyPlannerSettings settings = settings(2);
        HydrologyGeometrySampler geometry = request -> switch (request.field()) {
            case SURFACE_WIDTH -> 8;
            case SURFACE_DEPTH -> {
                if (request.x() == 0 && request.z() == 0) {
                    sourceDepthSamples.incrementAndGet();
                }
                yield 3;
            }
            default -> request.minimum();
        };
        HydrologyPlanner planner = new HydrologyPlanner(71L, settings, terrain, geometry, -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(terrain, 63, -4096, 4096));
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>();
        for (int z = 0; z < 9; z++) {
            for (int x = 0; x < 9; x++) {
                int index = z * 9 + x;
                nodes.add(new HydrologyGridNode(index, x, z, x * 128, z * 128, index + 100L, terrain.sample(x * 128, z * 128)));
            }
        }
        HydrologySampledGrid grid = new HydrologySampledGrid(0, 0, 0, 0, 1152, 9, 128, nodes);
        HydrologyRegionalGraph.Label[] selected = new HydrologyRegionalGraph.Label[nodes.size()];
        HydrologyRegionalGraph.Label downstream = null;
        for (int index = 8; index >= 0; index--) {
            downstream = new HydrologyRegionalGraph.Label(new HydrologyRegionalGraph.LabelState(
                    index, index, 63, (8 - index) * 128D, (8 - index) * 128D, 0), downstream);
            selected[index] = downstream;
        }
        int[] contributions = new int[9];
        Arrays.fill(contributions, 1);
        HydrologyRegionalGraph.Tree tree = new HydrologyRegionalGraph.Tree(selected, contributions,
                List.of(new OutletCandidate(8, 8, outlet())));
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        Method build = HydrologyRegionalPlanner.class.getDeclaredMethod("build", HydrologySampledGrid.class,
                HydrologyRegionalGraph.Tree.class, int.class, OutletCandidate.class, long.class, List.class);
        build.setAccessible(true);
        HydrologyRegionalNetwork network = (HydrologyRegionalNetwork) build.invoke(planner.regional, grid, tree, 0, null, 123L, diagnostics);

        assertTrue(network.courses().isEmpty());
        assertEquals(1, diagnostics.size());
        assertEquals(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, diagnostics.getFirst().rejection());
        assertTrue(sourceDepthSamples.get() > 1);
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
        return constructor.newInstance(new HydrologyGridNode(0, 0, 0, 0, 0, 100L, terrain.sample(0, 0)),
                null, 123L, outlet(), "default", new HydrologyRegionalFlow(guide, contributions),
                HydrologyRegionalMorphology.sample(guide, terrain, settings(16)));
    }

    private static RiverOutlet outlet() {
        return new RiverOutlet(2L, HydrologyFeatureType.MOUTH, 8L, new HydrologyPoint(1024, 63, 0),
                new HydrologyPoint(1025, 63, 0), 63, true);
    }

    private static List<HydrologyPoint> guide(HydrologyTerrainSampler terrain) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (int x = 0; x <= 1024; x += 128) {
            points.add(new HydrologyPoint(x, terrain.sample(x, 0).naturalHeight(), 0));
        }
        return List.copyOf(points);
    }

    private static HydrologyTerrainSampler coast(boolean lowBank) {
        return (x, z) -> x >= 1025 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(lowBank && x == 288 && z == 19 ? 64
                : 80 - Math.max(0, x - 752) / 16, 0D, "land");
    }

    private static HydrologyPlanner planner(HydrologyTerrainSampler terrain, int incision, int depth) {
        HydrologyPlannerSettings settings = settings(incision);
        HydrologyGeometrySampler geometry = request -> switch (request.field()) {
            case SURFACE_WIDTH -> 8;
            case SURFACE_DEPTH -> depth;
            default -> request.minimum();
        };
        return new HydrologyPlanner(71L, settings, terrain, geometry, -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(terrain, 63, -4096, 4096));
    }

    private static HydrologyPlannerSettings settings(int incision) {
        HydrologyPlannerSettings base = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyPlannerSettings.Surface surface = base.surface();
        return new HydrologyPlannerSettings(base.seaLevel(), base.routing(),
                new HydrologyPlannerSettings.Surface(surface.enabled(), surface.sources(), surface.minimumWidth(), surface.maximumWidth(),
                        surface.minimumDepth(), surface.maximumDepth(), incision, surface.shoreWidth(), surface.banks()),
                base.hydraulics(), base.underground(), base.outlets(), base.geometry(), base.deepFluids(), base.surfacePools(),
                base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
    }
}
