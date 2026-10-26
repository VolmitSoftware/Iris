package art.arcane.iris.studio.render;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.engine.HydrologyPlanPreview;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.testsupport.WaterScene;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisRendererRiverTest {
    private static final int SEA_LEVEL = 40;
    private static final int NO_RIVER = new Color(28, 31, 38).getRGB();
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
            assertTrue(type.name(), colors.add(IrisRenderer.hydrologyFeatureColor(type)));
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
        assertTrue(drawn.indexOf(HydrologyFeatureType.LAKE) < drawn.indexOf(HydrologyFeatureType.POND));
    }

    @Test
    public void provisionalRastersRenderDryAndMarkTheTileUntilTheyLand() throws Exception {
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, 63)) {
            Engine engine = engine(hydrology.runtime);
            hydrology.hold();
            long before = ProvisionalSampling.revision();

            BufferedImage provisional = new IrisRenderer(engine).renderStudio(0D, 0D, 64D, 64, RenderType.RIVER, () -> false);

            assertNotEquals(before, ProvisionalSampling.revision());
            assertEquals(NO_RIVER, provisional.getRGB(16, 16));
            hydrology.release();

            BufferedImage planned = settled(engine, 64D, 64, RenderType.RIVER);

            assertEquals(IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.POND), planned.getRGB(16, 16));
            assertEquals(NO_RIVER, planned.getRGB(48, 48));
        }
    }

    @Test
    public void zoomKeepsResidentWaterPlansUntilDetailedRastersAreReady() {
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        Pond pond = WaterScene.pond(32L, 62, 16, 16, 10);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new HydrologyPlanPreview(List.of(), List.of(pond), List.of(), List.of(), true));
        when(runtime.rasterPreview(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.PROVISIONAL);
        IrisRenderer renderer = new IrisRenderer(engine(runtime));

        BufferedImage overview = renderer.renderStudio(0D, 0D, 256D, 64, RenderType.RIVER, () -> false);
        int water = IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.POND);
        assertEquals(water, overview.getRGB(4, 4));

        for (int step : new int[]{2, 1}) {
            long before = ProvisionalSampling.revision();
            BufferedImage detailed = renderer.renderStudio(0D, 0D, step * 64D, 64, RenderType.RIVER, () -> false);
            assertEquals(water, detailed.getRGB(16 / step, 16 / step));
            assertNotEquals(before, ProvisionalSampling.revision());
        }

        when(runtime.rasterPreview(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);
        long before = ProvisionalSampling.revision();
        BufferedImage settled = renderer.renderStudio(0D, 0D, 64D, 64, RenderType.RIVER, () -> false);
        assertEquals(NO_RIVER, settled.getRGB(16, 16));
        assertEquals(before, ProvisionalSampling.revision());
    }

    @Test
    public void channelPixelsShowTheirTier() throws Exception {
        HydrologySettings settings = FakeHydrology.settings(SEA_LEVEL);
        Reach brook = WaterScene.line(11L, settings.tiers().getFirst(), 2, 40, 29, 40, (x, z) -> 62);
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, List.of(brook), List.of())) {
            Engine engine = engine(hydrology.runtime);

            BufferedImage image = settled(engine, 32D, 32D, 32, RenderType.RIVER);

            assertEquals(IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.STREAM), image.getRGB(16, 8));
            assertEquals(NO_RIVER, image.getRGB(16, 0));
        }
    }

    @Test
    public void closeZoomDrawsWaterFromRasters() throws Exception {
        try (FakeHydrology hydrology = scene()) {
            HydrologyRuntime runtime = spy(hydrology.runtime);

            assertScene(settled(engine(runtime), 2D * PIXELS, PIXELS, RenderType.RIVER), 2);
            verify(runtime, atLeastOnce()).rasterPreview(anyInt(), anyInt());
            verify(runtime, never()).rasterSnapshot(anyInt(), anyInt());
        }
    }

    @Test
    public void fourBlocksPerPixelDrawsResidentPlansWithoutRasters() throws Exception {
        try (FakeHydrology hydrology = scene()) {
            HydrologyRuntime runtime = spy(hydrology.runtime);

            assertScene(settled(engine(runtime), 4D * PIXELS, PIXELS, RenderType.RIVER), 4);
            assertNoRasters(runtime);
        }
    }

    @Test
    public void sixteenBlocksPerPixelDrawsResidentPlansWithoutRasters() throws Exception {
        try (FakeHydrology hydrology = scene()) {
            HydrologyRuntime runtime = spy(hydrology.runtime);

            assertScene(settled(engine(runtime), 16D * PIXELS, PIXELS, RenderType.RIVER), 16);
            assertNoRasters(runtime);
        }
    }

    @Test
    public void planModeDrawsSeaInletPathsUnderground() throws Exception {
        int stations = 26;
        float[] xs = new float[stations];
        float[] zs = new float[stations];
        float[] halfWidth = new float[stations];
        int[] floor = new int[stations];
        int[] ceiling = new int[stations];
        for (int station = 0; station < stations; station++) {
            xs[station] = 100f + station * 8f;
            zs[station] = 200f;
            halfWidth[station] = 4f;
            floor[station] = SEA_LEVEL - 3;
            ceiling[station] = SEA_LEVEL + 5;
        }
        SeaInlet inlet = new SeaInlet(51L, xs, zs, halfWidth, SEA_LEVEL, floor, ceiling, 300, 200, 8);
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 90, SEA_LEVEL, List.of(), List.of(), List.of(),
                List.of(inlet))) {
            HydrologyRuntime runtime = spy(hydrology.runtime);
            BufferedImage plan = settled(engine(runtime), 16D * PIXELS, PIXELS, RenderType.RIVER);
            assertEquals(IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.SEA_INLET), plan.getRGB(200 / 16, 200 / 16));
            assertEquals(NO_RIVER, plan.getRGB(200 / 16, 400 / 16));
            assertNoRasters(runtime);
        }
    }

    @Test
    public void standingWaterCoversChannelsInBothDrawModes() throws Exception {
        HydrologySettings settings = FakeHydrology.settings(SEA_LEVEL);
        Reach through = WaterScene.line(31L, settings.tiers().getFirst(), 4, POND_Z, 60, POND_Z, (x, z) -> 62);
        Pond pond = WaterScene.pond(32L, 62, POND_X, POND_Z, 10);
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, List.of(through), List.of(pond))) {
            Engine engine = engine(hydrology.runtime);

            for (int step : new int[]{2, 4}) {
                BufferedImage image = settled(engine, (double) step * PIXELS, PIXELS, RenderType.RIVER);

                assertEquals("pond over the channel at " + step + " b/px", IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.POND),
                        image.getRGB(POND_X / step, POND_Z / step));
                assertEquals("channel outside the pond at " + step + " b/px",
                        IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.STREAM), image.getRGB(8 / step, POND_Z / step));
            }
        }
    }

    @Test
    public void zoomedOutPixelsKeepTheStrongestWaterInTheirFootprint() throws Exception {
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, 63)) {
            Engine engine = engine(hydrology.runtime);

            BufferedImage image = settled(engine, 32D, 2, RenderType.RIVER);

            assertEquals(IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.POND), image.getRGB(0, 0));
            assertEquals(IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.POND), image.getRGB(1, 1));
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

            BufferedImage image = settled(engine, 64D, 32, RenderType.RIVER);

            assertEquals(IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.CAVE_POOL), image.getRGB(10, 12));
            assertEquals(NO_RIVER, image.getRGB(20, 20));
            assertEquals(NO_RIVER, image.getRGB(10, 13));
        }
    }

    @Test
    public void riverMapsBeyondTheMaximumZoomDoNotQueueRasters() {
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        when(runtime.enabled()).thenReturn(true);
        long before = ProvisionalSampling.revision();

        BufferedImage image = new IrisRenderer(engine(runtime)).renderStudio(0D, 0D, 2048D, 64, RenderType.RIVER, () -> false);

        assertEquals(NO_RIVER, image.getRGB(0, 0));
        assertEquals(before, ProvisionalSampling.revision());
        verify(runtime, never()).planPreview(anyInt(), anyInt(), anyInt(), anyInt());
        assertNoRasters(runtime);
    }

    @Test
    public void disabledHydrologyLeavesTheRiverMapEmpty() {
        long before = ProvisionalSampling.revision();

        BufferedImage image = new IrisRenderer(engine(FakeHydrology.disabled())).renderStudio(0D, 0D, 32D, 32, RenderType.RIVER,
                () -> false);

        assertEquals(NO_RIVER, image.getRGB(16, 16));
        assertEquals(before, ProvisionalSampling.revision());
    }

    @Test
    public void biomeRefinementCompositesThePlannedWaterOverTheBase() throws Exception {
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, SEA_LEVEL, 63)) {
            Engine engine = engine(hydrology.runtime);
            greenBiomes(engine);
            settled(engine, 64D, 64, RenderType.RIVER);
            IrisRenderer renderer = new IrisRenderer(engine);
            BufferedImage preview = renderer.renderStudioBase(0D, 0D, 64D, 64, RenderType.BIOME, () -> false);

            BufferedImage refined = renderer.refineStudioBiome(0D, 0D, 64D, preview, () -> false);
            BufferedImage complete = renderer.renderStudio(0D, 0D, 64D, 64, RenderType.BIOME, () -> false);

            assertEquals(Color.GREEN.getRGB(), preview.getRGB(16, 16));
            assertNotEquals(preview.getRGB(16, 16), refined.getRGB(16, 16));
            assertEquals(Color.GREEN.getRGB(), refined.getRGB(48, 48));
            assertEquals(complete.getRGB(16, 16), refined.getRGB(16, 16));
        }
    }

    @Test
    public void studioBiomeViewShowsWaterAtItsDefaultZoomWithoutRasters() throws Exception {
        try (FakeHydrology hydrology = scene()) {
            HydrologyRuntime runtime = spy(hydrology.runtime);
            Engine engine = engine(runtime);
            greenBiomes(engine);
            IrisRenderer renderer = new IrisRenderer(engine);
            BufferedImage preview = renderer.renderStudioBase(0D, 0D, 4D * PIXELS, PIXELS, RenderType.BIOME, () -> false);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS);
            BufferedImage refined;
            long revision;
            do {
                assertTrue("The studio water never settled", System.nanoTime() < deadline);
                Thread.sleep(10L);
                revision = ProvisionalSampling.revision();
                refined = renderer.refineStudioBiome(0D, 0D, 4D * PIXELS, preview, () -> false);
            } while (ProvisionalSampling.revision() != revision);

            assertNotEquals(Color.GREEN.getRGB(), refined.getRGB(POND_X / 4, POND_Z / 4));
            assertNotEquals(Color.GREEN.getRGB(), refined.getRGB(LAKE_X / 4, LAKE_Z / 4));
            assertNotEquals(Color.GREEN.getRGB(), refined.getRGB(STREAM_X / 4, STREAM_Z / 4));
            assertEquals(Color.GREEN.getRGB(), refined.getRGB(DRY / 4, DRY / 4));
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

    private static void assertScene(BufferedImage image, int step) {
        assertEquals(IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.POND), image.getRGB(POND_X / step, POND_Z / step));
        assertEquals(IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.LAKE), image.getRGB(LAKE_X / step, LAKE_Z / step));
        assertEquals(IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.STREAM), image.getRGB(STREAM_X / step, STREAM_Z / step));
        assertEquals(NO_RIVER, image.getRGB(DRY / step, DRY / step));
    }

    private static void assertNoRasters(HydrologyRuntime runtime) {
        verify(runtime, never()).rasterPreview(anyInt(), anyInt());
        verify(runtime, never()).rasterSnapshot(anyInt(), anyInt());
        verify(runtime, never()).raster(anyInt(), anyInt());
    }

    private static void greenBiomes(Engine engine) {
        IrisBiome biome = mock(IrisBiome.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<IrisBiome> base = mock(ProceduralStream.class);
        when(engine.getComplex().getBaseBiomeStream()).thenReturn(base);
        when(base.get(anyDouble(), anyDouble())).thenReturn(biome);
        when(biome.getColor(engine, RenderType.BIOME)).thenReturn(Color.GREEN);
    }

    private static BufferedImage settled(Engine engine, double span, int pixels, RenderType type) throws InterruptedException {
        return settled(engine, 0D, span, pixels, type);
    }

    private static BufferedImage settled(Engine engine, double startZ, double span, int pixels, RenderType type)
            throws InterruptedException {
        IrisRenderer renderer = new IrisRenderer(engine);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS);
        while (true) {
            long revision = ProvisionalSampling.revision();
            BufferedImage image = renderer.renderStudio(0D, startZ, span, pixels, type, () -> false);
            if (ProvisionalSampling.revision() == revision) {
                return image;
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
