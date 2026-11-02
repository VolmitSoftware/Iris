package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.query.HydrologySearchTimeoutException;
import art.arcane.iris.generation.terrain.IrisDimension;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class GenerationSemanticQueriesTest {
    @Test
    public void mergesExactHistoricalStructureStartsWithEligibleActiveCandidates() {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(complex.allowsNewGenerationChunk(anyInt(), anyInt())).thenReturn(true);
        when(complex.allowsNewGenerationChunk(1, 0)).thenReturn(false);
        when(history.isActiveUnowned(anyInt(), anyInt())).thenReturn(true);
        when(history.isActiveUnowned(0, 0)).thenReturn(false);
        ChunkGenerationSemantics sealedEmpty = ChunkGenerationSemantics.builder(2, 0, 2L).seal().build();
        when(history.semantics(2, 0)).thenReturn(Optional.of(sealedEmpty));
        GenerationSemanticIndex.Match recorded = new GenerationSemanticIndex.Match(
                GenerationSemanticIndex.SemanticKind.STRUCTURE,
                "old-tower",
                new GenerationSemanticIndex.ChunkReference(6, 0, 1L),
                Optional.of(new ChunkGenerationSemantics.BlockPosition(100, 73, 5)));
        when(history.findRecorded(any())).thenReturn(Optional.of(recorded));
        IrisStructureLocator.LocateResult nearer = foundStructure(48, 90, 0);

        try (MockedStatic<IrisStructureLocator> structures = mockStatic(IrisStructureLocator.class)) {
            structures.when(() -> IrisStructureLocator.locate(
                    eq(engine), eq("old-tower"), eq(0), eq(0), eq(20), any()))
                    .thenAnswer(invocation -> {
                        IrisStructureLocator.CandidateFilter filter = invocation.getArgument(5);
                        assertFalse(filter.accept(0, 0));
                        assertFalse(filter.accept(1, 0));
                        assertFalse(filter.accept(2, 0));
                        assertTrue(filter.accept(3, 0));
                        return nearer;
                    });
            assertSame(nearer, GenerationSemanticQueries.nearestStructure(engine, "old-tower", 0, 0, 20));

            structures.when(() -> IrisStructureLocator.locate(
                    eq(engine), eq("old-tower"), eq(0), eq(0), eq(20), any()))
                    .thenReturn(foundStructure(120, 90, 0));
            assertEquals(foundStructure(100, 73, 5),
                    GenerationSemanticQueries.nearestStructure(engine, "old-tower", 0, 0, 20));

            structures.when(() -> IrisStructureLocator.locate(
                    eq(engine), eq("old-tower"), eq(0), eq(0), eq(20), any()))
                    .thenReturn(foundStructure(100, 90, 5));
            assertEquals(foundStructure(100, 73, 5),
                    GenerationSemanticQueries.nearestStructure(engine, "old-tower", 0, 0, 20));

            structures.when(() -> IrisStructureLocator.locate(
                    eq(engine), eq("old-tower"), eq(0), eq(0), eq(20), any()))
                    .thenReturn(new IrisStructureLocator.LocateResult(IrisStructureLocator.LocateStatus.NOT_FOUND, 0, 0, 0));
            assertEquals(foundStructure(100, 73, 5),
                    GenerationSemanticQueries.nearestStructure(engine, "old-tower", 0, 0, 20));
        }
    }

    @Test
    public void structureQueryPreservesTheRecordedPackIdentifier() {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        GenerationSemanticIndex.Match recorded = new GenerationSemanticIndex.Match(
                GenerationSemanticIndex.SemanticKind.STRUCTURE, "Structures/Tower+",
                new GenerationSemanticIndex.ChunkReference(1, 0, 1L),
                Optional.of(new ChunkGenerationSemantics.BlockPosition(20, 73, 5)));
        when(history.findRecorded(any())).thenAnswer(invocation -> {
            GenerationSemanticIndex.Query query = invocation.getArgument(0);
            assertEquals("Structures/Tower+", query.key());
            return Optional.of(recorded);
        });
        try (MockedStatic<IrisStructureLocator> structures = mockStatic(IrisStructureLocator.class)) {
            structures.when(() -> IrisStructureLocator.locate(
                    eq(engine), eq("Structures/Tower+"), eq(0), eq(0), eq(20), any()))
                    .thenReturn(new IrisStructureLocator.LocateResult(
                            IrisStructureLocator.LocateStatus.NOT_FOUND, 0, 0, 0));
            assertEquals(foundStructure(20, 73, 5),
                    GenerationSemanticQueries.nearestStructure(engine, "Structures/Tower+", 0, 0, 20));
        }
    }

    @Test
    public void exhaustedStructureSearchDoesNotClaimARecordedResultIsNearest() {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        IrisStructureLocator.LocateResult limit = new IrisStructureLocator.LocateResult(
                IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED, 0, 0, 0);
        try (MockedStatic<IrisStructureLocator> structures = mockStatic(IrisStructureLocator.class)) {
            structures.when(() -> IrisStructureLocator.locate(
                    eq(engine), eq("tower"), eq(0), eq(0), eq(20), any())).thenReturn(limit);
            assertSame(limit, GenerationSemanticQueries.nearestStructure(engine, "tower", 0, 0, 20));
        }
    }

    private static IrisStructureLocator.LocateResult foundStructure(int x, int y, int z) {
        return new IrisStructureLocator.LocateResult(IrisStructureLocator.LocateStatus.FOUND, x, y, z);
    }

    @Test
    public void rejectsTaperedPredictionsAndMergesTheNearestEligibleRiver() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        IrisDimension dimension = mock(IrisDimension.class);
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationActivation activation = mock(GenerationActivation.class);
        HydrologyFeatureRef tapered = feature(1L, 8, 20, 0);
        HydrologyFeatureRef eligible = feature(2L, 40, 20, 0);
        ChunkGenerationSemantics.RiverFeatureOccurrence recordedOccurrence =
                new ChunkGenerationSemantics.RiverFeatureOccurrence(
                        "water",
                        HydrologyFeatureType.RIVER,
                        3L,
                        new ChunkGenerationSemantics.BlockPosition(100, 70, 0)
                );
        GenerationSemanticIndex.RiverMatch recorded = new GenerationSemanticIndex.RiverMatch(
                recordedOccurrence,
                new GenerationSemanticIndex.ChunkReference(6, 0, 1L)
        );

        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        when(complex.getTransitionDisplacement()).thenReturn(displacement);
        when(complex.allowsNewGenerationChunk(anyInt(), anyInt())).thenReturn(true);
        when(displacement.seamWeight(anyInt(), anyInt())).thenAnswer(invocation ->
                invocation.<Integer>getArgument(0) == tapered.x() ? 0.5D : 1D);
        when(dimension.getMinHeight()).thenReturn(-64);
        when(router.history()).thenReturn(history);
        when(history.findRecordedRiver(any())).thenAnswer(invocation -> {
            GenerationSemanticIndex.RiverQuery query = invocation.getArgument(0);
            assertEquals(java.util.Set.of(HydrologyFeatureType.RIVER), query.types());
            return Optional.of(recorded);
        });
        when(history.isActiveUnowned(anyInt(), anyInt())).thenReturn(true);
        when(history.semantics(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(history.activeActivation()).thenReturn(activation);
        when(activation.activationId()).thenReturn(2L);
        when(runtime.nearestFeature(eq(0), eq(0), eq(HydrologyFeatureQuery.of(HydrologyFeatureType.RIVER)), eq(200), any(), any()))
                .thenAnswer(invocation -> firstEligible(List.of(tapered, eligible), invocation.getArgument(4)));

        GenerationSemanticQueries.RiverResult active = found(GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.RIVER, 0, 0, 200, () -> true));

        assertEquals(GenerationSemanticQueries.RiverSource.ACTIVE_PREDICTION, active.source());
        assertEquals(HydrologyFeatureType.RIVER, active.type());
        assertEquals(40, active.x());
        assertEquals(-44, active.y());

        HydrologyFeatureRef fartherActive = feature(4L, 120, 20, 0);
        doAnswer(invocation -> firstEligible(List.of(tapered, fartherActive), invocation.getArgument(4)))
                .when(runtime).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());

        GenerationSemanticQueries.RiverResult historical = found(GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.RIVER, 0, 0, 200, () -> true));

        assertEquals(GenerationSemanticQueries.RiverSource.RECORDED, historical.source());
        assertEquals(100, historical.x());
        assertEquals(70, historical.y());
    }

    @Test
    public void seamWeightIsJudgedPerCandidateInsideOneTransitionChunk() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        IrisDimension dimension = mock(IrisDimension.class);
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        HydrologyFeatureRef tapered = feature(1L, 3, 20, 5);
        HydrologyFeatureRef solid = feature(2L, 12, 20, 5);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        when(complex.getTransitionDisplacement()).thenReturn(displacement);
        when(complex.allowsNewGenerationChunk(anyInt(), anyInt())).thenReturn(true);
        when(displacement.seamWeight(anyInt(), anyInt())).thenAnswer(invocation ->
                invocation.<Integer>getArgument(0) < 8 ? 0.4D : 1D);
        when(dimension.getMinHeight()).thenReturn(-64);
        when(runtime.nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any()))
                .thenAnswer(invocation -> firstEligible(List.of(tapered, solid), invocation.getArgument(4)));

        GenerationSemanticQueries.RiverResult taperedFirst = found(GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.RIVER, 0, 0, 200, () -> true));
        doAnswer(invocation -> firstEligible(List.of(solid, tapered), invocation.getArgument(4)))
                .when(runtime).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());
        GenerationSemanticQueries.RiverResult solidFirst = found(GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.RIVER, 0, 0, 200, () -> true));
        doAnswer(invocation -> {
            Predicate<HydrologyFeatureRef> eligible = invocation.getArgument(4);
            assertTrue(eligible.test(solid));
            assertFalse(eligible.test(tapered));
            return Optional.empty();
        }).when(runtime).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());

        assertEquals(2L, taperedFirst.featureId());
        assertEquals(2L, solidFirst.featureId());
        assertEquals(new GenerationSemanticQueries.RiverSearch.Missing("No river found within 200 blocks."),
                GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.RIVER, 0, 0, 200, () -> true));
    }

    @Test
    public void returnsRecordedRiverWhenTheActivePackHasNoHydrology() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        ChunkGenerationSemantics.RiverFeatureOccurrence occurrence =
                new ChunkGenerationSemantics.RiverFeatureOccurrence(
                        "water",
                        HydrologyFeatureType.WATERFALL,
                        9L,
                        new ChunkGenerationSemantics.BlockPosition(-30, 55, 12)
                );

        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.unavailable(any())).thenReturn(Optional.of("hydrology is off"));
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.findRecordedRiver(any())).thenReturn(Optional.of(
                new GenerationSemanticIndex.RiverMatch(
                        occurrence,
                        new GenerationSemanticIndex.ChunkReference(-2, 0, 1L)
                )
        ));

        GenerationSemanticQueries.RiverResult result = found(GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.WATERFALL, 0, 0, 100, () -> true));

        assertEquals(GenerationSemanticQueries.RiverSource.RECORDED, result.source());
        assertEquals(HydrologyFeatureType.WATERFALL, result.type());
        assertEquals(9L, result.featureId());
        verify(runtime, never()).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());

        when(history.findRecordedRiver(any())).thenReturn(Optional.empty());
        assertEquals(new GenerationSemanticQueries.RiverSearch.Missing("No waterfall forms in this dimension: hydrology is off."),
                GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.WATERFALL, 0, 0, 100, () -> true));
    }

    @Test
    public void disabledTypeAnswersWithItsReasonWithoutPlanning() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.unavailable(HydrologyFeatureType.DAM)).thenReturn(Optional.of("hydrology.ponds.dams is disabled"));
        when(runtime.unavailable(HydrologyFeatureType.CAVE_POOL)).thenReturn(Optional.of("hydrology.underground.cavePools is disabled"));
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.findRecordedRiver(any())).thenReturn(Optional.empty());

        assertEquals(new GenerationSemanticQueries.RiverSearch.Missing("No dam forms in this dimension: hydrology.ponds.dams is disabled."),
                GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.DAM, 0, 0, 100, () -> true));
        assertEquals(new GenerationSemanticQueries.RiverSearch.Missing(
                        "No cave pool forms in this dimension: hydrology.underground.cavePools is disabled."),
                GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.CAVE_POOL, 0, 0, 100, () -> true));

        ChunkGenerationSemantics.RiverFeatureOccurrence older = new ChunkGenerationSemantics.RiverFeatureOccurrence(
                "water", HydrologyFeatureType.DAM, 5L, new ChunkGenerationSemantics.BlockPosition(30, 64, 0));
        when(history.findRecordedRiver(any())).thenReturn(Optional.of(
                new GenerationSemanticIndex.RiverMatch(older, new GenerationSemanticIndex.ChunkReference(1, 0, 1L))));
        GenerationSemanticQueries.RiverResult recorded = found(
                GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.DAM, 0, 0, 100, () -> true));

        assertEquals(GenerationSemanticQueries.RiverSource.RECORDED, recorded.source());
        assertEquals(5L, recorded.featureId());
        verify(runtime, never()).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());
    }

    @Test
    public void stoppedSearchReportsTheNearestWaterFoundSoFar() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        IrisDimension dimension = mock(IrisDimension.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationActivation activation = mock(GenerationActivation.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getMinHeight()).thenReturn(-64);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any()))
                .thenThrow(new HydrologySearchTimeoutException(Duration.ofSeconds(30L), null));
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());

        assertEquals(new GenerationSemanticQueries.RiverSearch.Stopped(Optional.empty(),
                        "Stopped searching for the nearest river after 30 seconds without finding one."),
                GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.RIVER, 0, 0, 100, () -> true));

        ChunkGenerationSemantics.RiverFeatureOccurrence occurrence = new ChunkGenerationSemantics.RiverFeatureOccurrence(
                "water", HydrologyFeatureType.RIVER, 8L, new ChunkGenerationSemantics.BlockPosition(60, 70, 0));
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.activeActivation()).thenReturn(activation);
        when(activation.activationId()).thenReturn(2L);
        when(history.findRecordedRiver(any())).thenReturn(Optional.of(
                new GenerationSemanticIndex.RiverMatch(occurrence, new GenerationSemanticIndex.ChunkReference(3, 0, 1L))));
        GenerationSemanticQueries.RiverSearch.Stopped recorded = stopped(
                GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.RIVER, 0, 0, 100, () -> true));

        assertEquals(8L, recorded.nearestSoFar().orElseThrow().featureId());
        assertEquals("Stopped searching for the nearest river after 30 seconds. The nearest river found so far is at 60, 70, 0; "
                + "a nearer one may exist.", recorded.message());

        doThrow(new HydrologySearchTimeoutException(Duration.ofSeconds(30L), feature(4L, 20, 20, 0)))
                .when(runtime).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());
        GenerationSemanticQueries.RiverSearch.Stopped planned = stopped(
                GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.RIVER, 0, 0, 100, () -> true));

        GenerationSemanticQueries.RiverResult nearer = planned.nearestSoFar().orElseThrow();
        assertEquals(GenerationSemanticQueries.RiverSource.ACTIVE_PREDICTION, nearer.source());
        assertEquals(4L, nearer.featureId());
        assertEquals(-44, nearer.y());
        assertTrue(planned.message(), planned.message().contains("at 20, -44, 0;"));
    }

    @Test
    public void aNewSearchCancelsThePlayersPreviousSearch() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any())).thenReturn(Optional.empty());
        List<Runnable> queued = new ArrayList<>();
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        GenerationSemanticQueries.RiverSearch missing = new GenerationSemanticQueries.RiverSearch.Missing(
                "No river found within " + GenerationSemanticQueries.RIVER_SEARCH_DISTANCE + " blocks.");

        CompletableFuture<GenerationSemanticQueries.RiverSearch> first = GenerationSemanticQueries.startRiverSearch(
                player, queued::add, engine, HydrologyFeatureType.RIVER, 0, 0);
        CompletableFuture<GenerationSemanticQueries.RiverSearch> elsewhere = GenerationSemanticQueries.startRiverSearch(
                other, queued::add, engine, HydrologyFeatureType.RIVER, 0, 0);
        CompletableFuture<GenerationSemanticQueries.RiverSearch> second = GenerationSemanticQueries.startRiverSearch(
                player, queued::add, engine, HydrologyFeatureType.RIVER, 0, 0);

        assertTrue(first.isCancelled());
        assertFalse(elsewhere.isDone());
        for (Runnable search : queued) {
            search.run();
        }
        assertEquals(missing, second.join());
        assertEquals(missing, elsewhere.join());
        verify(runtime, times(2)).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());
    }

    @Test
    public void supersededSearchStopsAndOtherCancellationsAreFailures() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        UUID player = UUID.randomUUID();
        AtomicReference<CompletableFuture<GenerationSemanticQueries.RiverSearch>> replacement = new AtomicReference<>();
        AtomicBoolean stillRunning = new AtomicBoolean(true);
        doAnswer(invocation -> {
            BooleanSupplier running = invocation.getArgument(5);
            assertTrue(running.getAsBoolean());
            replacement.set(GenerationSemanticQueries.startRiverSearch(player, ignored -> {
            }, engine, HydrologyFeatureType.RIVER, 0, 0));
            stillRunning.set(running.getAsBoolean());
            throw new CancellationException("The hydrology feature search was cancelled.");
        }).when(runtime).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());

        CompletableFuture<GenerationSemanticQueries.RiverSearch> superseded = GenerationSemanticQueries.startRiverSearch(
                player, Runnable::run, engine, HydrologyFeatureType.RIVER, 0, 0);

        assertFalse(stillRunning.get());
        assertTrue(superseded.isCancelled());
        replacement.get().cancel(false);

        doThrow(new CancellationException("Hydrology plan cache is closed."))
                .when(runtime).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());
        CompletableFuture<GenerationSemanticQueries.RiverSearch> closed = GenerationSemanticQueries.startRiverSearch(
                player, Runnable::run, engine, HydrologyFeatureType.RIVER, 0, 0);

        assertFalse(closed.isCancelled());
        ExecutionException failure = assertThrows(ExecutionException.class, closed::get);
        assertTrue(String.valueOf(failure.getCause()), failure.getCause() instanceof IllegalStateException);
        assertEquals("Hydrology plan cache is closed.", failure.getCause().getMessage());
    }

    @Test
    public void cavePoolsAreFoundOnlyInRecordedHistory() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        ChunkGenerationSemantics.RiverFeatureOccurrence occurrence = new ChunkGenerationSemantics.RiverFeatureOccurrence(
                "water", HydrologyFeatureType.CAVE_POOL, 12L, new ChunkGenerationSemantics.BlockPosition(40, -20, 8));
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.findRecordedRiver(any())).thenReturn(Optional.of(
                new GenerationSemanticIndex.RiverMatch(occurrence, new GenerationSemanticIndex.ChunkReference(2, 0, 1L))));

        GenerationSemanticQueries.RiverResult found = found(GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.CAVE_POOL, 0, 0, 100, () -> true));

        assertEquals(GenerationSemanticQueries.RiverSource.RECORDED, found.source());
        assertEquals(40, found.x());
        assertEquals(-20, found.y());
        verify(runtime, never()).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());

        when(history.findRecordedRiver(any())).thenReturn(Optional.empty());
        assertEquals(new GenerationSemanticQueries.RiverSearch.Missing("No cave pool found in generated chunks within 100 blocks. Cave pools "
                        + "form in carved caves, so only chunks that have already generated can hold one."),
                GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.CAVE_POOL, 0, 0, 100, () -> true));
        verify(runtime, never()).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any());
        assertTrue(GenerationSemanticQueries.riverSearchMessage(HydrologyFeatureType.CAVE_POOL, 100).contains("generated chunks"));
    }

    @Test
    public void recordedCavePoolsListTheHistoryOfTheRequestedChunks() {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.semantics(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(history.semantics(1, 0)).thenReturn(Optional.of(ChunkGenerationSemantics.builder(1, 0, 1L)
                .addRiverFeature("water", HydrologyFeatureType.CAVE_POOL, 3L, 20, 10, 4)
                .addRiverFeature("water", HydrologyFeatureType.STREAM, 4L, 21, 70, 5)
                .build()));

        assertEquals(List.of(new ChunkGenerationSemantics.BlockPosition(20, 10, 4)),
                GenerationSemanticQueries.recordedCavePools(engine, 0, 0, 1, 1));
        assertTrue(GenerationSemanticQueries.recordedCavePools(mock(Engine.class), 0, 0, 1, 1).isEmpty());
    }

    @Test
    public void plannerFailureEscapesInsteadOfReportingNothingFound() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        IllegalStateException failure = new IllegalStateException("window planner exploded");
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.nearestFeature(anyInt(), anyInt(), any(), anyInt(), any(), any())).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.LAKE, 0, 0, 100, () -> true));

        assertSame(failure, thrown);
    }

    private static Optional<HydrologyFeatureRef> firstEligible(
            List<HydrologyFeatureRef> candidates,
            Predicate<HydrologyFeatureRef> eligibility
    ) {
        for (HydrologyFeatureRef candidate : candidates) {
            if (eligibility.test(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static GenerationSemanticQueries.RiverResult found(GenerationSemanticQueries.RiverSearch search) {
        if (search instanceof GenerationSemanticQueries.RiverSearch.Found found) {
            return found.feature();
        }
        throw new AssertionError("Expected a river, the search said: " + search);
    }

    private static GenerationSemanticQueries.RiverSearch.Stopped stopped(GenerationSemanticQueries.RiverSearch search) {
        if (search instanceof GenerationSemanticQueries.RiverSearch.Stopped stopped) {
            return stopped;
        }
        throw new AssertionError("Expected a stopped search, the search said: " + search);
    }

    private static HydrologyFeatureRef feature(long id, int x, int y, int z) {
        return new HydrologyFeatureRef(id, HydrologyFeatureType.RIVER, x, y, z);
    }
}
