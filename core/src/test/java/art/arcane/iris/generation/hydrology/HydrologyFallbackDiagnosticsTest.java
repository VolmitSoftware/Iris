package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class HydrologyFallbackDiagnosticsTest {
    private static final HydrologyTileKey KEY = new HydrologyTileKey(0, 0);
    private static final HydrologyCaveCourseFilter.Result EMPTY = new HydrologyCaveCourseFilter.Result(
            List.of(), List.of(), List.of(), List.of(), List.of());
    private static final long DISCARDED = 200L;
    private static final long ADOPTED = 201L;

    /**
     * A fallback re-routing re-runs source selection over the whole owned lattice, so every discarded trial invents a
     * full set of per-node rejections. Only the trial the draft actually keeps may report any.
     */
    @Test(timeout = 15000)
    public void discardedFallbackTrialsDoNotReportTheirRejections() throws Exception {
        Harness harness = harness();
        HydrologyOwnerDraft draft = run(harness.resolver());

        assertEquals(List.of(diagnosticId(ADOPTED)),
                draft.diagnostics().stream().map(HydrologyDiagnosticCandidate::id).toList());
        assertEquals(List.of(ADOPTED), draft.result().courses().stream().map(RiverCourse::id).toList());
    }

    /**
     * Only the rejections of a discarded trial are dropped. Its refined edges and source compilations are caches
     * keyed by routing and source set, so the owner keeps them and the next trial does not recompile them. This is
     * the one part of a discarded trial that can still reach generation output.
     */
    @Test(timeout = 15000)
    public void discardedFallbackTrialsStillMergeTheirCompilationCaches() throws Exception {
        Harness harness = harness();
        run(harness.resolver());

        assertEquals(Set.of(DISCARDED, ADOPTED), new HashSet<>(harness.refinedEdges().get().keySet()));
        assertEquals(Set.of(DISCARDED, ADOPTED), harness.sourceCompilations().get().keySet().stream()
                .map(key -> key.routing().outlets().getFirst().outlet().id())
                .collect(Collectors.toSet()));
    }

    private static HydrologyOwnerDraft run(HydrologyCrossTileResolver resolver) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            return pool.submit(() -> resolver.compileOwnerDraft(KEY, null, false)).get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static Harness harness() throws Exception {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(70, 0D, "land");
        HydrologyPlanner planner = new HydrologyPlanner(71L, HydrologyPlannerSettings.defaults(), terrain);
        HydrologyGridNode source = new HydrologyGridNode(0, 0, 0, 0, 0, 10L, terrain.sample(0, 0));
        HydrologySampledGrid grid = new HydrologySampledGrid(0, 0, 0, 0, 2048, 1, 64, List.of(source));
        HydrologyRoutingPlan primary = routing(List.of());
        planner.routingContexts.put(KEY, new SourceRoutingContext(grid, primary, primary, List.of()));
        HydrologySourcePlanner sourcePlanner = mock(HydrologySourcePlanner.class);
        when(sourcePlanner.hasRoutedSourceSearch()).thenReturn(true);
        when(sourcePlanner.requireOrganicSurface(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourcePlanner.buildRouting(any(), anyList(), anyBoolean()))
                .thenAnswer(invocation -> routing(invocation.getArgument(1)));
        when(sourcePlanner.selectSources(any(), any(), any(), anyBoolean(), anyBoolean(), anyList(), anyMap()))
                .thenAnswer(invocation -> {
                    if (!invocation.<Boolean>getArgument(3)) {
                        return SourceSelection.empty(false);
                    }
                    HydrologyRoutingPlan selected = invocation.getArgument(2);
                    if (selected.outlets().isEmpty()) {
                        return selection(false);
                    }
                    long outlet = selected.outlets().getFirst().outlet().id();
                    invocation.<List<HydrologyDiagnosticCandidate>>getArgument(5).add(diagnostic(diagnosticId(outlet)));
                    return selection(true);
                });
        when(sourcePlanner.uniqueDiagnostics(anyList()))
                .thenAnswer(invocation -> List.copyOf(invocation.<List<HydrologyDiagnosticCandidate>>getArgument(0)));
        replace(planner, "sourcePlanner", sourcePlanner);
        HydrologyOutletPlanner outletPlanner = mock(HydrologyOutletPlanner.class);
        when(outletPlanner.resolveSurfaceFallbackOutlets(any())).thenReturn(List.of(outlet(0), outlet(1)));
        replace(planner, "outletPlanner", outletPlanner);
        HydrologyCrossTileResolver resolver = spy(planner.crossTile);
        AtomicReference<Map<Long, List<HydrologyPoint>>> ownerEdges = new AtomicReference<>();
        AtomicReference<Map<HydrologyCrossTileResolver.SourceCompilationKey,
                HydrologyCrossTileResolver.SourceCompilation>> ownerCompilations = new AtomicReference<>();
        doAnswer(invocation -> {
            Map<Long, List<HydrologyPoint>> refinedEdges = invocation.getArgument(7);
            Map<HydrologyCrossTileResolver.SourceCompilationKey,
                    HydrologyCrossTileResolver.SourceCompilation> sourceCompilations = invocation.getArgument(8);
            if (invocation.<Boolean>getArgument(9)) {
                // Only the owner draft publishes deep fluids, so these are the maps the trials merge into.
                ownerEdges.compareAndSet(null, refinedEdges);
                ownerCompilations.compareAndSet(null, sourceCompilations);
            }
            SourceSelection selected = invocation.getArgument(4);
            if (selected.selectedCandidateIndices.isEmpty()) {
                return new HydrologyCrossTileResolver.PublicationAttempt(EMPTY, List.of());
            }
            HydrologyRoutingPlan selectedRouting = invocation.getArgument(2);
            long outlet = selectedRouting.outlets().getFirst().outlet().id();
            refinedEdges.put(outlet, List.of(new HydrologyPoint(0, 70, 0)));
            sourceCompilations.put(
                    new HydrologyCrossTileResolver.SourceCompilationKey(true, selectedRouting, List.of(0)),
                    new HydrologyCrossTileResolver.SourceCompilation(
                            new CompiledGraph(List.of(), List.of(), List.of(), Map.of()), List.of(), List.of()));
            return new HydrologyCrossTileResolver.PublicationAttempt(
                    outlet == ADOPTED ? result(outlet, source) : EMPTY, List.of());
        }).when(resolver).compilePublication(any(), any(), any(), any(), any(), any(), any(), anyMap(), anyMap(), anyBoolean());
        return new Harness(resolver, ownerEdges, ownerCompilations);
    }

    private record Harness(
            HydrologyCrossTileResolver resolver,
            AtomicReference<Map<Long, List<HydrologyPoint>>> refinedEdges,
            AtomicReference<Map<HydrologyCrossTileResolver.SourceCompilationKey,
                    HydrologyCrossTileResolver.SourceCompilation>> sourceCompilations
    ) {
    }

    private static long diagnosticId(long outlet) {
        return 700L + outlet;
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
        return new HydrologyCaveCourseFilter.Result(List.of(node), List.of(),
                List.of(outlet(Math.toIntExact(id - 200L)).outlet()), List.of(course), List.of());
    }

    private static HydrologyDiagnosticCandidate diagnostic(long id) {
        return new HydrologyDiagnosticCandidate(id, HydrologyCandidateKind.SOURCE,
                HydrologyFeatureType.SURFACE_POOL, new HydrologyPoint(0, 70, 0),
                HydrologyCandidateRejection.NO_DRAINAGE_PATH, 0);
    }

    private static void replace(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
