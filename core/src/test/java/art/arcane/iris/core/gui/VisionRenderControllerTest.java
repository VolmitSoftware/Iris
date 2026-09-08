package art.arcane.iris.core.gui;

import art.arcane.iris.engine.framework.render.IrisRenderer;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.testsupport.Await;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VisionRenderControllerTest {
    @Test
    public void visibleTilesAreUniqueAndOrderedCenterFirst() {
        VisionRenderController.RenderSpec spec = spec(RenderType.BIOME, 7L, 0D, 0D, 4D, 1440, 792);

        List<VisionRenderController.VisibleTile> tiles = VisionRenderController.visibleTiles(spec);

        assertEquals(336, tiles.size());
        Set<String> coordinates = new HashSet<>();
        double previousDistance = -1D;
        for (VisionRenderController.VisibleTile tile : tiles) {
            assertTrue(coordinates.add(tile.tileX() + ":" + tile.tileZ()));
            assertTrue(tile.distanceSquared() >= previousDistance);
            previousDistance = tile.distanceSquared();
        }
        assertEquals(1_376_256L, VisionRenderController.sampleCount(tiles.size()));
    }

    @Test
    public void negativeWorldCoordinatesUseFloorBasedTileOwnership() {
        VisionRenderController.RenderSpec spec = spec(RenderType.BIOME, 1L, -32D, -32D, 1D, 1, 1);

        List<VisionRenderController.VisibleTile> tiles = VisionRenderController.visibleTiles(spec);

        assertEquals(1, tiles.size());
        assertEquals(-1L, tiles.get(0).tileX());
        assertEquals(-1L, tiles.get(0).tileZ());
    }

    @Test
    public void cacheIdentityContainsOnlyAtlasGenerationModeZoomAndWorldPage() {
        VisionRenderController.TileKey baseline = new VisionRenderController.TileKey(4L, RenderType.BIOME, 4D, 8L, 9L);

        assertNotEquals(baseline, new VisionRenderController.TileKey(5L, RenderType.BIOME, 4D, 8L, 9L));
        assertNotEquals(baseline, new VisionRenderController.TileKey(4L, RenderType.RIVER, 4D, 8L, 9L));
        assertNotEquals(baseline, new VisionRenderController.TileKey(4L, RenderType.BIOME, 4.5D, 8L, 9L));
        assertNotEquals(baseline, new VisionRenderController.TileKey(4L, RenderType.BIOME, 4D, 7L, 9L));
    }

    @Test
    public void everyPublishedPageIsFinalResolutionAndIdenticalRequestsAreCacheHits() throws Exception {
        IrisRenderer renderer = mock(IrisRenderer.class);
        AtomicInteger calls = new AtomicInteger();
        when(renderer.renderStudio(
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyInt(),
                any(RenderType.class),
                any(BooleanSupplier.class)
        )).thenAnswer(invocation -> {
            assertEquals(VisionRenderController.TILE_PIXELS, (int) invocation.getArgument(3));
            calls.incrementAndGet();
            return new BufferedImage(
                    VisionRenderController.TILE_PIXELS,
                    VisionRenderController.TILE_PIXELS,
                    BufferedImage.TYPE_INT_RGB
            );
        });
        VisionRenderController controller = controller(2);
        VisionRenderController.RenderSpec spec = spec(renderer, RenderType.BIOME, 1L, 0D, 0D, 4D, 128, 128);
        try {
            VisionRenderController.Frame first = controller.request(spec);
            Await.until("the first render to report every tile ready", Duration.ofSeconds(2L),
                    () -> controller.progress(first).ready() == controller.progress(first).total());
            int rendered = calls.get();

            VisionRenderController.Frame cached = controller.request(spec);

            assertEquals(rendered, calls.get());
            assertEquals(controller.progress(cached).total(), controller.progress(cached).ready());
            for (VisionRenderController.VisibleTile tile : cached.tiles()) {
                assertEquals(VisionRenderController.TILE_PIXELS, controller.image(cached, tile).getWidth());
                assertEquals(VisionRenderController.TILE_PIXELS, controller.image(cached, tile).getHeight());
            }
        } finally {
            controller.close();
        }
    }

    @Test
    public void smallPanReusesEveryOverlappingWorldPage() throws Exception {
        IrisRenderer renderer = exactRenderer();
        VisionRenderController controller = controller(2);
        try {
            VisionRenderController.Frame first = controller.request(spec(renderer, RenderType.BIOME, 1L, 16D, 16D, 4D, 128, 128));
            Await.until("the first render to report every tile ready", Duration.ofSeconds(2L),
                    () -> controller.progress(first).ready() == controller.progress(first).total());
            Set<VisionRenderController.TileKey> firstKeys = keys(first);

            VisionRenderController.Frame panned = controller.request(spec(renderer, RenderType.BIOME, 1L, 20D, 20D, 4D, 128, 128));

            assertEquals(firstKeys, keys(panned));
            assertEquals(panned.tiles().size(), controller.progress(panned).ready());
            for (VisionRenderController.VisibleTile pannedTile : panned.tiles()) {
                VisionRenderController.VisibleTile original = tile(first, panned.key(pannedTile));
                assertSame(controller.image(first, original), controller.image(panned, pannedTile));
            }
        } finally {
            controller.close();
        }
    }

    @Test
    public void newerViewPublishesWithoutWaitingForCancelledQueuedWork() throws Exception {
        IrisRenderer renderer = mock(IrisRenderer.class);
        CountDownLatch staleStarted = new CountDownLatch(1);
        CountDownLatch latestStarted = new CountDownLatch(1);
        when(renderer.renderStudio(
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyInt(),
                any(RenderType.class),
                any(BooleanSupplier.class)
        )).thenAnswer(invocation -> {
            RenderType type = invocation.getArgument(4);
            BooleanSupplier cancelled = invocation.getArgument(5);
            if (type == RenderType.REGION) {
                staleStarted.countDown();
                while (!cancelled.getAsBoolean()) {
                    Thread.onSpinWait();
                }
                throw new java.util.concurrent.CancellationException();
            }
            latestStarted.countDown();
            return new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        });
        VisionRenderController controller = controller(2);
        try {
            controller.request(spec(renderer, RenderType.REGION, 1L, 0D, 0D, 4D, 64, 64));
            assertTrue(staleStarted.await(1L, TimeUnit.SECONDS));

            controller.request(spec(renderer, RenderType.BIOME, 1L, 0D, 0D, 4D, 64, 64));

            assertTrue(latestStarted.await(1L, TimeUnit.SECONDS));
        } finally {
            controller.close();
        }
    }

    private static VisionRenderController controller(int workers) {
        return new VisionRenderController(
                () -> {
                },
                new VisionRenderController.RuntimeOptions(workers, 8, false)
        );
    }

    private static IrisRenderer exactRenderer() {
        IrisRenderer renderer = mock(IrisRenderer.class);
        when(renderer.renderStudio(
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyInt(),
                any(RenderType.class),
                any(BooleanSupplier.class)
        )).thenAnswer(invocation -> new BufferedImage(
                invocation.getArgument(3),
                invocation.getArgument(3),
                BufferedImage.TYPE_INT_RGB
        ));
        return renderer;
    }

    private static Set<VisionRenderController.TileKey> keys(VisionRenderController.Frame frame) {
        Set<VisionRenderController.TileKey> keys = new HashSet<>();
        for (VisionRenderController.VisibleTile tile : frame.tiles()) {
            keys.add(frame.key(tile));
        }
        return keys;
    }

    private static VisionRenderController.VisibleTile tile(
            VisionRenderController.Frame frame,
            VisionRenderController.TileKey key
    ) {
        for (VisionRenderController.VisibleTile tile : frame.tiles()) {
            if (frame.key(tile).equals(key)) {
                return tile;
            }
        }
        throw new AssertionError("Missing tile " + key);
    }

    private static VisionRenderController.RenderSpec spec(
            RenderType type,
            long revision,
            double centerX,
            double centerZ,
            double blocksPerPixel,
            int width,
            int height
    ) {
        return new VisionRenderController.RenderSpec(
                mock(IrisRenderer.class),
                type,
                revision,
                centerX,
                centerZ,
                blocksPerPixel,
                width,
                height
        );
    }

    private static VisionRenderController.RenderSpec spec(
            IrisRenderer renderer,
            RenderType type,
            long revision,
            double centerX,
            double centerZ,
            double blocksPerPixel,
            int width,
            int height
    ) {
        return new VisionRenderController.RenderSpec(
                renderer,
                type,
                revision,
                centerX,
                centerZ,
                blocksPerPixel,
                width,
                height
        );
    }
}
