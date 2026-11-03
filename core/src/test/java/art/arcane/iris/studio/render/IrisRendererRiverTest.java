package art.arcane.iris.studio.render;

import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.engine.HydrologyPlanPreview;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.testsupport.WaterScene;
import org.junit.Test;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisRendererRiverTest {
    private static final int SEA_LEVEL = 40;
    private static final int NO_RIVER = new Color(28, 31, 38).getRGB() & 0xFFFFFF;
    private static final int GREEN = Color.GREEN.getRGB() & 0xFFFFFF;
    private static final int PIXELS = 64;
    private static final long SETTLE_SECONDS = 10L;
    private static final int POND_X = 30;
    private static final int POND_Z = 30;
    private static final int LAKE_X = 90;
    private static final int LAKE_Z = 30;
    private static final int STREAM_X = 40;
    private static final int STREAM_Z = 100;
    private static final int DRY = 120;

    @Test
    public void everyFeatureTypeHasItsOwnColor() {
        Set<Integer> colors = new HashSet<>();
        for (HydrologyFeatureType type : HydrologyFeatureType.values()) {
            assertTrue(type.name(), colors.add(water(type)));
        }
        assertFalse(colors.contains(NO_RIVER));
    }

    @Test
    public void legendListsTheWaterTheMapDrawsStrongestFirst() {
        List<HydrologyFeatureType> drawn = IrisRenderer.mapWaterTypes();

        assertEquals(HydrologyFeatureType.WATERFALL, drawn.getFirst());
        assertEquals("cave pools sit under everything else", HydrologyFeatureType.CAVE_POOL, drawn.getLast());
        assertTrue(drawn.containsAll(List.of(HydrologyFeatureType.STREAM, HydrologyFeatureType.RIVER, HydrologyFeatureType.MAJOR_RIVER,
                HydrologyFeatureType.LAKE, HydrologyFeatureType.POND, HydrologyFeatureType.DAM, HydrologyFeatureType.SEA_INLET)));
        assertTrue(drawn.indexOf(HydrologyFeatureType.STREAM) < drawn.indexOf(HydrologyFeatureType.POND));
        assertTrue(drawn.indexOf(HydrologyFeatureType.POND) < drawn.indexOf(HydrologyFeatureType.LAKE));
    }

    @Test
    public void provisionalRastersRenderDryAndMarkTheTileUntilTheyLand() throws Exception {
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, 63)) {
            Engine engine = engine(hydrology.runtime);
            hydrology.hold();
            long before = ProvisionalSampling.revision();

            int[] provisional = new IrisRenderer(engine).prepareStudioWater(0D, 0D, 1D, 64).render(null, false, () -> false);

            assertNotEquals(before, ProvisionalSampling.revision());
            assertEquals(NO_RIVER, at(provisional, 64, 16, 16));
            hydrology.release();

            int[] planned = settled(engine, 64D, 64);

            assertEquals(water(HydrologyFeatureType.POND), at(planned, 64, 16, 16));
            assertEquals(NO_RIVER, at(planned, 64, 48, 48));
        }
    }

    @Test
    public void zoomKeepsResidentWaterPlansUntilDetailedRastersAreReady() {
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        Pond pond = WaterScene.pond(32L, 62, 16, 16, 10);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new HydrologyPlanPreview(List.of(), List.of(pond), List.of(), true));
        when(runtime.rasterPreview(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.PROVISIONAL);
        IrisRenderer renderer = new IrisRenderer(engine(runtime));

        int[] overview = renderer.prepareStudioWater(0D, 0D, 4D, 64).render(null, false, () -> false);
        int water = water(HydrologyFeatureType.POND);
        assertEquals(water, at(overview, 64, 4, 4));

        for (int step : new int[]{2, 1}) {
            long before = ProvisionalSampling.revision();
            int[] detailed = renderer.prepareStudioWater(0D, 0D, step, 64).render(null, false, () -> false);
            assertEquals(water, at(detailed, 64, 16 / step, 16 / step));
            assertNotEquals(before, ProvisionalSampling.revision());
        }

        when(runtime.rasterPreview(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);
        long before = ProvisionalSampling.revision();
        int[] settled = renderer.prepareStudioWater(0D, 0D, 1D, 64).render(null, false, () -> false);
        assertEquals(NO_RIVER, at(settled, 64, 16, 16));
        assertEquals(before, ProvisionalSampling.revision());
    }

    @Test
    public void channelPixelsShowTheirTier() throws Exception {
        HydrologySettings settings = FakeHydrology.settings(SEA_LEVEL);
        Reach brook = WaterScene.line(11L, settings.tiers().getFirst(), 2, 40, 29, 40, (x, z) -> 62);
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, List.of(brook), List.of())) {
            int[] colors = settled(engine(hydrology.runtime), 32D, 32D, 32, null);

            assertEquals(water(HydrologyFeatureType.STREAM), at(colors, 32, 16, 8));
            assertEquals(NO_RIVER, at(colors, 32, 16, 0));
        }
    }

    @Test
    public void closeZoomDrawsWaterFromRasters() throws Exception {
        try (FakeHydrology hydrology = scene()) {
            HydrologyRuntime runtime = spy(hydrology.runtime);

            assertScene(settled(engine(runtime), 2D * PIXELS, PIXELS), 2);
            verify(runtime, atLeastOnce()).rasterPreview(anyInt(), anyInt());
            verify(runtime, never()).rasterSnapshot(anyInt(), anyInt());
        }
    }

    @Test
    public void fourBlocksPerPixelDrawsResidentPlansWithoutRasters() throws Exception {
        try (FakeHydrology hydrology = scene()) {
            HydrologyRuntime runtime = spy(hydrology.runtime);

            assertScene(settled(engine(runtime), 4D * PIXELS, PIXELS), 4);
            assertNoRasters(runtime);
        }
    }

    @Test
    public void sixteenBlocksPerPixelDrawsResidentPlansWithoutRasters() throws Exception {
        try (FakeHydrology hydrology = scene()) {
            HydrologyRuntime runtime = spy(hydrology.runtime);

            assertScene(settled(engine(runtime), 16D * PIXELS, PIXELS), 16);
            assertNoRasters(runtime);
        }
    }

    @Test
    public void planModeDrawsTidalReachesAsSeaInlets() throws Exception {
        int points = 26;
        float[] xs = new float[points];
        float[] zs = new float[points];
        int[] grade = new int[points];
        float[] halfWidth = new float[points];
        float[] depth = new float[points];
        ReachStyle[] style = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            xs[point] = 100f + point * 8f;
            zs[point] = 200f;
            grade[point] = SEA_LEVEL;
            halfWidth[point] = 4f;
            depth[point] = 3f;
            style[point] = ReachStyle.TIDAL;
        }
        Reach tidal = new Reach(51L, FakeHydrology.settings(SEA_LEVEL).tidalTier(), 0L, xs, zs, grade, halfWidth, depth, style, 0f);
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 90, SEA_LEVEL, List.of(tidal), List.of())) {
            HydrologyRuntime runtime = spy(hydrology.runtime);
            int[] plan = settled(engine(runtime), 16D * PIXELS, PIXELS);
            assertEquals(water(HydrologyFeatureType.SEA_INLET), at(plan, PIXELS, 200 / 16, 200 / 16));
            assertEquals(NO_RIVER, at(plan, PIXELS, 200 / 16, 400 / 16));
            assertNoRasters(runtime);
        }
    }

    @Test
    public void standingWaterCoversChannelsInRasters() throws Exception {
        HydrologySettings settings = FakeHydrology.settings(SEA_LEVEL);
        Reach through = WaterScene.line(31L, settings.tiers().getFirst(), 4, POND_Z, 60, POND_Z, (x, z) -> 62);
        Pond pond = WaterScene.pond(32L, 62, POND_X, POND_Z, 10);
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, List.of(through), List.of(pond))) {
            int[] colors = settled(engine(hydrology.runtime), 2D * PIXELS, PIXELS);

            assertEquals(water(HydrologyFeatureType.POND), at(colors, PIXELS, POND_X / 2, POND_Z / 2));
            assertEquals(water(HydrologyFeatureType.STREAM), at(colors, PIXELS, 4, POND_Z / 2));
        }
    }

    @Test
    public void zoomedOutPixelsKeepTheStrongestWaterInTheirFootprint() throws Exception {
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, 63)) {
            int[] colors = settled(engine(hydrology.runtime), 32D, 2);

            assertEquals(water(HydrologyFeatureType.POND), at(colors, 2, 0, 0));
            assertEquals(water(HydrologyFeatureType.POND), at(colors, 2, 1, 1));
        }
    }

    @Test
    public void cavePoolsAppearOnlyWhereHistoryRecordedThem() throws Exception {
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, List.of(), List.of())) {
            IrisEngine engine = mock(IrisEngine.class);
            IrisComplex complex = mock(IrisComplex.class);
            when(engine.getComplex()).thenReturn(complex);
            when(complex.getHydrologyRuntime()).thenReturn(hydrology.runtime);
            GenerationHistory history = mock(GenerationHistory.class);
            GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
            when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
            when(router.history()).thenReturn(history);
            when(history.semantics(anyInt(), anyInt())).thenReturn(Optional.empty());
            when(history.semantics(1, 1)).thenReturn(Optional.of(ChunkGenerationSemantics.builder(1, 1, 1L)
                    .addRiverFeature("water", HydrologyFeatureType.CAVE_POOL, 7L, 20, 12, 24).build()));

            int[] colors = settled(engine, 64D, 32);

            assertEquals(water(HydrologyFeatureType.CAVE_POOL), at(colors, 32, 10, 12));
            assertEquals(NO_RIVER, at(colors, 32, 20, 20));
            assertEquals(NO_RIVER, at(colors, 32, 10, 13));
        }
    }

    @Test
    public void riverMapsBeyondTheMaximumZoomDoNotQueueRasters() {
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        when(runtime.enabled()).thenReturn(true);
        long before = ProvisionalSampling.revision();

        int[] colors = new IrisRenderer(engine(runtime)).prepareStudioWater(0D, 0D, 32D, 64).render(null, false, () -> false);

        assertEquals(NO_RIVER, at(colors, 64, 0, 0));
        assertEquals(before, ProvisionalSampling.revision());
        verify(runtime, never()).planPreview(anyInt(), anyInt(), anyInt(), anyInt());
        assertNoRasters(runtime);
    }

    @Test
    public void disabledHydrologyLeavesTheRiverMapEmpty() {
        long before = ProvisionalSampling.revision();

        int[] colors = new IrisRenderer(engine(FakeHydrology.disabled())).prepareStudioWater(0D, 0D, 1D, 32)
                .render(null, false, () -> false);

        assertEquals(NO_RIVER, at(colors, 32, 16, 16));
        assertEquals(before, ProvisionalSampling.revision());
    }

    @Test
    public void compositeTilesBlendThePlannedWaterOverTheBase() throws Exception {
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, 63)) {
            int[] composite = settled(engine(hydrology.runtime), 64D, 64, green(64));

            assertNotEquals(GREEN, at(composite, 64, 16, 16));
            assertNotEquals(water(HydrologyFeatureType.POND), at(composite, 64, 16, 16));
            assertEquals(GREEN, at(composite, 64, 48, 48));
        }
    }

    @Test
    public void compositeTilesShowWaterAtTheDefaultZoomWithoutRasters() throws Exception {
        try (FakeHydrology hydrology = scene()) {
            HydrologyRuntime runtime = spy(hydrology.runtime);

            int[] composite = settled(engine(runtime), 4D * PIXELS, PIXELS, green(PIXELS));

            assertNotEquals(GREEN, at(composite, PIXELS, POND_X / 4, POND_Z / 4));
            assertNotEquals(GREEN, at(composite, PIXELS, LAKE_X / 4, LAKE_Z / 4));
            assertNotEquals(GREEN, at(composite, PIXELS, STREAM_X / 4, STREAM_Z / 4));
            assertEquals(GREEN, at(composite, PIXELS, DRY / 4, DRY / 4));
            assertNoRasters(runtime);
        }
    }

    private static FakeHydrology scene() {
        HydrologySettings settings = FakeHydrology.settings(SEA_LEVEL);
        Reach brook = WaterScene.line(21L, settings.tiers().getFirst(), 4, STREAM_Z, 60, STREAM_Z, (x, z) -> 62);
        Pond pond = WaterScene.pond(22L, 62, POND_X, POND_Z, 10);
        Pond lake = WaterScene.pond(23L, 62, LAKE_X, LAKE_Z, 12);
        return new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, List.of(brook), List.of(pond), List.of(lake));
    }

    private static void assertScene(int[] colors, int step) {
        assertEquals(water(HydrologyFeatureType.POND), at(colors, PIXELS, POND_X / step, POND_Z / step));
        assertEquals(water(HydrologyFeatureType.LAKE), at(colors, PIXELS, LAKE_X / step, LAKE_Z / step));
        assertEquals(water(HydrologyFeatureType.STREAM), at(colors, PIXELS, STREAM_X / step, STREAM_Z / step));
        assertEquals(NO_RIVER, at(colors, PIXELS, DRY / step, DRY / step));
    }

    private static void assertNoRasters(HydrologyRuntime runtime) {
        verify(runtime, never()).rasterPreview(anyInt(), anyInt());
        verify(runtime, never()).rasterSnapshot(anyInt(), anyInt());
        verify(runtime, never()).raster(anyInt(), anyInt());
    }

    private static int water(HydrologyFeatureType type) {
        return IrisRenderer.hydrologyFeatureColor(type) & 0xFFFFFF;
    }

    private static int at(int[] colors, int pixels, int x, int z) {
        return colors[z * pixels + x];
    }

    private static int[] green(int pixels) {
        int[] base = new int[pixels * pixels];
        Arrays.fill(base, GREEN);
        return base;
    }

    private static int[] settled(Engine engine, double span, int pixels) throws InterruptedException {
        return settled(engine, 0D, span, pixels, null);
    }

    private static int[] settled(Engine engine, double span, int pixels, int[] base) throws InterruptedException {
        return settled(engine, 0D, span, pixels, base);
    }

    private static int[] settled(Engine engine, double startZ, double span, int pixels, int[] base) throws InterruptedException {
        IrisRenderer.WaterTile tile = new IrisRenderer(engine).prepareStudioWater(0D, startZ, span / pixels, pixels);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS);
        while (true) {
            long revision = ProvisionalSampling.revision();
            int[] colors = tile.render(base, base != null, () -> false);
            if (ProvisionalSampling.revision() == revision) {
                return colors;
            }
            assertTrue("The water map never settled", System.nanoTime() < deadline);
            Thread.sleep(10L);
        }
    }

    private static Engine engine(HydrologyRuntime runtime) {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        return engine;
    }
}
