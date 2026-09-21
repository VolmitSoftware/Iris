package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HydrologyRegionalSourceParallelismTest {
    @Test(timeout = 15000)
    public void boundedExecutionPreservesTheWholePreselectedSourceWindow() throws Exception {
        int maximumTrials = HydrologyPlanningAdmission.maximumTrials();
        int requiredParallelism = Math.min(4, maximumTrials);
        CountDownLatch firstBatch = new CountDownLatch(requiredParallelism);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        Set<Integer> visited = ConcurrentHashMap.newKeySet();
        Fixture fixture = fixture(4, source -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            visited.add(source);
            firstBatch.countDown();
            try {
                await(firstBatch);
                return source == 0 || source == 4 ? accepted(source) : rejected(source);
            } finally {
                active.decrementAndGet();
            }
        });
        HydrologyRegionalNetwork network = run(fixture);

        assertEquals(List.of(100L, 104L), network.courses().stream().map(RiverCourse::id).toList());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7), visited);
        assertTrue(peak.get() >= requiredParallelism);
        assertTrue(peak.get() <= maximumTrials);
        assertEquals(0, active.get());
    }

    @Test(timeout = 15000)
    public void acceptsOutletsInSourceOrderWhenTheMiddleOfAWindowSucceeds() throws Exception {
        Fixture fixture = fixture(4, source -> source == 4 || source == 1 ? accepted(source) : rejected(source));
        HydrologyRegionalNetwork network = run(fixture);

        assertEquals(List.of(104L, 101L), network.courses().stream().map(RiverCourse::id).toList());
        assertEquals(List.of(0L), network.diagnostics().stream().map(HydrologyDiagnosticCandidate::id).toList());
    }

    @Test(timeout = 15000)
    public void skipsFailureFromAnOutletAlreadyAcceptedAndDrainsItsStartedWork() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        Fixture fixture = fixture(4, source -> {
            if (source == 5) {
                blocked.countDown();
                await(release);
                completed.set(true);
                throw new IllegalStateException("Unused outlet provider failed");
            }
            return source == 4 ? accepted(source) : rejected(source);
        });
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            Future<HydrologyRegionalNetwork> pending = pool.submit(() -> fixture.planner().regional.draft(new HydrologyTileKey(0, 0)));
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            assertFalse(pending.isDone());
            release.countDown();
            HydrologyRegionalNetwork network = pending.get(5, TimeUnit.SECONDS);

            assertTrue(completed.get());
            assertEquals(List.of(104L), network.courses().stream().map(RiverCourse::id).toList());
            assertEquals(List.of(0L, 1L, 2L, 3L), network.diagnostics().stream().map(HydrologyDiagnosticCandidate::id).toList());
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 15000)
    public void acceptedOutletSkipsLeaveExactlyTwentyFourActualSourceTrials() throws Exception {
        AtomicInteger evaluations = new AtomicInteger();
        Fixture fixture = fixture(32, source -> {
            evaluations.incrementAndGet();
            return source == 0 ? accepted(source) : rejected(source);
        });
        HydrologyRegionalNetwork network = run(fixture);

        assertEquals(List.of(100L), network.courses().stream().map(RiverCourse::id).toList());
        assertEquals(23, network.diagnostics().size());
        assertEquals(23L, network.diagnostics().stream().map(HydrologyDiagnosticCandidate::id).distinct().count());
        assertTrue(network.diagnostics().stream().allMatch(diagnostic -> diagnostic.id() >= 4L));
        assertTrue("The fixture must include work skipped after outlet acceptance", evaluations.get() > 24);
    }

    @Test(timeout = 15000)
    public void requiredSourceFailureWaitsForAllStartedTrialProviders() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        IllegalStateException expected = new IllegalStateException("Required source provider failed");
        Fixture fixture = fixture(4, source -> {
            if (source == 1) {
                failed.countDown();
                throw expected;
            }
            if (source == 5) {
                blocked.countDown();
                await(release);
                completed.set(true);
            }
            return rejected(source);
        });
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            Future<HydrologyRegionalNetwork> pending = pool.submit(() -> fixture.planner().regional.draft(new HydrologyTileKey(0, 0)));
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            assertFalse(pending.isDone());
            release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
            Throwable cause = failure.getCause();
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertSame(expected, cause);
            assertTrue(completed.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static HydrologyRegionalNetwork run(Fixture fixture) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            return pool.submit(() -> fixture.planner().regional.draft(new HydrologyTileKey(0, 0))).get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static Fixture fixture(int secondCatchmentSize, SourceProvider provider) throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(70, 0D, "land");
        HydrologyPlannerSettings base = HydrologyRegionalPlannerTest.settings(false, 1);
        HydrologyPlannerSettings.Routing routing = base.routing();
        HydrologyPlannerSettings.Regional regional = routing.regional();
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(base.seaLevel(),
                new HydrologyPlannerSettings.Routing(routing.tileSize(), routing.sampleSpacing(), routing.maximumRouteNodes(),
                        routing.maximumRouteLength(), routing.minimumSurfaceCourseLength(), routing.minimumUndergroundCourseLength(),
                        routing.valleyPreference(), routing.uphillPenalty(), routing.slopePenalty(), routing.confluenceAttraction(),
                        routing.lengthPreference(), routing.tributaries(), new HydrologyPlannerSettings.Regional(true,
                        regional.sampleSpacing(), regional.minimumLength(), 2, regional.maximumCachedBasins(),
                        regional.maximumCachedStations(), false, regional.coastalChannelChance(), regional.maximumCoastalIncision())),
                base.surface(), base.hydraulics(), base.underground(), base.outlets(), base.geometry(), base.deepFluids(),
                base.surfacePools(), base.widestShoreBiomeWidth(), base.seaCaves(), base.surfacePolicyBounds());
        HydrologyPlanner planner = new HydrologyPlanner(71L, settings, terrain);
        ArrayList<OutletCandidate> outlets = new ArrayList<>(List.of(outlet(0), outlet(1)));
        HydrologyOutletPlanner outletPlanner = mock(HydrologyOutletPlanner.class);
        when(outletPlanner.regionalOceanOutletCandidates(any())).thenAnswer(invocation -> new ArrayList<>(outlets));
        replace(planner, "outletPlanner", outletPlanner);
        Map<Long, Integer> sources = new HashMap<>();
        HydrologyRegionalGraph graph = mock(HydrologyRegionalGraph.class);
        when(graph.route(any(), anyList(), anyBoolean())).thenAnswer(invocation -> {
            HydrologySampledGrid grid = invocation.getArgument(0);
            HydrologyRegionalGraph.Label[] labels = new HydrologyRegionalGraph.Label[grid.nodes().size()];
            int[] contributions = new int[grid.nodes().size()];
            Arrays.fill(contributions, 1);
            int ordinal = 0;
            for (HydrologyGridNode node : grid.nodes()) {
                if (!grid.owns(node.x(), node.z()) || ordinal >= secondCatchmentSize + 4) {
                    continue;
                }
                int root = ordinal < 4 ? 0 : 1;
                int local = ordinal < 4 ? ordinal : ordinal - 4;
                double length = ordinal < 4 || secondCatchmentSize == 4 ? 10000D - local * 2000D : 10000D - local;
                HydrologyRegionalGraph.Label terminal = new HydrologyRegionalGraph.Label(
                        new HydrologyRegionalGraph.LabelState(0, 0, 63, 0D, 0D, root), null);
                labels[node.index()] = new HydrologyRegionalGraph.Label(new HydrologyRegionalGraph.LabelState(
                        node.index(), node.index(), 63, 0D, length, root), terminal);
                sources.put(RiverFootprint.pack(node.x(), node.z()), ordinal++);
            }
            assertEquals(secondCatchmentSize + 4, ordinal);
            return new HydrologyRegionalGraph.Tree(labels, contributions, outlets);
        });
        when(graph.path(any(), anyInt())).thenAnswer(invocation -> List.of(invocation.<Integer>getArgument(1), 0));
        replace(planner.regional, "graph", graph);
        HydrologyRegionalRoute routes = mock(HydrologyRegionalRoute.class);
        when(routes.select(anyList(), anyString(), anyBoolean(), any(), any())).thenAnswer(invocation -> {
            List<HydrologyPoint> guide = invocation.getArgument(0);
            HydrologyPoint first = guide.getFirst();
            return provider.build(sources.get(RiverFootprint.pack(first.x(), first.z())));
        });
        replace(planner.regional, "routes", routes);
        return new Fixture(planner);
    }

    private static OutletCandidate outlet(int root) {
        HydrologyPoint point = new HydrologyPoint(2048, 63, root * 1024);
        return new OutletCandidate(0, 0, new RiverOutlet(1000L + root, HydrologyFeatureType.MOUTH,
                root, point, new HydrologyPoint(point.x() + 1, point.y(), point.z()), 63, true));
    }

    private static HydrologyRegionalRoute.Attempt accepted(int source) {
        int z = source * 4096;
        HydrologyPoint first = new HydrologyPoint(0, 70, z);
        HydrologyPoint last = new HydrologyPoint(512, 70, z);
        HydraulicSegment segment = new HydraulicSegment(100L + source, 100L + source, HydrologyFeatureType.SURFACE_POOL,
                70, 70, 4, 2, false, false, List.of(first, last),
                new HydraulicChannelProfile(new double[]{4D}, new double[]{2D}));
        RiverCourse course = new RiverCourse(100L + source, RiverCourseType.SURFACE, OptionalLong.of(10000L + source),
                OptionalLong.of(1000L + (source < 4 ? 0 : 1)), "default", 1, List.of(), List.of(segment));
        HydrologyRegionalNetwork network = new HydrologyRegionalNetwork(List.of(), List.of(), List.of(),
                List.of(course), List.of(), List.of());
        return new HydrologyRegionalRoute.Attempt(new HydrologyRegionalRoute.Refinement(List.of(first, last), null, null, 0, null), network, null);
    }

    private static HydrologyRegionalRoute.Attempt rejected(int source) {
        HydrologyPoint point = new HydrologyPoint(source, 70, 0);
        HydrologyDiagnosticCandidate diagnostic = new HydrologyDiagnosticCandidate(source, HydrologyCandidateKind.REGIONAL_SOURCE,
                HydrologyFeatureType.SURFACE_POOL, point, HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, source);
        return new HydrologyRegionalRoute.Attempt(new HydrologyRegionalRoute.Refinement(List.of(point), null, null, 0, null),
                HydrologyRegionalNetwork.EMPTY, diagnostic);
    }

    private static void replace(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue("Timed out waiting for trial release", latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    @FunctionalInterface
    private interface SourceProvider {
        HydrologyRegionalRoute.Attempt build(int source);
    }

    private record Fixture(HydrologyPlanner planner) {
    }
}
