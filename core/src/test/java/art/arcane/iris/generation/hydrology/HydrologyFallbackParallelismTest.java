package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class HydrologyFallbackParallelismTest {
    private static final HydrologyTileKey KEY = new HydrologyTileKey(0, 0);
    private static final HydrologyCaveCourseFilter.Result EMPTY = new HydrologyCaveCourseFilter.Result(
            List.of(), List.of(), List.of(), List.of(), List.of());

    @Test(timeout = 15000)
    public void publicationRejectionConsumesTheNextCompletedOutletWithoutRebuildingIt() throws Exception {
        int parallelism = HydrologyPlanningAdmission.effectiveParallelism(4);
        int batchSize = parallelism <= 2 ? 1 : Math.min(parallelism, HydrologyPlanningAdmission.maximumTrials());
        AtomicIntegerArray builds = new AtomicIntegerArray(4);
        Fixture fixture = fixture(outlet -> {
            builds.incrementAndGet(outlet);
            if (outlet == 3) {
                throw new IllegalStateException("Unused fallback provider failed");
            }
        });
        ArrayList<Long> submitted = new ArrayList<>();
        CrossTileDraftAdmission admission = new CrossTileDraftAdmission() {
            @Override
            public void prepare() {
            }

            @Override
            public CrossTilePublicationAdmission admit(HydrologyCaveCourseFilter.Result result) {
                if (result.courses().isEmpty()) {
                    return new CrossTilePublicationAdmission(result, List.of(), false);
                }
                long course = result.courses().getFirst().id();
                int completed = Math.min(4, Math.ceilDiv((int) (course - 199L), batchSize) * batchSize);
                for (int outlet = 0; outlet < 4; outlet++) {
                    assertEquals("Every admitted trial provider must finish before publication", outlet < completed ? 1 : 0, builds.get(outlet));
                }
                submitted.add(course);
                return course == 200L
                        ? new CrossTilePublicationAdmission(EMPTY, List.of(diagnostic(900L)), true)
                        : new CrossTilePublicationAdmission(result, List.of(), false);
            }
        };
        HydrologyOwnerDraft draft = run(fixture, admission, 4);

        assertEquals(List.of(200L, 201L), submitted);
        assertEquals(List.of(201L), draft.result().courses().stream().map(RiverCourse::id).toList());
        assertEquals(List.of(200L, 900L, 201L), draft.diagnostics().stream().map(HydrologyDiagnosticCandidate::id).toList());
        for (int outlet = 0; outlet < 4; outlet++) {
            int completed = Math.min(4, Math.ceilDiv(2, batchSize) * batchSize);
            assertEquals(outlet < completed ? 1 : 0, builds.get(outlet));
        }
    }

    @Test(timeout = 15000)
    public void limitedWorkersStopAfterTheFirstAcceptedOutlet() throws Exception {
        int[] poolSizes = Runtime.getRuntime().availableProcessors() <= 2 ? new int[]{1, 2, 16} : new int[]{1, 2};
        for (int parallelism : poolSizes) {
            AtomicIntegerArray builds = new AtomicIntegerArray(4);
            Fixture fixture = fixture(outlet -> builds.incrementAndGet(outlet));

            HydrologyOwnerDraft draft = run(fixture, null, parallelism);

            assertEquals(List.of(200L), draft.result().courses().stream().map(RiverCourse::id).toList());
            assertEquals(List.of(200L), draft.diagnostics().stream().map(HydrologyDiagnosticCandidate::id).toList());
            assertEquals(1, builds.get(0));
            for (int outlet = 1; outlet < 4; outlet++) {
                assertEquals("Unused outlets must not be compiled", 0, builds.get(outlet));
            }
        }
    }

    @Test(timeout = 15000)
    public void rejectedOutletsAdvanceToTheNextWorkerBoundedBatch() throws Exception {
        int batchSize = 1;
        AtomicIntegerArray builds = new AtomicIntegerArray(4);
        Fixture fixture = fixture(outlet -> {
            builds.incrementAndGet(outlet);
            if (outlet == 3) {
                throw new IllegalStateException("Unused fallback provider failed");
            }
        });
        ArrayList<Long> submitted = new ArrayList<>();
        CrossTileDraftAdmission admission = new CrossTileDraftAdmission() {
            @Override
            public void prepare() {
            }

            @Override
            public CrossTilePublicationAdmission admit(HydrologyCaveCourseFilter.Result result) {
                if (result.courses().isEmpty()) {
                    return new CrossTilePublicationAdmission(result, List.of(), false);
                }
                long course = result.courses().getFirst().id();
                submitted.add(course);
                for (int outlet = 0; outlet < 4; outlet++) {
                    int completed = Math.min(4, Math.ceilDiv((int) (course - 199L), batchSize) * batchSize);
                    int expected = outlet < completed ? 1 : 0;
                    assertEquals("Only the demanded batch may be compiled", expected, builds.get(outlet));
                }
                return course < 202L
                        ? new CrossTilePublicationAdmission(EMPTY, List.of(diagnostic(course + 700L)), true)
                        : new CrossTilePublicationAdmission(result, List.of(), false);
            }
        };

        HydrologyOwnerDraft draft = run(fixture, admission, 2);

        assertEquals(List.of(200L, 201L, 202L), submitted);
        assertEquals(List.of(202L), draft.result().courses().stream().map(RiverCourse::id).toList());
        assertEquals(List.of(200L, 900L, 201L, 901L, 202L),
                draft.diagnostics().stream().map(HydrologyDiagnosticCandidate::id).toList());
        for (int outlet = 0; outlet < 4; outlet++) {
            int completed = Math.min(4, Math.ceilDiv(3, batchSize) * batchSize);
            assertEquals("Rejected trials must not be rebuilt", outlet < completed ? 1 : 0, builds.get(outlet));
        }
    }

    @Test(timeout = 15000)
    public void requiredFallbackFailureDrainsOtherStartedProviders() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        IllegalStateException expected = new IllegalStateException("Required fallback provider failed");
        Fixture fixture = fixture(outlet -> {
            if (outlet == 0) {
                failed.countDown();
                throw expected;
            }
            if (outlet == 1) {
                blocked.countDown();
                await(release);
                completed.set(true);
            }
        });
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            Future<HydrologyOwnerDraft> pending = pool.submit(() -> fixture.resolver().compileOwnerDraft(KEY, null, false));
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            if (HydrologyPlanningAdmission.maximumTrials() > 1
                    && HydrologyPlanningAdmission.effectiveParallelism(4) > 2) {
                assertTrue(blocked.await(5, TimeUnit.SECONDS));
                assertFalse(pending.isDone());
            }
            release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
            Throwable cause = failure.getCause();
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertSame(expected, cause);
            assertEquals(HydrologyPlanningAdmission.maximumTrials() > 1
                    && HydrologyPlanningAdmission.effectiveParallelism(4) > 2, completed.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static HydrologyOwnerDraft run(Fixture fixture, CrossTileDraftAdmission admission, int parallelism) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        try {
            return pool.submit(() -> fixture.resolver().compileOwnerDraft(KEY, admission, false)).get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static Fixture fixture(OutletProvider provider) throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(70, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(71L, HydrologyPlannerSettings.defaults(), terrain);
        HydrologyGridNode source = new HydrologyGridNode(0, 0, 0, 0, 0, 10L, terrain.sample(0, 0));
        HydrologySampledGrid grid = new HydrologySampledGrid(0, 0, 0, 0, 2048, 1, 64, List.of(source));
        HydrologyRoutingPlan primary = routing(List.of());
        planner.routingContexts.put(KEY, new SourceRoutingContext(grid, primary, primary, List.of()));
        HydrologySourcePlanner sourcePlanner = mock(HydrologySourcePlanner.class);
        when(sourcePlanner.hasRoutedSourceSearch()).thenReturn(true);
        when(sourcePlanner.requireOrganicSurface(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourcePlanner.buildRouting(any(), anyList(), anyBoolean())).thenAnswer(invocation -> {
            List<OutletCandidate> outlets = invocation.getArgument(1);
            provider.build(Math.toIntExact(outlets.getFirst().outlet().id() - 200L));
            return routing(outlets);
        });
        when(sourcePlanner.selectSources(any(), any(), any(), anyBoolean(), anyBoolean(), anyList(), anyMap()))
                .thenAnswer(invocation -> {
                    if (!invocation.<Boolean>getArgument(3)) {
                        return SourceSelection.empty(false);
                    }
                    HydrologyRoutingPlan selected = invocation.getArgument(2);
                    return selection(!selected.outlets().isEmpty());
                });
        when(sourcePlanner.uniqueDiagnostics(anyList())).thenAnswer(invocation -> List.copyOf(invocation.<List<HydrologyDiagnosticCandidate>>getArgument(0)));
        replace(planner, "sourcePlanner", sourcePlanner);
        HydrologyOutletPlanner outletPlanner = mock(HydrologyOutletPlanner.class);
        when(outletPlanner.resolveSurfaceFallbackOutlets(any())).thenReturn(List.of(outlet(0), outlet(1), outlet(2), outlet(3)));
        replace(planner, "outletPlanner", outletPlanner);
        HydrologyCrossTileResolver resolver = spy(planner.crossTile);
        doAnswer(invocation -> {
            SourceSelection selected = invocation.getArgument(4);
            if (selected.selectedCandidateIndices.isEmpty()) {
                return new HydrologyCrossTileResolver.PublicationAttempt(EMPTY, List.of());
            }
            HydrologyRoutingPlan selectedRouting = invocation.getArgument(2);
            long id = selectedRouting.outlets().getFirst().outlet().id();
            boolean finalPublication = invocation.getArgument(9);
            return new HydrologyCrossTileResolver.PublicationAttempt(result(id, source),
                    finalPublication ? List.of() : List.of(diagnostic(id)));
        }).when(resolver).compilePublication(any(), any(), any(), any(), any(), any(), any(), anyMap(), anyMap(), anyBoolean());
        return new Fixture(resolver);
    }

    private static SourceSelection selection(boolean accepted) {
        List<SourceCandidate> candidates = accepted ? List.of(new SourceCandidate(0, 10L, 1D, false)) : List.of();
        int size = candidates.size();
        SourceAdmissionSelection admission = new SourceAdmissionSelection(1, accepted ? List.of(0) : List.of(),
                new boolean[size], new boolean[size], new boolean[size], index -> true,
                SourceAdmissionSelection.Quotas.uniform(new int[size], 1, 1));
        return new SourceSelection(true, candidates, admission, new int[]{0}, 1);
    }

    private static HydrologyRoutingPlan routing(List<OutletCandidate> outlets) {
        return new HydrologyRoutingPlan(new double[]{0D}, new int[]{-1}, new int[]{0}, new int[]{0}, outlets, true);
    }

    private static OutletCandidate outlet(int index) {
        HydrologyPoint point = new HydrologyPoint(128, 63, index * 128);
        return new OutletCandidate(0, 0, new RiverOutlet(200L + index, HydrologyFeatureType.MOUTH, 10L,
                point, new HydrologyPoint(point.x() + 1, point.y(), point.z()), 63, true));
    }

    private static HydrologyCaveCourseFilter.Result result(long id, HydrologyGridNode source) {
        HydraulicSegment segment = new HydraulicSegment(id, id, HydrologyFeatureType.SURFACE_POOL,
                70, 70, 4, 2, false, false, List.of(source.naturalPoint(), new HydrologyPoint(128, 70, 0)),
                new HydraulicChannelProfile(new double[]{4D}, new double[]{2D}));
        RiverCourse course = new RiverCourse(id, RiverCourseType.SURFACE, OptionalLong.of(source.id()),
                OptionalLong.of(id), "default", 1, List.of(), List.of(segment));
        DrainageNode node = new DrainageNode(source.id(), source.x(), source.z(), source.terrain(), 0D, id);
        return new HydrologyCaveCourseFilter.Result(List.of(node), List.of(), List.of(outlet(Math.toIntExact(id - 200L)).outlet()),
                List.of(course), List.of());
    }

    private static HydrologyDiagnosticCandidate diagnostic(long id) {
        return new HydrologyDiagnosticCandidate(id, HydrologyCandidateKind.SOURCE,
                HydrologyFeatureType.SURFACE_POOL, new HydrologyPoint(0, 70, 0), HydrologyCandidateRejection.SOURCE_QUOTA, 0);
    }

    private static void replace(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue("Timed out waiting for fallback release", latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    @FunctionalInterface
    private interface OutletProvider {
        void build(int outlet);
    }

    private record Fixture(HydrologyCrossTileResolver resolver) {
    }
}
