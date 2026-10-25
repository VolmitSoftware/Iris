package art.arcane.iris.studio.map;

import art.arcane.iris.generation.stream.ProvisionalSampling;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class TerrainTilesTest {
    @Test
    public void provisionalTerrainStandsInUntilACompleteRetryReplacesIt() throws Exception {
        AtomicBoolean provisional = new AtomicBoolean(true);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        TerrainSampling sampling = new TerrainSampling((grid, row, heights, colors) -> {
            if (row == 0) {
                attempts.incrementAndGet();
            }
            if (provisional.get()) {
                ProvisionalSampling.mark();
            }
            Arrays.fill(heights, row * grid.columns(), (row + 1) * grid.columns(), provisional.get() ? 10F : 20F);
        }, 0, 64, Double.NaN);
        ExecutorService workers = Executors.newSingleThreadExecutor();
        TerrainTile tile = new TerrainTile(0, 0, 0);
        TerrainTiles tiles = new TerrainTiles(sampling, workers, 1, () -> {
            if (attempts.get() == 1) {
                first.countDown();
            } else {
                second.countDown();
            }
        }, Map.of());
        try {
            tiles.sampleOnly(List.of(tile), Set.of(tile));
            assertTrue(first.await(3, TimeUnit.SECONDS));
            assertNotNull(tiles.layerOf(tile));
            assertTrue(tiles.isStandingIn(tile));
            assertEquals(10F, tiles.layerOf(tile).terrain().height(0, 0), 0F);

            provisional.set(false);
            tiles.sampleOnly(List.of(tile), Set.of(tile));
            assertFalse(second.await(100, TimeUnit.MILLISECONDS));
            assertEquals(1, attempts.get());
            Thread.sleep(500L);
            tiles.sampleOnly(List.of(tile), Set.of(tile));

            assertTrue(second.await(3, TimeUnit.SECONDS));
            assertFalse(tiles.isStandingIn(tile));
            assertEquals(20F, tiles.layerOf(tile).terrain().height(0, 0), 0F);
            tiles.sampleOnly(List.of(tile), Set.of(tile));
            assertEquals(2, attempts.get());
        } finally {
            tiles.close();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
        }
    }
}
