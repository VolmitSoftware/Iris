package art.arcane.iris.studio.map;

import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.studio.map.components.MapLayer;
import art.arcane.iris.studio.map.components.MapTile;
import art.arcane.iris.studio.map.components.MapTileRenderer;
import art.arcane.iris.studio.map.components.TileType;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VisionRenderControllerTest {
    @Test
    public void provisionalTilesRetryWithBackoffAndConvergeWithoutMoving() throws Exception {
        MapTileRenderer renderer = mock(MapTileRenderer.class);
        MapTile tile = mock(MapTile.class);
        AtomicInteger attempts = new AtomicInteger();
        when(renderer.renderTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() < 3) {
                        ProvisionalSampling.mark();
                    }
                    return tile;
                });
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame frame = controller.request(spec(renderer, 1L));
            await(() -> frame.tiles().getFirst().tile() != null);
            controller.retryProvisional(frame);
            assertEquals(1, attempts.get());
            await(() -> {
                controller.retryProvisional(frame);
                return attempts.get() == 2 && controller.progress(frame).activeWorkers() == 0;
            });
            controller.retryProvisional(frame);
            assertEquals(2, attempts.get());
            assertEquals(0, controller.progress(frame).ready());
            await(() -> {
                controller.retryProvisional(frame);
                return controller.progress(frame).ready() == 1;
            });
            assertEquals(3, attempts.get());
            assertSame(tile, controller.request(spec(renderer, 1L)).tiles().getFirst().tile());
        }
    }

    @Test
    public void provisionalTilesAreShownButNeverReusedAsFinal() throws Exception {
        MapTileRenderer renderer = mock(MapTileRenderer.class);
        MapTile incomplete = mock(MapTile.class);
        MapTile complete = mock(MapTile.class);
        AtomicInteger attempts = new AtomicInteger();
        when(renderer.renderTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() == 1) {
                        ProvisionalSampling.mark();
                        return incomplete;
                    }
                    return complete;
                });
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame first = controller.request(spec(renderer, 1L));
            await(() -> first.tiles().getFirst().tile() != null);
            assertSame(incomplete, first.tiles().getFirst().tile());
            VisionRenderController.Frame second = controller.request(spec(renderer, 1L));
            await(() -> second.tiles().getFirst().tile() == complete);
            assertEquals(2, attempts.get());
            VisionRenderController.Frame third = controller.request(spec(renderer, 1L));
            assertSame(complete, third.tiles().getFirst().tile());
            assertEquals(2, attempts.get());
        }
    }

    @Test
    public void contentRevisionInvalidatesFinalTiles() throws Exception {
        MapTileRenderer renderer = mock(MapTileRenderer.class);
        MapTile tile = mock(MapTile.class);
        AtomicInteger attempts = new AtomicInteger();
        when(renderer.renderTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    attempts.incrementAndGet();
                    return tile;
                });
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame first = controller.request(spec(renderer, 1L));
            await(() -> first.tiles().getFirst().tile() != null);
            VisionRenderController.Frame second = controller.request(spec(renderer, 2L));
            await(() -> second.tiles().getFirst().tile() != null);
            assertEquals(2, attempts.get());
        }
    }

    @Test
    public void closeRejectsLatePublication() throws Exception {
        MapTileRenderer renderer = mock(MapTileRenderer.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(renderer.renderTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    while (release.getCount() != 0L) {
                        try {
                            release.await();
                        } catch (InterruptedException ignored) {
                        }
                    }
                    return mock(MapTile.class);
                });
        VisionRenderController controller = new VisionRenderController(() -> { });
        VisionRenderController.Frame frame = controller.request(spec(renderer, 1L));
        try {
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            controller.close();
            controller.close();
        } finally {
            release.countDown();
            controller.close();
        }
        await(() -> controller.progress(frame).activeWorkers() == 0);
        assertEquals(null, frame.tiles().getFirst().tile());
    }

    private static VisionRenderController.RenderSpec spec(MapTileRenderer renderer, long revision) {
        return new VisionRenderController.RenderSpec(renderer, MapLayer.of(TileType.RIVER), revision,
                32D, 32D, 1D, 64, 64, false);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertTrue("render did not converge", condition.getAsBoolean());
    }
}
