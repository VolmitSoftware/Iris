package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.generation.hydrology.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.terrain.IrisDimension;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doAnswer;
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
        IrisHydrologyRuntime runtime = mock(IrisHydrologyRuntime.class);
        IrisDimension dimension = mock(IrisDimension.class);
        TransitionGenerationPlan transition = mock(TransitionGenerationPlan.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationActivation activation = mock(GenerationActivation.class);
        HydrologyFeatureRef tapered = feature(1L, 8, 20, 0);
        HydrologyFeatureRef eligible = feature(2L, 40, 20, 0);
        ChunkGenerationSemantics.RiverFeatureOccurrence recordedOccurrence =
                new ChunkGenerationSemantics.RiverFeatureOccurrence(
                        "iris:river",
                        HydrologyFeatureType.RIFFLE,
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
        when(complex.getTransitionGenerationPlan()).thenReturn(transition);
        when(complex.allowsNewGenerationChunk(anyInt(), anyInt())).thenReturn(true);
        when(transition.hydrologyWeightAt(anyInt(), anyInt())).thenAnswer(invocation ->
                invocation.<Integer>getArgument(0) == tapered.x() ? 0.5D : 1D);
        when(dimension.getMinHeight()).thenReturn(-64);
        when(router.history()).thenReturn(history);
        when(history.findRecordedRiver(any())).thenReturn(Optional.of(recorded));
        when(history.isActiveUnowned(anyInt(), anyInt())).thenReturn(true);
        when(history.semantics(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(history.activeActivation()).thenReturn(activation);
        when(activation.activationId()).thenReturn(2L);
        when(runtime.nearestFeature(
                any(), nullable(String.class), anyInt(), anyInt(), anyInt(), any(), any()
        )).thenAnswer(invocation -> firstEligible(
                List.of(tapered, eligible),
                invocation.getArgument(5)
        ));

        GenerationSemanticQueries.RiverResult active = GenerationSemanticQueries.nearestRiver(
                engine,
                HydrologyFeatureQuery.parse("surface"),
                0,
                0,
                200,
                ignored -> { }
        ).orElseThrow();

        assertEquals(GenerationSemanticQueries.RiverSource.ACTIVE_PREDICTION, active.source());
        assertEquals(40, active.x());
        assertEquals(-44, active.y());

        HydrologyFeatureRef fartherActive = feature(4L, 120, 20, 0);
        doAnswer(invocation -> firstEligible(
                List.of(tapered, fartherActive),
                invocation.getArgument(5)
        )).when(runtime).nearestFeature(
                any(), nullable(String.class), anyInt(), anyInt(), anyInt(), any(), any()
        );

        GenerationSemanticQueries.RiverResult historical = GenerationSemanticQueries.nearestRiver(
                engine,
                HydrologyFeatureQuery.parse("surface"),
                0,
                0,
                200,
                ignored -> { }
        ).orElseThrow();

        assertEquals(GenerationSemanticQueries.RiverSource.RECORDED, historical.source());
        assertEquals(100, historical.x());
        assertEquals(70, historical.y());
    }

    @Test
    public void returnsRecordedRiverWhenTheActivePackHasNoHydrologyRuntime() {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        ChunkGenerationSemantics.RiverFeatureOccurrence occurrence =
                new ChunkGenerationSemantics.RiverFeatureOccurrence(
                        "iris:legacy_river",
                        HydrologyFeatureType.WATERFALL,
                        9L,
                        new ChunkGenerationSemantics.BlockPosition(-30, 55, 12)
                );

        when(engine.getComplex()).thenReturn(complex);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.findRecordedRiver(any())).thenReturn(Optional.of(
                new GenerationSemanticIndex.RiverMatch(
                        occurrence,
                        new GenerationSemanticIndex.ChunkReference(-2, 0, 1L)
                )
        ));

        GenerationSemanticQueries.RiverResult result = GenerationSemanticQueries.nearestRiver(
                engine,
                HydrologyFeatureQuery.parse("waterfall"),
                0,
                0,
                100,
                ignored -> { }
        ).orElseThrow();

        assertEquals(GenerationSemanticQueries.RiverSource.RECORDED, result.source());
        assertEquals(HydrologyFeatureType.WATERFALL, result.type());
        assertEquals("iris:legacy_river", result.profileKey().orElseThrow());
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
        return new HydrologyFeatureRef(
                id,
                HydrologyFeatureType.RIFFLE,
                10L,
                11L,
                x,
                y,
                z,
                1,
                0,
                false
        );
    }
}
