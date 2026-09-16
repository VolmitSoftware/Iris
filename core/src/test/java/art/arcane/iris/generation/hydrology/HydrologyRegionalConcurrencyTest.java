package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceBounds;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyRegionalConcurrencyTest {
    @Test(timeout = 15000)
    public void collidingBasinKeysCompileWithoutHoldingCacheLocks() throws Exception {
        HydrologyTileKey first = new HydrologyTileKey(0, 0);
        HydrologyTileKey second = new HydrologyTileKey(1, -31);
        assertEquals(first.hashCode(), second.hashCode());
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        HydrologyPlanner planner = planner(request -> {
            started.countDown();
            await(release);
            return land(70);
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<HydrologyRegionalNetwork> firstResult = workers.submit(() -> planner.regional.draft(first));
            Future<HydrologyRegionalNetwork> secondResult = workers.submit(() -> planner.regional.draft(second));
            assertTrue("Independent basin compilation must overlap", started.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(firstResult.get(5, TimeUnit.SECONDS).courses().isEmpty());
            assertTrue(secondResult.get(5, TimeUnit.SECONDS).courses().isEmpty());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test(timeout = 15000)
    public void concurrentRequestsShareOneBasinCompilation() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch callers = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        HydrologyPlanner planner = planner(request -> {
            compilations.incrementAndGet();
            started.countDown();
            await(release);
            return land(70);
        });
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<HydrologyRegionalNetwork> first = workers.submit(() -> {
                callers.countDown();
                return planner.regional.draft(key);
            });
            Future<HydrologyRegionalNetwork> second = workers.submit(() -> {
                callers.countDown();
                return planner.regional.draft(key);
            });
            assertTrue(callers.await(5, TimeUnit.SECONDS));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, compilations.get());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test(timeout = 15000)
    public void clearDuringCompilationDoesNotPublishTheOldDraft() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HydrologyPlanner planner = planner(request -> {
            if (compilations.incrementAndGet() == 1) {
                started.countDown();
                await(release);
                return land(70);
            }
            return land(85);
        });
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<HydrologyRegionalNetwork> old = worker.submit(() -> planner.regional.draft(key));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            planner.regional.clear();
            HydrologyRegionalNetwork current = planner.regional.draft(key);
            release.countDown();
            assertEquals(70, old.get(5, TimeUnit.SECONDS).diagnostics().getFirst().point().y());
            assertEquals(85, current.diagnostics().getFirst().point().y());
            assertSame(current, planner.regional.draft(key));
            assertEquals(2, compilations.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    public void failedCompilationCanBeRetried() {
        AtomicInteger compilations = new AtomicInteger();
        IllegalStateException expected = new IllegalStateException("Terrain is unavailable");
        HydrologyPlanner planner = planner(request -> {
            if (compilations.incrementAndGet() == 1) {
                throw expected;
            }
            return land(70);
        });
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        assertSame(expected, assertThrows(IllegalStateException.class, () -> planner.regional.draft(key)));
        HydrologyRegionalNetwork current = planner.regional.draft(key);
        assertSame(current, planner.regional.draft(key));
        assertEquals(2, compilations.get());
    }

    @Test(timeout = 15000)
    public void aBasinWindowDispatchesIndependentDraftsBeforeWaiting() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        HydrologyPlanner planner = planner(request -> {
            compilations.incrementAndGet();
            started.countDown();
            await(release);
            return land(70);
        });
        SurfaceBounds bounds = new SurfaceBounds(0, 0, 15, 15);
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            Future<HydrologyRegionalNetwork> result = pool.submit(() -> planner.regional.coursesIn(bounds));
            assertTrue("A window must start more than one cold basin", started.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(planner(request -> land(70)).regional.coursesIn(bounds), result.get(5, TimeUnit.SECONDS));
            assertEquals(4, compilations.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test(timeout = 15000)
    public void aBasinWindowCompletesOnASaturatedSingleWorkerPool() throws Exception {
        HydrologyPlanner planner = planner(request -> land(70));
        SurfaceBounds bounds = new SurfaceBounds(0, 0, 15, 15);
        HydrologyRegionalNetwork expected = planner(request -> land(70)).regional.coursesIn(bounds);
        ForkJoinPool pool = new ForkJoinPool(1, ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null, false, 1, 1, 1, null, 60, TimeUnit.SECONDS);
        try {
            assertEquals(expected, pool.submit(() -> planner.regional.coursesIn(bounds)).get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private static HydrologyPlanner planner(
            Function<HydrologyRoutingTerrainSampler.GridRequest, HydrologyTerrainSample> grids
    ) {
        HydrologyTerrainSampler terrain = (x, z) -> land(70);
        return new HydrologyPlanner(71L, HydrologyRegionalPlannerTest.settings(false, 8), terrain,
                new ControlledRoutingSampler(grids), HydrologyGeometrySampler.deterministic(terrain), 0,
                footprint -> new HydrologyTerrainCaveVoxelView(terrain, 63, 0, 128));
    }

    private static HydrologyTerrainSample land(int height) {
        return HydrologyTerrainSample.openLand(height, 0D, "land");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for basin compilation");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private record ControlledRoutingSampler(
            Function<GridRequest, HydrologyTerrainSample> grids
    ) implements HydrologyRoutingTerrainSampler {
        @Override
        public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
            HydrologyTerrainSample[] samples = new HydrologyTerrainSample[request.width() * request.width()];
            Arrays.fill(samples, grids.apply(request));
            return samples;
        }

        @Override
        public NaturalClassification classifyNatural(int blockX, int blockZ) {
            return NaturalClassification.LAND;
        }
    }
}
