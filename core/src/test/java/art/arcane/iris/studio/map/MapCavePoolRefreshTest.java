package art.arcane.iris.studio.map;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.engine.HydrologyPlanPreview;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.studio.map.components.MapLayer;
import art.arcane.iris.studio.map.components.MapStyle;
import art.arcane.iris.studio.map.components.MapTile;
import art.arcane.iris.studio.map.components.MapTileRenderer;
import art.arcane.iris.studio.map.components.TileType;
import art.arcane.iris.studio.render.IrisRenderer;
import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MapCavePoolRefreshTest {
    private static final int POOL_X = 20;
    private static final int POOL_Z = 24;
    private static final int POOL = IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.CAVE_POOL) & 0xFFFFFF;

    @Test
    public void completeTilesPickUpRecordedCavePools() throws Exception {
        Scene scene = new Scene();
        AtomicLong time = new AtomicLong();
        try (VisionRenderController controller = new VisionRenderController(() -> { }, time::get)) {
            VisionRenderController.Frame frame = controller.request(spec(scene.renderer, 4D));
            await(() -> controller.progress(frame).ready() == 1 && controller.progress(frame).activeWorkers() == 0);
            assertNotEquals(POOL, pixel(frame, 4D));
            long sampled = scene.samples.get();

            scene.record();
            controller.retryProvisional(frame);
            await(() -> controller.progress(frame).activeWorkers() == 0);
            assertNotEquals("refreshes wait for the retry cadence", POOL, pixel(frame, 4D));
            time.set(VisionRenderController.FIRST_RETRY_NANOS);
            await(() -> {
                controller.retryProvisional(frame);
                return pixel(frame, 4D) == POOL;
            });

            assertEquals("a refresh redraws only the water", sampled, scene.samples.get());
            assertEquals(1, controller.progress(frame).ready());
        }
    }

    @Test
    public void coarseTilesKeepNoWaterState() throws Exception {
        Scene scene = new Scene();
        MapTileRenderer.TileRender detailed = settle(scene.renderer.prepareTile(0D, 0D, 4D, VisionRenderController.TILE_PIXELS,
                MapLayer.of(TileType.RIVER), false));
        MapTileRenderer.TileRender coarse = settle(scene.renderer.prepareTile(0D, 0D, 8D, VisionRenderController.TILE_PIXELS,
                MapLayer.of(TileType.RIVER), false));
        assertTrue(detailed.retainsWater());
        assertTrue(detailed.bytes() > 0L);
        assertFalse(coarse.retainsWater());
        assertEquals(0L, coarse.bytes());

        AtomicLong time = new AtomicLong();
        try (VisionRenderController controller = new VisionRenderController(() -> { }, time::get)) {
            VisionRenderController.Frame frame = controller.request(spec(scene.renderer, 8D));
            await(() -> controller.progress(frame).ready() == 1 && controller.progress(frame).activeWorkers() == 0);
            MapTile drawn = frame.tiles().getFirst().tile();

            scene.record();
            time.set(VisionRenderController.FIRST_RETRY_NANOS);
            controller.retryProvisional(frame);
            await(() -> controller.progress(frame).activeWorkers() == 0);

            assertSame(drawn, frame.tiles().getFirst().tile());
            assertNotEquals(POOL, pixel(frame, 8D));
        }
    }

    @Test
    public void poolsRecordedAfterARasterLandsStillRefresh() throws Exception {
        Scene scene = new Scene();
        AtomicBoolean landed = new AtomicBoolean();
        when(scene.runtime.rasterPreview(anyInt(), anyInt()))
                .thenAnswer(invocation -> landed.get() ? ChunkWaterRaster.EMPTY : ChunkWaterRaster.PROVISIONAL);
        AtomicLong time = new AtomicLong();
        try (VisionRenderController controller = new VisionRenderController(() -> { }, time::get)) {
            VisionRenderController.Frame frame = controller.request(spec(scene.renderer, 2D));
            await(() -> frame.tiles().getFirst().tile() != null && controller.progress(frame).activeWorkers() == 0);
            assertTrue(frame.tiles().getFirst().provisional());

            landed.set(true);
            scene.preview.incrementAndGet();
            time.addAndGet(VisionRenderController.FIRST_RETRY_NANOS);
            await(() -> {
                controller.retryProvisional(frame);
                return controller.progress(frame).ready() == 1 && controller.progress(frame).activeWorkers() == 0;
            });
            assertNotEquals("the chunk records its pool after its raster lands", POOL, pixel(frame, 2D));

            scene.record();
            time.addAndGet(VisionRenderController.FIRST_RETRY_NANOS);
            await(() -> {
                controller.retryProvisional(frame);
                return pixel(frame, 2D) == POOL;
            });
        }
    }

    private static MapTileRenderer.TileRender settle(MapTileRenderer.TileRender work) throws Exception {
        await(() -> {
            work.render(() -> false);
            return work.complete();
        });
        return work;
    }

    private static int pixel(VisionRenderController.Frame frame, double blocksPerPixel) {
        MapTile tile = frame.tiles().getFirst().tile();
        return tile.image(MapStyle.PLAIN).getRGB((int) (POOL_X / blocksPerPixel), (int) (POOL_Z / blocksPerPixel)) & 0xFFFFFF;
    }

    private static VisionRenderController.RenderSpec spec(MapTileRenderer renderer, double blocksPerPixel) {
        double centre = VisionRenderController.TILE_PIXELS * blocksPerPixel / 2D;
        return new VisionRenderController.RenderSpec(renderer, MapLayer.of(TileType.RIVER), 1L, centre, centre, blocksPerPixel,
                VisionRenderController.TILE_PIXELS, VisionRenderController.TILE_PIXELS, false);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!condition.getAsBoolean()) {
            assertTrue("condition never held", System.nanoTime() < deadline);
            Thread.sleep(5L);
        }
    }

    private static final class Scene {
        private final AtomicLong samples = new AtomicLong();
        private final AtomicLong preview = new AtomicLong();
        private final AtomicLong water = new AtomicLong();
        private final AtomicBoolean recorded = new AtomicBoolean();
        private final HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        private final MapTileRenderer renderer;

        private Scene() {
            when(runtime.enabled()).thenReturn(true);
            when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(HydrologyPlanPreview.EMPTY);
            when(runtime.rasterPreview(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);
            when(runtime.previewRevision()).thenAnswer(invocation -> preview.get());
            IrisEngine engine = mock(IrisEngine.class);
            IrisComplex complex = mock(IrisComplex.class);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getHeight()).thenReturn(256);
            when(complex.getHydrologyRuntime()).thenReturn(runtime);
            IrisBiome biome = new IrisBiome();
            biome.setColor("#738249");
            IrisRegion region = new IrisRegion();
            when(complex.getBaseBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> biome, Interpolated.of(b -> 0D, d -> biome)));
            when(complex.getRegionStream()).thenReturn(ProceduralStream.of((x, z) -> {
                samples.incrementAndGet();
                return region;
            }, Interpolated.of(r -> 0D, d -> region)));
            GenerationHistory history = mock(GenerationHistory.class);
            GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
            when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
            when(router.history()).thenReturn(history);
            when(history.riverRevision()).thenAnswer(invocation -> water.get());
            ChunkGenerationSemantics pool = ChunkGenerationSemantics.builder(1, 1, 1L)
                    .addRiverFeature("water", HydrologyFeatureType.CAVE_POOL, 7L, POOL_X, 12, POOL_Z).build();
            when(history.semantics(anyInt(), anyInt())).thenReturn(Optional.empty());
            when(history.semantics(1, 1)).thenAnswer(invocation -> recorded.get() ? Optional.of(pool) : Optional.empty());
            renderer = new MapTileRenderer(engine);
        }

        private void record() {
            recorded.set(true);
            water.incrementAndGet();
        }
    }
}
