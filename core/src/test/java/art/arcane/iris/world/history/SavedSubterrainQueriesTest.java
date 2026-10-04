package art.arcane.iris.world.history;

import art.arcane.iris.generation.locator.SubterrainLocator;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.subterrain.SubterrainPosition;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SavedSubterrainQueriesTest {
    @Test
    public void locateUsesRetainedDefinitionOnlyInsideItsOwnedChunks() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        GenerationManifest manifest = mock(GenerationManifest.class);
        GenerationActivation old = GenerationActivation.initial("a".repeat(64), 1L);
        GenerationActivation active = GenerationActivation.next(2L, "b".repeat(64), 1L, 2L, 64);
        SubterrainPlanner historical = planner("old-basin");
        SubterrainPlan plan = historical.plansForBounds(0, 0, 511, 511).getFirst();
        SubterrainPosition anchor = plan.anchor();
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(router.biomes()).thenReturn(saved);
        when(history.manifest()).thenReturn(manifest);
        when(manifest.activations()).thenReturn(List.of(old, active));
        when(manifest.pendingActivation()).thenReturn(Optional.empty());
        when(saved.subterrainPlanner(1L)).thenReturn(historical);
        when(saved.subterrainPlanner(2L)).thenReturn(planner("new-basin"));
        when(history.resolveActivation(anyInt(), anyInt())).thenReturn(active);
        when(history.resolveActivation(Math.floorDiv(anchor.x(), 16), Math.floorDiv(anchor.z(), 16))).thenReturn(old);
        SubterrainLocator.Query query = new SubterrainLocator.Query("old-basin", null, "");

        SubterrainLocator.Result found = GenerationSemanticQueries.nearestSubterrain(engine, query,
                anchor.x(), anchor.y(), anchor.z(), 256).orElseThrow();
        assertEquals(plan.id(), found.featureId());
        assertEquals(anchor.y(), found.y());
        verify(engine, never()).getMantle();
        verify(router, never()).openRoute(anyInt(), anyInt());
        verify(router, never()).openReadScope(anyInt(), anyInt());

        when(history.resolveActivation(anyInt(), anyInt())).thenReturn(active);
        assertTrue(GenerationSemanticQueries.nearestSubterrain(engine, query,
                anchor.x(), anchor.y(), anchor.z(), 256).isEmpty());
    }

    @Test
    public void newPredictionCannotCrossProtectedGenerationTransition() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        GenerationManifest manifest = mock(GenerationManifest.class);
        GenerationActivation active = GenerationActivation.initial("a".repeat(64), 1L);
        SubterrainPlanner planner = planner("basin");
        SubterrainPosition anchor = planner.plansForBounds(0, 0, 511, 511).getFirst().anchor();
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(router.biomes()).thenReturn(saved);
        when(history.manifest()).thenReturn(manifest);
        when(manifest.activations()).thenReturn(List.of(active));
        when(manifest.pendingActivation()).thenReturn(Optional.empty());
        when(saved.subterrainPlanner(1L)).thenReturn(planner);
        when(history.resolveActivation(anyInt(), anyInt())).thenReturn(active);
        when(history.isActiveUnowned(anyInt(), anyInt())).thenReturn(true);
        SubterrainLocator.Query query = new SubterrainLocator.Query("basin", null, "");
        assertTrue(GenerationSemanticQueries.nearestSubterrain(engine, query,
                anchor.x(), anchor.y(), anchor.z(), 64).isEmpty());
        when(complex.allowsNewDiscreteContentAt(anchor.x(), anchor.z())).thenReturn(true);
        assertTrue(GenerationSemanticQueries.nearestSubterrain(engine, query,
                anchor.x(), anchor.y(), anchor.z(), 64).isPresent());
        verify(engine, never()).getMantle();
    }

    private static SubterrainPlanner planner(String id) {
        IrisSubterrainFeature feature = new IrisSubterrainFeature().setId(id).setProbability(1D);
        return new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 1191L, -64, 320));
    }
}
