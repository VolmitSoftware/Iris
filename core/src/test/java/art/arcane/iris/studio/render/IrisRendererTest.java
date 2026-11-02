package art.arcane.iris.studio.render;

import art.arcane.iris.generation.hydrology.engine.HydrologyPlanPreview;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisRendererTest {
    private static final int PIXELS = 32;
    private static final double STEP = 4D;

    @Test
    public void cavePoolsRefreshOnRevision() {
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        AtomicLong preview = new AtomicLong(7L);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(HydrologyPlanPreview.EMPTY);
        when(runtime.previewRevision()).thenAnswer(invocation -> preview.get());
        AtomicBoolean recorded = new AtomicBoolean();
        AtomicLong water = new AtomicLong(3L);
        IrisEngine engine = engine(runtime, recorded, water);
        IrisRenderer.WaterTile tile = new IrisRenderer(engine).prepareStudioWater(0D, 0D, STEP, PIXELS);
        int pool = IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.CAVE_POOL) & 0xFFFFFF;
        int index = (24 / (int) STEP) * PIXELS + 20 / (int) STEP;

        int[] before = tile.render(null, false, () -> false);
        assertTrue(tile.complete());
        assertNotEquals(pool, before[index]);

        preview.incrementAndGet();
        int[] rasterLanded = tile.render(null, false, () -> false);
        assertNotEquals("the chunk's semantics are not recorded yet", pool, rasterLanded[index]);

        recorded.set(true);
        int[] unannounced = tile.render(null, false, () -> false);
        assertNotEquals("pools refresh only when the recorded water revision moves", pool, unannounced[index]);

        water.incrementAndGet();
        int[] refreshed = tile.render(null, false, () -> false);
        assertEquals(pool, refreshed[index]);
    }

    private static IrisEngine engine(HydrologyRuntime runtime, AtomicBoolean recorded, AtomicLong water) {
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.riverRevision()).thenAnswer(invocation -> water.get());
        ChunkGenerationSemantics pool = ChunkGenerationSemantics.builder(1, 1, 1L)
                .addRiverFeature("water", HydrologyFeatureType.CAVE_POOL, 7L, 20, 12, 24).build();
        when(history.semantics(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(history.semantics(1, 1)).thenAnswer(invocation -> recorded.get() ? Optional.of(pool) : Optional.empty());
        return engine;
    }
}
