package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.terrain.IrisDimension;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
    public void rejectsTaperedPredictionsAndMergesTheNearestEligibleRiver() {
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
        when(runtime.nearestFeature(eq(0), eq(0), eq(HydrologyFeatureQuery.of(HydrologyFeatureType.RIVER)), eq(200), any()))
                .thenAnswer(invocation -> firstEligible(List.of(tapered, eligible), invocation.getArgument(4)));

        GenerationSemanticQueries.RiverResult active = GenerationSemanticQueries.nearestRiver(
                engine, HydrologyFeatureType.RIVER, 0, 0, 200).orElseThrow();

        assertEquals(GenerationSemanticQueries.RiverSource.ACTIVE_PREDICTION, active.source());
        assertEquals(HydrologyFeatureType.RIVER, active.type());
        assertEquals(40, active.x());
        assertEquals(-44, active.y());

        HydrologyFeatureRef fartherActive = feature(4L, 120, 20, 0);
        doAnswer(invocation -> firstEligible(List.of(tapered, fartherActive), invocation.getArgument(4)))
                .when(runtime).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any());

        GenerationSemanticQueries.RiverResult historical = GenerationSemanticQueries.nearestRiver(
                engine, HydrologyFeatureType.RIVER, 0, 0, 200).orElseThrow();

        assertEquals(GenerationSemanticQueries.RiverSource.RECORDED, historical.source());
        assertEquals(100, historical.x());
        assertEquals(70, historical.y());
    }

    @Test
    public void seamWeightIsJudgedPerCandidateInsideOneTransitionChunk() {
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
        when(runtime.nearestFeature(anyInt(), anyInt(), any(), anyInt(), any()))
                .thenAnswer(invocation -> firstEligible(List.of(tapered, solid), invocation.getArgument(4)));

        GenerationSemanticQueries.RiverResult taperedFirst = GenerationSemanticQueries.nearestRiver(
                engine, HydrologyFeatureType.RIVER, 0, 0, 200).orElseThrow();
        doAnswer(invocation -> firstEligible(List.of(solid, tapered), invocation.getArgument(4)))
                .when(runtime).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any());
        GenerationSemanticQueries.RiverResult solidFirst = GenerationSemanticQueries.nearestRiver(
                engine, HydrologyFeatureType.RIVER, 0, 0, 200).orElseThrow();
        doAnswer(invocation -> {
            Predicate<HydrologyFeatureRef> eligible = invocation.getArgument(4);
            assertTrue(eligible.test(solid));
            assertFalse(eligible.test(tapered));
            return Optional.empty();
        }).when(runtime).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any());

        assertEquals(2L, taperedFirst.featureId());
        assertEquals(2L, solidFirst.featureId());
        assertTrue(GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.RIVER, 0, 0, 200).isEmpty());
    }

    @Test
    public void returnsRecordedRiverWhenTheActivePackHasNoHydrology() {
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
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.findRecordedRiver(any())).thenReturn(Optional.of(
                new GenerationSemanticIndex.RiverMatch(
                        occurrence,
                        new GenerationSemanticIndex.ChunkReference(-2, 0, 1L)
                )
        ));

        GenerationSemanticQueries.RiverResult result = GenerationSemanticQueries.nearestRiver(
                engine, HydrologyFeatureType.WATERFALL, 0, 0, 100).orElseThrow();

        assertEquals(GenerationSemanticQueries.RiverSource.RECORDED, result.source());
        assertEquals(HydrologyFeatureType.WATERFALL, result.type());
        assertEquals(9L, result.featureId());
    }

    @Test
    public void cavePoolsAreFoundOnlyInRecordedHistory() {
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

        GenerationSemanticQueries.RiverResult found = GenerationSemanticQueries.nearestRiver(
                engine, HydrologyFeatureType.CAVE_POOL, 0, 0, 100).orElseThrow();

        assertEquals(GenerationSemanticQueries.RiverSource.RECORDED, found.source());
        assertEquals(40, found.x());
        assertEquals(-20, found.y());
        verify(runtime, never()).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any());

        when(history.findRecordedRiver(any())).thenReturn(Optional.empty());
        assertTrue(GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.CAVE_POOL, 0, 0, 100).isEmpty());
        verify(runtime, never()).nearestFeature(anyInt(), anyInt(), any(), anyInt(), any());
        assertTrue(GenerationSemanticQueries.riverNotFoundMessage(HydrologyFeatureType.CAVE_POOL, 100)
                .startsWith("No cave pool found in generated chunks within 100 blocks."));
        assertTrue(GenerationSemanticQueries.riverSearchMessage(HydrologyFeatureType.CAVE_POOL, 100).contains("generated chunks"));
        assertEquals("No river found within 100 blocks.", GenerationSemanticQueries.riverNotFoundMessage(HydrologyFeatureType.RIVER, 100));
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
    public void plannerFailureEscapesInsteadOfReportingNothingFound() {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        IllegalStateException failure = new IllegalStateException("window planner exploded");
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.nearestFeature(anyInt(), anyInt(), any(), anyInt(), any())).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> GenerationSemanticQueries.nearestRiver(engine, HydrologyFeatureType.LAKE, 0, 0, 100));

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

    private static HydrologyFeatureRef feature(long id, int x, int y, int z) {
        return new HydrologyFeatureRef(id, HydrologyFeatureType.RIVER, x, y, z);
    }
}
