package art.arcane.iris.studio.map;

import art.arcane.iris.studio.map.components.MapLayer;
import art.arcane.iris.studio.map.components.MapTile;
import art.arcane.iris.studio.map.components.MapTileRenderer;
import art.arcane.iris.studio.map.components.TileType;
import org.junit.Test;

import java.awt.EventQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VisionRenderControllerTest {
    @Test
    public void provisionalTilesRetryWithBackoffAndConvergeWithoutMoving() throws Exception {
        MapTileRenderer.TileRender work = mock(MapTileRenderer.TileRender.class);
        MapTileRenderer renderer = renderer(work);
        MapTile tile = mock(MapTile.class);
        AtomicInteger attempts = new AtomicInteger();
        when(work.render(any()))
                .thenAnswer(invocation -> {
                    attempts.incrementAndGet();
                    return tile;
                });
        when(work.complete()).thenAnswer(invocation -> attempts.get() >= 3);
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
            verify(renderer).prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean());
        }
    }

    @Test
    public void resizeZoomAndSuspendRetainProvisionalImageWorkAndBackoff() throws Exception {
        MapTileRenderer.TileRender work = mock(MapTileRenderer.TileRender.class);
        MapTileRenderer renderer = renderer(work);
        MapTile incomplete = mock(MapTile.class);
        MapTile complete = mock(MapTile.class);
        AtomicInteger attempts = new AtomicInteger();
        when(work.render(any()))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() == 1) {
                        return incomplete;
                    }
                    return complete;
                });
        when(work.complete()).thenAnswer(invocation -> attempts.get() >= 2);
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame first = controller.request(spec(renderer, 1L));
            await(() -> first.tiles().getFirst().tile() != null && controller.progress(first).activeWorkers() == 0);
            assertSame(incomplete, first.tiles().getFirst().tile());
            VisionRenderController.Frame resized = controller.request(new VisionRenderController.RenderSpec(renderer,
                    MapLayer.of(TileType.RIVER), 1L, 32D, 32D, 1D, 48, 48, false));
            assertSame(incomplete, resized.tiles().getFirst().tile());
            assertTrue(resized.tiles().getFirst().provisional());
            VisionRenderController.Frame zoomed = controller.request(new VisionRenderController.RenderSpec(renderer,
                    MapLayer.of(TileType.RIVER), 1L, 32D, 32D, 1.15D, 48, 48, false));
            assertSame(incomplete, zoomed.tiles().getFirst().tile());
            controller.suspend();
            VisionRenderController.Frame second = controller.request(spec(renderer, 1L));
            assertSame(incomplete, second.tiles().getFirst().tile());
            assertEquals(0, controller.progress(second).ready());
            assertEquals(1, attempts.get());
            await(() -> {
                controller.retryProvisional(second);
                return controller.progress(second).ready() == 1;
            });
            assertEquals(2, attempts.get());
            verify(renderer).prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean());
            VisionRenderController.Frame third = controller.request(spec(renderer, 1L));
            assertSame(complete, third.tiles().getFirst().tile());
            assertEquals(2, attempts.get());
        }
    }

    @Test
    public void contentRevisionInvalidatesFinalTiles() throws Exception {
        MapTile tile = mock(MapTile.class);
        MapTileRenderer.TileRender work = finished(tile);
        MapTileRenderer renderer = renderer(work);
        AtomicInteger attempts = new AtomicInteger();
        when(work.render(any()))
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
    public void zoomRetainsCompleteWaterTilesWhileDetailedTilesAreProvisional() throws Exception {
        MapTileRenderer renderer = mock(MapTileRenderer.class);
        MapTile overview = mock(MapTile.class);
        MapTile incomplete = mock(MapTile.class);
        MapTileRenderer.TileRender overviewWork = finished(overview);
        MapTileRenderer.TileRender detailWork = mock(MapTileRenderer.TileRender.class);
        when(detailWork.render(any())).thenReturn(incomplete);
        when(renderer.prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    double scale = invocation.getArgument(2);
                    if (scale < 4D) {
                        return detailWork;
                    }
                    return overviewWork;
                });
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame coarse = controller.request(new VisionRenderController.RenderSpec(renderer,
                    MapLayer.of(TileType.RIVER), 1L, 128D, 128D, 4D, 64, 64, false));
            await(() -> controller.progress(coarse).ready() == 1);

            VisionRenderController.Frame fine = controller.request(spec(renderer, 1L));
            await(() -> fine.tiles().getFirst().tile() != null);
            VisionRenderController.VisibleTile visible = fine.tiles().getFirst();
            assertTrue(visible.provisional());
            assertEquals(0, controller.progress(fine).ready());
            VisionRenderController.Fallback retained = controller.fallback(fine, visible);
            assertNotNull(retained);
            assertSame(overview, retained.pieces().getFirst().tile());
            assertEquals(16, retained.pieces().getFirst().sourceSize());

            VisionRenderController.Frame returned = controller.request(new VisionRenderController.RenderSpec(renderer,
                    MapLayer.of(TileType.RIVER), 1L, 128D, 128D, 4D, 64, 64, false));
            assertSame(overview, returned.tiles().getFirst().tile());
            assertFalse(returned.tiles().getFirst().provisional());
        }
    }

    @Test
    public void closeRejectsLatePublication() throws Exception {
        MapTileRenderer.TileRender work = mock(MapTileRenderer.TileRender.class);
        MapTileRenderer renderer = renderer(work);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(work.render(any()))
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
        assertNull(frame.tiles().getFirst().tile());
    }

    @Test
    public void resizeAndSameLevelZoomReuseFinalTiles() throws Exception {
        MapTile tile = mock(MapTile.class);
        MapTileRenderer.TileRender work = finished(tile);
        MapTileRenderer renderer = renderer(work);
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame first = controller.request(new VisionRenderController.RenderSpec(renderer,
                    MapLayer.of(TileType.RIVER), 1L, 32D, 32D, 1D, 48, 48, false));
            await(() -> controller.progress(first).ready() == 1);
            VisionRenderController.Frame resized = controller.request(spec(renderer, 1L));
            assertSame(tile, resized.tiles().getFirst().tile());
            VisionRenderController.Frame zoomed = controller.request(new VisionRenderController.RenderSpec(renderer,
                    MapLayer.of(TileType.RIVER), 1L, 32D, 32D, 0.9D, 64, 64, false));
            assertSame(tile, zoomed.tiles().getFirst().tile());
            assertEquals(1, controller.progress(zoomed).ready());
            verify(work).render(any());
            verify(renderer).prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean());
        }
    }

    @Test
    public void contentRevisionInvalidatesProvisionalWork() throws Exception {
        MapTile tile = mock(MapTile.class);
        MapTileRenderer.TileRender work = mock(MapTileRenderer.TileRender.class);
        MapTileRenderer renderer = renderer(work);
        when(work.render(any())).thenReturn(tile);
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame first = controller.request(spec(renderer, 1L));
            await(() -> first.tiles().getFirst().tile() != null);
            VisionRenderController.Frame second = controller.request(spec(renderer, 2L));
            await(() -> second.tiles().getFirst().tile() != null);
            verify(renderer, times(2)).prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean());
        }
    }

    @Test
    public void missingDetailUsesProvisionalOverviewWithoutMarkingItFinal() throws Exception {
        MapTileRenderer renderer = mock(MapTileRenderer.class);
        MapTile overview = mock(MapTile.class);
        MapTileRenderer.TileRender overviewWork = mock(MapTileRenderer.TileRender.class);
        when(overviewWork.render(any())).thenReturn(overview);
        CountDownLatch entered = new CountDownLatch(1);
        MapTileRenderer.TileRender detailWork = cancellable(entered);
        when(renderer.prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean()))
                .thenAnswer(invocation -> (double) invocation.getArgument(2) < 4D ? detailWork : overviewWork);
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame coarse = controller.request(new VisionRenderController.RenderSpec(renderer,
                    MapLayer.of(TileType.RIVER), 1L, 128D, 128D, 4D, 64, 64, false));
            await(() -> coarse.tiles().getFirst().tile() != null);
            VisionRenderController.Frame fine = controller.request(spec(renderer, 1L));
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            assertNull(fine.tiles().getFirst().tile());
            VisionRenderController.Fallback fallback = controller.fallback(fine, fine.tiles().getFirst());
            assertNotNull(fallback);
            assertSame(overview, fallback.pieces().getFirst().tile());
            assertEquals(0, controller.progress(fine).ready());
        }
    }

    @Test
    public void zoomingOutKeepsCompleteFinerTilesOverProvisionalExactTile() throws Exception {
        MapTileRenderer renderer = mock(MapTileRenderer.class);
        MapTile fineTile = mock(MapTile.class);
        MapTile coarseTile = mock(MapTile.class);
        MapTileRenderer.TileRender fineWork = finished(fineTile);
        MapTileRenderer.TileRender coarseWork = mock(MapTileRenderer.TileRender.class);
        when(coarseWork.render(any())).thenReturn(coarseTile);
        when(renderer.prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean()))
                .thenAnswer(invocation -> (double) invocation.getArgument(2) < 2D ? fineWork : coarseWork);
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame fine = controller.request(spec(renderer, 1L));
            await(() -> controller.progress(fine).ready() == 1);
            VisionRenderController.Frame coarse = controller.request(new VisionRenderController.RenderSpec(renderer,
                    MapLayer.of(TileType.RIVER), 1L, 64D, 64D, 2D, 64, 64, false));
            await(() -> coarse.tiles().getFirst().tile() != null);
            VisionRenderController.Fallback fallback = controller.fallback(coarse, coarse.tiles().getFirst());
            assertNotNull(fallback);
            assertSame(fineTile, fallback.pieces().getFirst().tile());
            assertEquals(0.5D, fallback.pieces().getFirst().size(), 0D);
        }
    }

    @Test
    public void suspendedViewCancelsSamplingAndProbeButKeepsCompletedTiles() throws Exception {
        MapTile tile = mock(MapTile.class);
        MapTileRenderer renderer = mock(MapTileRenderer.class);
        CountDownLatch sampling = new CountDownLatch(1);
        MapTileRenderer.TileRender pending = cancellable(sampling);
        MapTileRenderer.TileRender complete = finished(tile);
        when(renderer.prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean()))
                .thenReturn(complete, pending);
        CountDownLatch probing = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger published = new AtomicInteger();
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame first = controller.request(spec(renderer, 1L));
            await(() -> controller.progress(first).ready() == 1);
            VisionRenderController.Frame next = controller.request(new VisionRenderController.RenderSpec(renderer,
                    MapLayer.of(TileType.RIVER), 1L, 96D, 32D, 1D, 64, 64, false));
            assertTrue(sampling.await(5L, TimeUnit.SECONDS));
            controller.submitProbe(next, () -> {
                probing.countDown();
                try {
                    new CountDownLatch(1).await();
                    return "late";
                } catch (InterruptedException error) {
                    interrupted.countDown();
                    throw error;
                }
            }, value -> published.incrementAndGet());
            assertTrue(probing.await(5L, TimeUnit.SECONDS));
            controller.suspend();
            assertTrue(interrupted.await(5L, TimeUnit.SECONDS));
            await(() -> controller.progress(next).activeWorkers() == 0);
            EventQueue.invokeAndWait(() -> { });
            assertEquals(0, published.get());
            assertNull(next.tiles().getFirst().tile());
            VisionRenderController.Frame resumed = controller.request(spec(renderer, 1L));
            assertSame(tile, resumed.tiles().getFirst().tile());
            verify(renderer, times(2)).prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean());
        }
    }

    @Test
    public void retainedWorkCountsAgainstCacheBudgetAndIsReleasedWhenComplete() throws Exception {
        MapTile tile = mock(MapTile.class);
        when(tile.bytes()).thenReturn(1L);
        MapTileRenderer.TileRender work = mock(MapTileRenderer.TileRender.class);
        MapTileRenderer renderer = renderer(work);
        AtomicInteger attempts = new AtomicInteger();
        when(work.render(any())).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            return tile;
        });
        when(work.complete()).thenAnswer(invocation -> attempts.get() >= 2);
        when(work.bytes()).thenReturn(256L * 1024L * 1024L);
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame first = controller.request(spec(renderer, 1L));
            await(() -> first.tiles().getFirst().tile() != null);
            VisionRenderController.Frame second = controller.request(spec(renderer, 1L));
            await(() -> controller.progress(second).ready() == 1);
            VisionRenderController.Frame third = controller.request(spec(renderer, 1L));
            assertSame(tile, third.tiles().getFirst().tile());
            verify(renderer, times(2)).prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean());
            assertEquals(2, attempts.get());
        }
    }

    @Test
    public void cancelledRetryReleasesWorkAndKeepsPublishedImage() throws Exception {
        MapTile incomplete = mock(MapTile.class);
        MapTile complete = mock(MapTile.class);
        CountDownLatch entered = new CountDownLatch(1);
        MapTileRenderer.TileRender partial = mock(MapTileRenderer.TileRender.class);
        MapTileRenderer.TileRender replacement = finished(complete);
        AtomicInteger attempts = new AtomicInteger();
        when(partial.render(any())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                return incomplete;
            }
            BooleanSupplier cancelled = invocation.getArgument(0);
            entered.countDown();
            await(cancelled);
            throw new CancellationException();
        });
        MapTileRenderer renderer = mock(MapTileRenderer.class);
        when(renderer.prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean()))
                .thenReturn(partial, replacement);
        try (VisionRenderController controller = new VisionRenderController(() -> { })) {
            VisionRenderController.Frame first = controller.request(spec(renderer, 1L));
            await(() -> first.tiles().getFirst().tile() != null);
            await(() -> {
                controller.retryProvisional(first);
                return entered.getCount() == 0L;
            });
            controller.suspend();
            await(() -> controller.progress(first).activeWorkers() == 0);
            assertSame(incomplete, first.tiles().getFirst().tile());
            VisionRenderController.Frame resumed = controller.request(spec(renderer, 1L));
            await(() -> controller.progress(resumed).ready() == 1);
            assertSame(complete, resumed.tiles().getFirst().tile());
            verify(renderer, times(2)).prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean());
        }
    }

    @Test
    public void previewReadinessWakesBackoffAtBoundedCadenceIncludingCompletionDuringRender() throws Exception {
        MapTile tile = mock(MapTile.class);
        MapTileRenderer.TileRender work = mock(MapTileRenderer.TileRender.class);
        MapTileRenderer renderer = renderer(work);
        AtomicLong time = new AtomicLong();
        AtomicLong readiness = new AtomicLong();
        AtomicInteger attempts = new AtomicInteger();
        when(renderer.previewRevision()).thenAnswer(invocation -> readiness.get());
        when(work.render(any())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 3) {
                readiness.incrementAndGet();
            }
            return tile;
        });
        when(work.complete()).thenAnswer(invocation -> attempts.get() >= 4);
        try (VisionRenderController controller = new VisionRenderController(() -> { }, time::get)) {
            VisionRenderController.Frame frame = controller.request(spec(renderer, 1L));
            await(() -> attempts.get() == 1 && controller.progress(frame).activeWorkers() == 0);
            time.set(VisionRenderController.FIRST_RETRY_NANOS);
            controller.retryProvisional(frame);
            await(() -> attempts.get() == 2 && controller.progress(frame).activeWorkers() == 0);
            time.addAndGet(VisionRenderController.FIRST_RETRY_NANOS);
            controller.retryProvisional(frame);
            assertEquals(2, attempts.get());
            readiness.incrementAndGet();
            controller.retryProvisional(frame);
            await(() -> attempts.get() == 3 && controller.progress(frame).activeWorkers() == 0);
            time.addAndGet(VisionRenderController.FIRST_RETRY_NANOS - 1L);
            controller.retryProvisional(frame);
            assertEquals(3, attempts.get());
            time.incrementAndGet();
            controller.retryProvisional(frame);
            await(() -> controller.progress(frame).ready() == 1);
            assertEquals(4, attempts.get());
            verify(renderer).prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean());
        }
    }

    private static MapTileRenderer.TileRender cancellable(CountDownLatch entered) {
        MapTileRenderer.TileRender work = mock(MapTileRenderer.TileRender.class);
        when(work.render(any())).thenAnswer(invocation -> {
            BooleanSupplier cancelled = invocation.getArgument(0);
            entered.countDown();
            while (!cancelled.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            throw new CancellationException();
        });
        return work;
    }

    private static MapTileRenderer renderer(MapTileRenderer.TileRender work) {
        MapTileRenderer renderer = mock(MapTileRenderer.class);
        when(renderer.prepareTile(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyBoolean())).thenReturn(work);
        return renderer;
    }

    private static MapTileRenderer.TileRender finished(MapTile tile) {
        MapTileRenderer.TileRender work = mock(MapTileRenderer.TileRender.class);
        when(work.render(any())).thenReturn(tile);
        when(work.complete()).thenReturn(true);
        return work;
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
