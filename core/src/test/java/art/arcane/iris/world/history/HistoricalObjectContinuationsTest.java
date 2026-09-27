package art.arcane.iris.world.history;

import art.arcane.iris.generation.mantle.ObjectContinuationBundle;
import art.arcane.volmlib.util.matter.IrisMatter;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HistoricalObjectContinuationsTest {
    @Test
    public void keepsOnlySavedOriginPlacementsAndCachesRegionReads() throws Exception {
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationManifest manifest = mock(GenerationManifest.class);
        GenerationActivation first = activation(1L);
        GenerationActivation second = activation(2L);
        GenerationActivation third = activation(3L);
        when(history.manifest()).thenReturn(manifest);
        when(manifest.activations()).thenReturn(List.of(first, second, third));
        when(history.resolveActivation(0, 0)).thenReturn(first);
        TransitionGenerationPlan plan = plan(3L);
        ObjectContinuationBundle.Fragment crossing = fragment(0, List.of(chunk(0), chunk(1)));
        ObjectContinuationBundle.Fragment unrelated = fragment(1, List.of(chunk(1), chunk(2)));
        AtomicInteger reads = new AtomicInteger();
        HistoricalObjectContinuations continuations = new HistoricalObjectContinuations(history, (activation, x, z) -> {
            reads.incrementAndGet();
            return Map.of(ChunkGenerationOwnership.packChunk(1, 0),
                    new ObjectContinuationBundle(List.of(crossing, unrelated)));
        });

        assertEquals(List.of(new GenerationHistoryRuntimeRouter.ObjectContinuation(1L, crossing)), continuations.at(plan, 1, 0));
        assertEquals(List.of(new GenerationHistoryRuntimeRouter.ObjectContinuation(1L, crossing)), continuations.at(plan, 1, 0));
        assertEquals(2, reads.get());
        assertTrue(continuations.at(plan, 0, 0).isEmpty());
        assertFalse(continuations.allowsFootprint(plan, 17, 1, 18, 2));
        assertTrue(continuations.allowsFootprint(plan, 24, 1, 25, 2));
        assertEquals(2, reads.get());
    }

    @Test
    public void originalPendingFragmentSurvivesAnIntermediateActivation() throws Exception {
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationManifest manifest = mock(GenerationManifest.class);
        GenerationActivation first = activation(1L);
        GenerationActivation second = activation(2L);
        GenerationActivation third = activation(3L);
        when(history.manifest()).thenReturn(manifest);
        when(manifest.activations()).thenReturn(List.of(first, second, third));
        when(history.resolveActivation(0, 0)).thenReturn(first);
        ObjectContinuationBundle.Fragment crossing = fragment(0, List.of(chunk(0), chunk(1), chunk(2)));
        HistoricalObjectContinuations continuations = new HistoricalObjectContinuations(history, (activation, x, z) ->
                activation.activationId() == 1L
                        ? Map.of(ChunkGenerationOwnership.packChunk(2, 0), new ObjectContinuationBundle(List.of(crossing)))
                        : Map.of());

        assertEquals(List.of(new GenerationHistoryRuntimeRouter.ObjectContinuation(1L, crossing)), continuations.at(plan(2L), 2, 0));
        assertEquals(List.of(new GenerationHistoryRuntimeRouter.ObjectContinuation(1L, crossing)), continuations.at(plan(3L), 2, 0));
    }

    @Test
    public void continuationReplayUsesSourceOrderInsteadOfConcurrentPersistenceOrder() throws Exception {
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationManifest manifest = mock(GenerationManifest.class);
        GenerationActivation first = activation(1L);
        when(history.manifest()).thenReturn(manifest);
        GenerationActivation second = activation(2L);
        when(manifest.activations()).thenReturn(List.of(first, second));
        when(history.resolveActivation(0, 0)).thenReturn(first);
        ObjectContinuationBundle.Fragment earlier = fragment(0, List.of(chunk(0), chunk(1)));
        ObjectContinuationBundle.Fragment later = fragment(1, List.of(chunk(0), chunk(1)));
        ObjectContinuationBundle.Fragment structure = new ObjectContinuationBundle.Fragment(
                new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.STRUCTURE, -1, 0, 0),
                earlier.bounds(), earlier.touchedChunks(), earlier.payload());
        HistoricalObjectContinuations continuations = new HistoricalObjectContinuations(history, (activation, x, z) ->
                Map.of(ChunkGenerationOwnership.packChunk(1, 0), new ObjectContinuationBundle(List.of(structure, later, earlier))));

        assertEquals(List.of(new GenerationHistoryRuntimeRouter.ObjectContinuation(1L, earlier),
                        new GenerationHistoryRuntimeRouter.ObjectContinuation(1L, later),
                        new GenerationHistoryRuntimeRouter.ObjectContinuation(1L, structure)), continuations.at(plan(2L), 1, 0));
    }

    private static GenerationActivation activation(long id) {
        GenerationActivation activation = mock(GenerationActivation.class);
        when(activation.activationId()).thenReturn(id);
        return activation;
    }

    private static TransitionGenerationPlan plan(long id) {
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        when(plan.activationId()).thenReturn(id);
        when(plan.boundary()).thenReturn(GenerationBoundary.freeze("frontier", List.of(new GenerationBoundary.ChunkCoordinate(0, 0))));
        return plan;
    }

    private static ObjectContinuationBundle.ChunkPosition chunk(int x) {
        return new ObjectContinuationBundle.ChunkPosition(x, 0);
    }

    private static ObjectContinuationBundle.Fragment fragment(int ordinal, List<ObjectContinuationBundle.ChunkPosition> touched) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        new IrisMatter(16, 64, 16).write(payload);
        return new ObjectContinuationBundle.Fragment(new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.BIOME, 0, 0, ordinal),
                new ObjectContinuationBundle.Bounds(15, 1, 20, 3), touched, payload.toByteArray());
    }
}
