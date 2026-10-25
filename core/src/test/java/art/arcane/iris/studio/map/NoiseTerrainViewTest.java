package art.arcane.iris.studio.map;

import art.arcane.iris.generation.stream.ProvisionalSampling;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class NoiseTerrainViewTest {
    @Test
    public void stationaryViewRetriesProvisionalTerrainWithoutCameraChanges() throws Exception {
        AtomicReference<TerrainGrid> firstGrid = new AtomicReference<>();
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch retried = new CountDownLatch(1);
        TerrainSampling sampling = new TerrainSampling((grid, row, heights, colors) -> {
            firstGrid.compareAndSet(null, grid);
            if (grid.equals(firstGrid.get())) {
                if (row == 0 && attempts.incrementAndGet() > 1) {
                    retried.countDown();
                }
                if (attempts.get() == 1) {
                    ProvisionalSampling.mark();
                }
            }
            Arrays.fill(heights, row * grid.columns(), (row + 1) * grid.columns(), 20F);
        }, 0, 64, Double.NaN);
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService renderers = Executors.newSingleThreadExecutor();
        AtomicReference<OrbitViewport> viewport = new AtomicReference<>();
        AtomicReference<NoiseTerrainView> view = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                OrbitViewport opened = new OrbitViewport("Terrain refresh test", null);
                opened.setSize(640, 480);
                viewport.set(opened);
                NoiseTerrainView shown = new NoiseTerrainView(opened, renderers, workers, 1);
                view.set(shown);
                shown.show(sampling);
            });
            assertTrue(retried.await(5, TimeUnit.SECONDS));
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                view.get().close();
                viewport.get().close();
            });
            workers.shutdownNow();
            renderers.shutdownNow();
            assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
            assertTrue(renderers.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void closedViewIgnoresSubsequentRefreshes() throws Exception {
        AtomicInteger samples = new AtomicInteger();
        TerrainSampling sampling = new TerrainSampling((grid, row, heights, colors) -> samples.incrementAndGet(),
                0, 64, Double.NaN);
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService renderers = Executors.newSingleThreadExecutor();
        try {
            SwingUtilities.invokeAndWait(() -> {
                OrbitViewport viewport = new OrbitViewport("Closed terrain test", null);
                viewport.setSize(32, 32);
                NoiseTerrainView view = new NoiseTerrainView(viewport, renderers, workers, 1);
                view.close();
                view.close();
                view.show(sampling);
                view.resume();
                viewport.close();
            });
            workers.shutdown();
            assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
            assertEquals(0, samples.get());
        } finally {
            workers.shutdownNow();
            renderers.shutdownNow();
            assertTrue(renderers.awaitTermination(3, TimeUnit.SECONDS));
        }
    }
}
