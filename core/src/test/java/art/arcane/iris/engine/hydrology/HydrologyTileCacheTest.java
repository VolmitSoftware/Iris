package art.arcane.iris.engine.hydrology;

import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.volmlib.util.cache.CacheKey;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class HydrologyTileCacheTest {
    @Test
    public void closeDrainsBackgroundPlanningAndRejectsNewDemand() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(planner.plan(key)).thenAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return tile;
        });
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4, workers);
        try {
            Future<HydrologyTile> result = callers.submit(() -> cache.get(key));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<?> closing = callers.submit(cache::close);
            awaitClosed(cache);
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            assertThrows(CancellationException.class, () -> cache.get(new HydrologyTileKey(1, 0)));
            assertThrows(CancellationException.class, () -> cache.columnAt(0, 0));
            release.countDown();
            assertSame(tile, result.get(5, TimeUnit.SECONDS));
            closing.get(5, TimeUnit.SECONDS);
            assertEquals(0, cache.size());
            verify(planner, never()).plan(new HydrologyTileKey(1, 0));
        } finally {
            release.countDown();
            workers.shutdownNow();
            callers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void closeWaitsForColumnCompositionBeforeReturning() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(tile.columnAt(anyInt(), anyInt())).thenAnswer(invocation -> {
            if (invocation.<Integer>getArgument(0) == 0 && invocation.<Integer>getArgument(1) == 0) {
                started.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return Optional.empty();
        });
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<HydrologyColumnSample>> result = callers.submit(() -> cache.columnAt(0, 0));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<?> closing = callers.submit(cache::close);
            awaitClosed(cache);
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(result.get(5, TimeUnit.SECONDS).isEmpty());
            closing.get(5, TimeUnit.SECONDS);
            assertEquals(0, cache.size());
        } finally {
            release.countDown();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void closingFromAnOwnedLoadFailsWithoutDeadlockingOrClosingTheCache() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4);
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        when(planner.plan(key)).thenAnswer(invocation -> {
            assertThrows(IllegalStateException.class, cache::close);
            return tile;
        });
        assertSame(tile, cache.get(key));
        assertSame(tile, cache.get(key));
        cache.close();
        assertThrows(CancellationException.class, () -> cache.get(key));
    }

    private static void awaitClosed(HydrologyTileCache cache) throws Exception {
        Field field = HydrologyTileCache.class.getDeclaredField("closed");
        field.setAccessible(true);
        AtomicBoolean closed = (AtomicBoolean) field.get(cache);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!closed.get() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertTrue(closed.get());
    }

    @Test
    public void explicitAreaIsFullyQueuedBeforeExecutorCallbacksCanAddOldWork() throws Exception {
        for (boolean pregeneration : new boolean[]{false, true}) {
            HydrologyPlanner planner = mock(HydrologyPlanner.class);
            HydrologyTile tile = mock(HydrologyTile.class);
            HydrologyPlannerSettings settings = stalePrefetchSettings();
            when(planner.settings()).thenReturn(settings);
            ArrayList<HydrologyTileKey> planned = new ArrayList<>();
            AtomicReference<HydrologyTileCache> cacheReference = new AtomicReference<>();
            AtomicReference<Object> monitor = new AtomicReference<>();
            AtomicBoolean injected = new AtomicBoolean();
            doAnswer(invocation -> {
                assertFalse(Thread.holdsLock(monitor.get()));
                planned.add(invocation.getArgument(0));
                return tile;
            }).when(planner).plan(any(HydrologyTileKey.class));
            HydrologyTileCache cache = new HydrologyTileCache(planner, 128, task -> {
                assertFalse(Thread.holdsLock(monitor.get()));
                if (injected.compareAndSet(false, true)) {
                    cacheReference.get().prefetchArea(0, 0, 1023, 0, 0, 0);
                }
                task.run();
            });
            cacheReference.set(cache);
            Field queue = HydrologyTileCache.class.getDeclaredField("prefetchQueue");
            queue.setAccessible(true);
            monitor.set(queue.get(cache));

            if (pregeneration) {
                cache.preparePregeneration(262144, 262144);
            } else {
                cache.prefetchArea(261120, 261120, 263168, 263168, 262144, 262144);
            }

            assertEquals(new HydrologyTileKey(256, 256), planned.getFirst());
            assertEquals(prefetchRectangle(254, 257, 254, 257), new HashSet<>(planned.subList(0, 16)));
            assertEquals(List.of(new HydrologyTileKey(255, 255), new HydrologyTileKey(256, 255),
                    new HydrologyTileKey(257, 255)), planned.subList(1, 4));
            assertEquals(prefetchRectangle(-1, 1, -1, 0), new HashSet<>(planned.subList(16, planned.size())));
            assertEquals(22, planned.size());
        }
    }

    @Test
    public void pregenDiscardsStaleQueueWithoutCancelingActivePlansOrDemand() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyPlannerSettings settings = stalePrefetchSettings();
        when(planner.settings()).thenReturn(settings);
        List<HydrologyTileKey> planned = Collections.synchronizedList(new ArrayList<>());
        HydrologyTileKey activeKey = new HydrologyTileKey(0, 0);
        HydrologyTileKey demandedKey = new HydrologyTileKey(-1, 0);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            HydrologyTileKey key = invocation.getArgument(0);
            planned.add(key);
            if (key.equals(activeKey)) {
                started.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        LinkedBlockingQueue<Runnable> queued = new LinkedBlockingQueue<>();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 128, queued::add);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            cache.prefetchArea(0, 0, 1023, 0, 0, 0);
            Runnable initial = queued.poll(5, TimeUnit.SECONDS);
            assertNotNull(initial);
            Future<?> active = callers.submit(initial);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<HydrologyTile> demanded = callers.submit(() -> cache.get(demandedKey));
            Runnable demand = queued.poll(5, TimeUnit.SECONDS);
            assertNotNull(demand);

            cache.preparePregeneration(262144, 262144);

            assertFalse(active.isDone());
            assertFalse(demanded.isDone());
            release.countDown();
            active.get(5, TimeUnit.SECONDS);
            assertTrue(queued.isEmpty());
            demand.run();
            assertSame(tile, demanded.get(5, TimeUnit.SECONDS));
            drainPrefetchTasks(queued);
            Set<HydrologyTileKey> expected = new HashSet<>(prefetchRectangle(254, 257, 254, 257));
            expected.add(activeKey);
            expected.add(demandedKey);
            assertEquals(expected, new HashSet<>(planned));
            assertEquals(18, planned.size());
            assertSame(tile, cache.get(activeKey));
            verify(planner, times(1)).plan(activeKey);
            verify(planner, times(1)).plan(demandedKey);
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void repeatedPregenPreparationRetainsTheFullCurrentLookaheadAndClearsCleanly() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyPlannerSettings settings = stalePrefetchSettings();
        when(planner.settings()).thenReturn(settings);
        ArrayList<HydrologyTileKey> planned = new ArrayList<>();
        doAnswer(invocation -> {
            planned.add(invocation.getArgument(0));
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        LinkedBlockingQueue<Runnable> queued = new LinkedBlockingQueue<>();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 128, queued::add);
        cache.prefetchArea(0, 0, 1023, 0, 0, 0);
        cache.preparePregeneration(262144, 262144);
        cache.preparePregeneration(263168, 262144);
        cache.preparePregeneration(263168, 262144);
        drainPrefetchTasks(queued);

        Set<HydrologyTileKey> expected = new HashSet<>(prefetchRectangle(255, 258, 254, 257));
        expected.add(new HydrologyTileKey(0, 0));
        assertEquals(expected, new HashSet<>(planned));
        assertEquals(17, planned.size());
        cache.clear();
        planned.clear();
        cache.preparePregeneration(263168, 262144);
        drainPrefetchTasks(queued);
        assertEquals(prefetchRectangle(255, 258, 254, 257), new HashSet<>(planned));
        assertEquals(16, planned.size());
    }

    @Test
    public void invalidPregenBoundsLeaveQueuedPrefetchIntact() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyPlannerSettings settings = stalePrefetchSettings();
        when(planner.settings()).thenReturn(settings);
        ArrayList<HydrologyTileKey> planned = new ArrayList<>();
        doAnswer(invocation -> {
            planned.add(invocation.getArgument(0));
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        LinkedBlockingQueue<Runnable> queued = new LinkedBlockingQueue<>();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 128, queued::add);
        cache.prefetchArea(0, 0, 1023, 0, 0, 0);
        assertThrows(ArithmeticException.class, () -> cache.preparePregeneration(Integer.MAX_VALUE, 0));
        assertThrows(ArithmeticException.class, () -> cache.preparePregeneration(0, Integer.MIN_VALUE));
        drainPrefetchTasks(queued);
        assertEquals(prefetchRectangle(-1, 1, -1, 0), new HashSet<>(planned));
        assertEquals(6, planned.size());
    }

    private static HydrologyPlannerSettings stalePrefetchSettings() {
        HydrologyPlannerSettings settings = mock(HydrologyPlannerSettings.class);
        when(settings.routing()).thenReturn(new HydrologyPlannerSettings.Routing(
                1024, 64, 4096, 2048, 0, 0, 0D, 0D, 0D, 0D, 1D, 0));
        when(settings.publicationRadius()).thenReturn(364);
        return settings;
    }

    private static Set<HydrologyTileKey> prefetchRectangle(int minX, int maxX, int minZ, int maxZ) {
        HashSet<HydrologyTileKey> keys = new HashSet<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                keys.add(new HydrologyTileKey(x, z));
            }
        }
        return keys;
    }

    private static void drainPrefetchTasks(LinkedBlockingQueue<Runnable> queued) {
        Runnable task;
        while ((task = queued.poll()) != null) {
            task.run();
        }
    }

    @Test
    public void equivalentStudioRuntimesReuseCompletedTiles() {
        HydrologyPlanner firstPlanner = mock(HydrologyPlanner.class);
        HydrologyPlanner secondPlanner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyTileKey key = new HydrologyTileKey(2, -3);
        HydrologyTileCache.SharedCacheScope scope = sharedScope();
        when(firstPlanner.plan(key)).thenReturn(tile);
        when(secondPlanner.settings()).thenReturn(emptySettings());
        when(tile.key()).thenReturn(key);
        when(tile.worldSeed()).thenReturn(scope.worldSeed());
        when(tile.settingsFingerprint()).thenReturn(scope.settingsFingerprint());
        when(tile.tileSize()).thenReturn(64);
        HydrologyTileCache first = new HydrologyTileCache(firstPlanner, 4, null, null, scope);
        HydrologyTileCache second = new HydrologyTileCache(secondPlanner, 4, null, null, scope);

        assertSame(tile, first.get(key));
        assertSame(tile, second.get(key));

        verify(firstPlanner, times(1)).plan(key);
        verify(secondPlanner, never()).plan(key);
        verify(secondPlanner, times(1)).reuseResolvedTile(tile);
    }

    @Test
    public void failedPlansDoNotEnterTheSharedStudioCache() {
        HydrologyPlanner failingPlanner = mock(HydrologyPlanner.class);
        HydrologyPlanner succeedingPlanner = mock(HydrologyPlanner.class);
        HydrologyTile emptyTile = mock(HydrologyTile.class);
        HydrologyTile plannedTile = mock(HydrologyTile.class);
        HydrologyTileKey key = new HydrologyTileKey(-4, 5);
        HydrologyTileCache.SharedCacheScope scope = sharedScope();
        when(failingPlanner.plan(key)).thenThrow(new IllegalStateException("test failure"));
        when(failingPlanner.emptyTile(key)).thenReturn(emptyTile);
        when(succeedingPlanner.plan(key)).thenReturn(plannedTile);
        HydrologyTileCache first = new HydrologyTileCache(failingPlanner, 4, null, null, scope);
        HydrologyTileCache second = new HydrologyTileCache(succeedingPlanner, 4, null, null, scope);

        assertSame(emptyTile, first.get(key));
        assertSame(plannedTile, second.get(key));

        verify(succeedingPlanner, times(1)).plan(key);
    }

    @Test
    public void closeWaitsForActivePlansWithoutPublishingTheirSharedTiles() throws Exception {
        HydrologyPlanner closingPlanner = mock(HydrologyPlanner.class);
        HydrologyPlanner succeedingPlanner = mock(HydrologyPlanner.class);
        HydrologyTile lateTile = mock(HydrologyTile.class);
        HydrologyTile plannedTile = mock(HydrologyTile.class);
        HydrologyTileKey key = new HydrologyTileKey(6, -2);
        HydrologyTileCache.SharedCacheScope scope = sharedScope();
        CountDownLatch planningStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        when(closingPlanner.plan(key)).thenAnswer(invocation -> {
            planningStarted.countDown();
            assertTrue(allowCompletion.await(10L, TimeUnit.SECONDS));
            return lateTile;
        });
        when(succeedingPlanner.plan(key)).thenReturn(plannedTile);
        when(succeedingPlanner.settings()).thenReturn(emptySettings());
        when(lateTile.key()).thenReturn(key);
        when(lateTile.worldSeed()).thenReturn(scope.worldSeed());
        when(lateTile.settingsFingerprint()).thenReturn(scope.settingsFingerprint());
        when(lateTile.tileSize()).thenReturn(64);
        HydrologyTileCache closing = new HydrologyTileCache(closingPlanner, 4, null, null, scope);
        HydrologyTileCache succeeding = new HydrologyTileCache(succeedingPlanner, 4, null, null, scope);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HydrologyTile> late = executor.submit(() -> closing.get(key));
            assertTrue(planningStarted.await(10L, TimeUnit.SECONDS));
            Future<?> shutdown = executor.submit(closing::close);
            assertThrows(TimeoutException.class, () -> shutdown.get(100L, TimeUnit.MILLISECONDS));
            allowCompletion.countDown();
            assertSame(lateTile, late.get(10L, TimeUnit.SECONDS));
            shutdown.get(10L, TimeUnit.SECONDS);
            assertEquals(0, closing.size());
            assertSame(plannedTile, succeeding.get(key));
            verify(succeedingPlanner, times(1)).plan(key);
        } finally {
            allowCompletion.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void chunkPreparationUsesHeightFillColumnOrder() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        ArrayList<Long> visitedColumns = new ArrayList<>();
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        doAnswer(invocation -> {
            int blockX = invocation.getArgument(0);
            int blockZ = invocation.getArgument(1);
            visitedColumns.add(RiverFootprint.pack(blockX, blockZ));
            return Optional.empty();
        }).when(tile).columnAt(anyInt(), anyInt());
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4);

        cache.prepareChunkColumns(32, 48);

        assertEquals(256, visitedColumns.size());
        int index = 0;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                assertEquals(
                        RiverFootprint.pack(32 + localX, 48 + localZ),
                        visitedColumns.get(index).longValue()
                );
                index++;
            }
        }
        verify(planner, times(1)).plan(new HydrologyTileKey(0, 0));
    }

    @Test
    public void concurrentSameKeyRequestsPlanOnlyOnce() throws Exception {
        AtomicInteger planningStarts = new AtomicInteger();
        HydrologyTerrainSampler sampler = (int x, int z) -> HydrologyTerrainSample.openLand(90, 0D, "parent");
        HydrologyPlanner planner = new HydrologyPlanner(
                41L,
                emptySettings(),
                sampler,
                -4096,
                footprint -> {
                    planningStarts.incrementAndGet();
                    return new HydrologyTerrainCaveVoxelView(sampler, 63, -4096, 4096);
                }
        );
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            ArrayList<Callable<HydrologyTile>> tasks = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                tasks.add(() -> cache.get(new HydrologyTileKey(0, 0)));
            }
            List<Future<HydrologyTile>> futures = executor.invokeAll(tasks);
            HydrologyTile expected = futures.getFirst().get(10L, TimeUnit.SECONDS);
            for (Future<HydrologyTile> future : futures) {
                assertSame(expected, future.get(10L, TimeUnit.SECONDS));
            }
            assertEquals(1, planningStarts.get());
            assertEquals(1, cache.size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void completedTilesAreEvictedAtTheConfiguredBound() {
        HydrologyPlanner planner = new HydrologyPlanner(
                73L,
                emptySettings(),
                (int x, int z) -> HydrologyTerrainSample.openLand(90, 0D, "parent")
        );
        HydrologyTileCache cache = new HydrologyTileCache(planner, 2);

        for (int tileX = 0; tileX < 8; tileX++) {
            cache.get(new HydrologyTileKey(tileX, 0));
            assertTrue(cache.size() <= 2);
        }

        assertEquals(2, cache.maximumEntries());
        assertEquals(2, cache.size());
    }

    @Test
    public void compositePublicationIsIndependentOfTileAndColumnQueryOrder() {
        HydrologyPlanner planner = new HydrologyPlanner(811L, featureSettings(), this::featureTerrain);
        HydrologyTile origin = planner.plan(new HydrologyTileKey(0, 0));
        HydrologyColumnSample neighboringColumn = null;
        HydrologyColumnSample ownerColumn = null;
        for (HydrologyColumnSample column : origin.footprint().columns().values()) {
            if (new HydrologyTileKey(0, 0).contains(column.x(), column.z(), origin.tileSize())) {
                ownerColumn = column;
            } else if (!column.ocean()) {
                neighboringColumn = column;
            }
            if (ownerColumn != null && neighboringColumn != null) {
                break;
            }
        }
        assertTrue(origin.footprint().columns().values().stream().anyMatch(
                (HydrologyColumnSample column) -> !new HydrologyTileKey(0, 0)
                        .contains(column.x(), column.z(), origin.tileSize())));
        assertTrue(ownerColumn != null && neighboringColumn != null);
        HydrologyTileKey neighboringKey = HydrologyTileKey.fromBlock(
                neighboringColumn.x(),
                neighboringColumn.z(),
                origin.tileSize()
        );
        assertFalse(planner.plan(neighboringKey)
                .columnAt(neighboringColumn.x(), neighboringColumn.z())
                .isPresent());

        HydrologyTileCache firstOrder = new HydrologyTileCache(planner, 8);
        HydrologyColumnSample firstNeighbor = firstOrder
                .columnAt(neighboringColumn.x(), neighboringColumn.z())
                .orElseThrow();
        HydrologyColumnSample firstOwner = firstOrder.columnAt(ownerColumn.x(), ownerColumn.z()).orElseThrow();
        HydrologyTileCache reverseOrder = new HydrologyTileCache(planner, 8);
        HydrologyColumnSample secondOwner = reverseOrder.columnAt(ownerColumn.x(), ownerColumn.z()).orElseThrow();
        HydrologyColumnSample secondNeighbor = reverseOrder
                .columnAt(neighboringColumn.x(), neighboringColumn.z())
                .orElseThrow();

        assertEquals(firstNeighbor, secondNeighbor);
        assertEquals(firstOwner, secondOwner);
        assertEquals(firstNeighbor.renderSample(), reverseOrder.renderAt(neighboringColumn.x(), neighboringColumn.z()));
        assertTrue(firstNeighbor.layers().containsAll(neighboringColumn.layers()));
    }

    @Test
    public void realPlannedFeatureIdsAndTypesReachTheRendererAtExactCoordinates() {
        HydrologyPlanner planner = new HydrologyPlanner(811L, featureSettings(), this::featureTerrain);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 8);
        HydrologyTile planned = cache.get(new HydrologyTileKey(0, 0));
        HydrologyColumnSample plannedColumn = planned.footprint().columns().values().stream()
                .filter(HydrologyColumnSample::present)
                .findFirst()
                .orElseThrow();
        HydrologyColumnSample accepted = cache.columnAt(plannedColumn.x(), plannedColumn.z()).orElseThrow();
        HydrologyRenderSample rendered = cache.renderAt(plannedColumn.x(), plannedColumn.z());
        Map<Long, HydrologyFeatureType> acceptedFeatures = new HashMap<>();
        Map<Long, HydrologyFeatureType> renderedFeatures = new HashMap<>();
        for (HydrologyColumnLayer layer : accepted.layers()) {
            acceptedFeatures.put(layer.feature().id(), layer.feature().type());
        }
        for (HydrologyFeatureRef feature : rendered.features()) {
            renderedFeatures.put(feature.id(), feature.type());
        }

        assertFalse(acceptedFeatures.isEmpty());
        assertEquals(plannedColumn.x(), rendered.x());
        assertEquals(plannedColumn.z(), rendered.z());
        assertEquals(acceptedFeatures, renderedFeatures);
        for (HydrologyColumnLayer layer : plannedColumn.layers()) {
            assertEquals(layer.feature().type(), renderedFeatures.get(layer.feature().id()));
        }
    }

    @Test
    public void concurrentDifferentTileAndCompositeQueriesRemainDeterministic() throws Exception {
        HydrologyPlanner planner = new HydrologyPlanner(1211L, featureSettings(), this::featureTerrain);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 16);
        List<HydrologyTileKey> keys = List.of(
                new HydrologyTileKey(-1, 0),
                new HydrologyTileKey(0, 0),
                new HydrologyTileKey(1, 0)
        );
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            ArrayList<Callable<HydrologyTile>> tileTasks = new ArrayList<>();
            for (int index = 0; index < 36; index++) {
                HydrologyTileKey key = keys.get(index % keys.size());
                tileTasks.add(() -> cache.get(key));
            }
            List<Future<HydrologyTile>> futures = executor.invokeAll(tileTasks);
            Map<HydrologyTileKey, HydrologyTile> firstByKey = new HashMap<>();
            for (int index = 0; index < futures.size(); index++) {
                HydrologyTileKey key = keys.get(index % keys.size());
                HydrologyTile tile = futures.get(index).get(10L, TimeUnit.SECONDS);
                HydrologyTile existing = firstByKey.putIfAbsent(key, tile);
                if (existing != null) {
                    assertSame(existing, tile);
                }
                assertEquals(planner.plan(key), tile);
            }
            HydrologyColumnSample outside = planner.plan(new HydrologyTileKey(0, 0)).footprint().columns().values()
                    .stream()
                    .filter((HydrologyColumnSample column) -> column.x() >= 64 && !column.ocean())
                    .findFirst()
                    .orElseThrow();
            HydrologyColumnSample expected = cache.columnAt(outside.x(), outside.z()).orElseThrow();
            ArrayList<Callable<HydrologyColumnSample>> columnTasks = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                columnTasks.add(() -> cache.columnAt(outside.x(), outside.z()).orElseThrow());
            }
            for (Future<HydrologyColumnSample> future : executor.invokeAll(columnTasks)) {
                assertEquals(expected, future.get(10L, TimeUnit.SECONDS));
                assertEquals(expected.renderSample(), future.get(10L, TimeUnit.SECONDS).renderSample());
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10L, TimeUnit.SECONDS));
        }
    }

    private HydrologyPlannerSettings emptySettings() {
        HydrologyPlannerSettings.Source disabled = new HydrologyPlannerSettings.Source(
                false,
                0D,
                Integer.MIN_VALUE,
                0,
                0,
                0
        );
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(64, 16, 128, 64, 0, 0, 0D, 0D, 0D, 0D, 1D, 0),
                new HydrologyPlannerSettings.Surface(false, disabled, 2, 4, 1, 2, 4, 1D,
                        HydrologyPlannerSettings.Banks.defaults()),
                new HydrologyPlannerSettings.Hydraulics(3),
                HydrologyPlannerSettings.Underground.of(false, disabled, -32, 32, 2, 4, 1, 2, 3, 4, false, 0),
                HydrologyPlannerSettings.Outlets.of(
                        false,
                        new HydrologyPlannerSettings.Grotto(false, 2, 2, 2, 512),
                        new HydrologyPlannerSettings.Grotto(false, 2, 2, 2, 512),
                        false,
                        8,
                        16,
                        1,
                        2,
                        2
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private HydrologyTileCache.SharedCacheScope sharedScope() {
        return new HydrologyTileCache.SharedCacheScope(
                UUID.randomUUID().toString(),
                91L,
                384,
                "overworld",
                17L);
    }

    private HydrologyPlannerSettings featureSettings() {
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(
                true,
                1D,
                80,
                0,
                1,
                16
        );
        HydrologyPlannerSettings.Source disabled = new HydrologyPlannerSettings.Source(
                false,
                0D,
                Integer.MIN_VALUE,
                0,
                0,
                0
        );
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(64, 16, 128, 96, 16, 8, 0.5D, 12D, 0.5D, 0.1D, 1D, 0),
                new HydrologyPlannerSettings.Surface(true, surfaceSources, 4, 8, 2, 3, 20, 1.5D, tileBoundedBanks()),
                new HydrologyPlannerSettings.Hydraulics(3),
                HydrologyPlannerSettings.Underground.of(false, disabled, -32, 32, 2, 4, 1, 2, 3, 4, false, 0),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        new HydrologyPlannerSettings.Grotto(false, 2, 2, 2, 512),
                        new HydrologyPlannerSettings.Grotto(false, 2, 2, 2, 512),
                        false,
                        8,
                        16,
                        1,
                        2,
                        2
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    // Small-tile settings need a blend envelope that fits inside the bounded cross-tile admission period.
    private static HydrologyPlannerSettings.Banks tileBoundedBanks() {
        return HydrologyPlannerSettings.Banks.of(0, 3D, 4, 4, 0.25D, 16, 2, 6, 1.6D, HydrologyPlannerSettings.Inlet.none(), 2.5D, 24, true,
                HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.defaults());
    }

    private HydrologyTerrainSample featureTerrain(int x, int z) {
        if (x >= 80) {
            return HydrologyTerrainSample.ocean(54, "ocean_parent");
        }
        boolean source = x >= 0 && x <= 16;
        return new HydrologyTerrainSample(
                104 - Math.floorDiv(x, 8),
                1D,
                false,
                false,
                40,
                42,
                true,
                true,
                source,
                source,
                false,
                false,
                0D,
                source ? 1D : 0D,
                0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                "parent",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("default"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true
        );
    }

    @Test
    public void planningFailureFallsBackToTheEmptyTileAndIsCached() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTileKey key = new HydrologyTileKey(3, -2);
        HydrologyTile empty = new HydrologyTile(key, 7L, 11L, 1024, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), RiverFootprint.empty());
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(key)).thenThrow(new IllegalStateException("Hydrology natural height was not finite at -66,-641"));
        when(planner.emptyTile(key)).thenReturn(empty);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4);

        assertSame(empty, cache.get(key));
        assertSame(empty, cache.get(key));

        verify(planner, times(1)).plan(key);
        assertTrue(cache.get(key).courses().isEmpty());
    }

    @Test
    public void plannerEmptyTileCarriesTheTileIdentityAndNoContent() {
        HydrologyPlanner planner = new HydrologyPlanner(811L, featureSettings(), this::featureTerrain);
        HydrologyTileKey key = new HydrologyTileKey(-4, 9);

        HydrologyTile tile = planner.emptyTile(key);

        assertEquals(key, tile.key());
        assertEquals(featureSettings().routing().tileSize(), tile.tileSize());
        assertTrue(tile.courses().isEmpty());
        assertTrue(tile.outlets().isEmpty());
        assertTrue(tile.columnAt(0, 0).isEmpty());
    }

    @Test
    public void chunkPreparationPrefetchesTheRingOfTilesAroundTheOnesItNeeded() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyPlannerSettings settings = emptySettings();
        when(planner.settings()).thenReturn(settings);
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        when(tile.columnAt(anyInt(), anyInt())).thenReturn(Optional.empty());
        HydrologyTileCache eager = new HydrologyTileCache(planner, 64, Runnable::run);
        HydrologyTileCache lazy = new HydrologyTileCache(planner, 64);

        eager.prepareChunkColumns(32, 48);
        lazy.prepareChunkColumns(32, 48);

        int tileSize = settings.routing().tileSize();
        int radius = settings.publicationRadius();
        int width = Math.floorDiv(32 + 15 + radius, tileSize) - Math.floorDiv(32 - radius, tileSize) + 1;
        int height = Math.floorDiv(48 + 15 + radius, tileSize) - Math.floorDiv(48 - radius, tileSize) + 1;
        assertEquals(width * height, lazy.size());
        assertEquals((width + 2) * (height + 2), eager.size());
    }

    @Test
    public void disabledNeighbourPrefetchPlansOnlyRequiredTiles() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyPlannerSettings settings = emptySettings();
        when(planner.settings()).thenReturn(settings);
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        when(tile.columnAt(anyInt(), anyInt())).thenReturn(Optional.empty());
        HydrologyTileCache cache = new HydrologyTileCache(planner, 64, Runnable::run);
        cache.setNeighbourPrefetchEnabled(false);

        cache.prepareChunkColumns(32, 48);

        int tileSize = settings.routing().tileSize();
        int radius = settings.publicationRadius();
        int width = Math.floorDiv(32 + 15 + radius, tileSize) - Math.floorDiv(32 - radius, tileSize) + 1;
        int height = Math.floorDiv(48 + 15 + radius, tileSize) - Math.floorDiv(48 - radius, tileSize) + 1;
        assertEquals(width * height, cache.size());
    }

    @Test
    public void pregenPreparationStartsAtTheNearestUncachedForwardTile() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyPlannerSettings settings = emptySettings();
        when(planner.settings()).thenReturn(settings);
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        List<HydrologyTileKey> order = new ArrayList<>();
        doAnswer(invocation -> {
            order.add(invocation.getArgument(0));
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        HydrologyTileCache cache = new HydrologyTileCache(planner, 64, Runnable::run);
        cache.get(new HydrologyTileKey(-1, -1));
        cache.get(new HydrologyTileKey(0, -1));
        cache.get(new HydrologyTileKey(-1, 0));
        cache.get(new HydrologyTileKey(0, 0));
        order.clear();
        cache.setNeighbourPrefetchEnabled(false);

        cache.preparePregeneration(0, 0);

        assertEquals(new HydrologyTileKey(1, -1), order.getFirst());
        assertTrue(order.contains(new HydrologyTileKey(1, 0)));
        assertTrue(order.contains(new HydrologyTileKey(0, 1)));
    }

    @Test
    public void areaPrefetchPlansEveryTouchingTileNearestTheCentreFirst() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        HydrologyPlannerSettings settings = emptySettings();
        when(planner.settings()).thenReturn(settings);
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        List<HydrologyTileKey> order = new java.util.ArrayList<>();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 256, Runnable::run);
        org.mockito.Mockito.doAnswer(invocation -> {
            order.add(invocation.getArgument(0));
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));

        int tileSize = settings.routing().tileSize();
        cache.prefetchArea(0, 0, tileSize * 2 - 1, tileSize * 2 - 1, tileSize, tileSize);

        int radius = settings.publicationRadius();
        int extra = radius > 0 ? 1 : 0;
        int span = 2 + 2 * extra;
        assertEquals(span * span, order.size());
        assertEquals(new HydrologyTileKey(1, 1), order.getFirst());
        assertTrue(order.subList(0, 4).containsAll(List.of(new HydrologyTileKey(0, 0), new HydrologyTileKey(1, 0), new HydrologyTileKey(0, 1), new HydrologyTileKey(1, 1))));
    }
    @Test
    public void tileBatchesPlanMissingTilesConcurrentlyOnThePrefetchExecutor() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch allStarted = new CountDownLatch(4);
        doAnswer(invocation -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            allStarted.countDown();
            assertTrue("every batch member should plan at the same time", allStarted.await(5, TimeUnit.SECONDS));
            inFlight.decrementAndGet();
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 64, executor);
            List<HydrologyTileKey> keys = List.of(
                    new HydrologyTileKey(0, 0),
                    new HydrologyTileKey(1, 0),
                    new HydrologyTileKey(2, 0),
                    new HydrologyTileKey(3, 0)
            );

            List<HydrologyTile> tiles = cache.tiles(keys);

            assertEquals(4, tiles.size());
            assertEquals(4, peak.get());
            verify(planner, times(4)).plan(any(HydrologyTileKey.class));
            assertEquals(4, cache.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void collidingTileHashesDoNotSerializePlanning() throws Exception {
        List<HydrologyTileKey> keys = List.of(new HydrologyTileKey(0, 31), new HydrologyTileKey(1, 0));
        assertEquals(keys.getFirst().hashCode(), keys.getLast().hashCode());
        assertConcurrentTilePlanning(keys);
    }

    @Test
    public void diagonalColdTilesDoNotSerializePlanning() throws Exception {
        assertConcurrentTilePlanning(List.of(new HydrologyTileKey(127, 127), new HydrologyTileKey(128, 128)));
    }

    private void assertConcurrentTilePlanning(List<HydrologyTileKey> keys) throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        CountDownLatch started = new CountDownLatch(keys.size());
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        ExecutorService executor = Executors.newFixedThreadPool(keys.size());
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 64, executor);
            Future<List<HydrologyTile>> result = caller.submit(() -> cache.tiles(keys));
            assertTrue("colliding keys must start before either plan completes", started.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(keys.size(), result.get(5, TimeUnit.SECONDS).size());
            for (HydrologyTileKey key : keys) {
                verify(planner, times(1)).plan(key);
            }
        } finally {
            release.countDown();
            caller.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test
    public void collidingChunkCacheBinsDoNotSerializeComposition() throws Exception {
        int secondChunkX = 207;
        int firstHash = Long.hashCode(CacheKey.mix(RiverFootprint.pack(0, 0)));
        int secondHash = Long.hashCode(CacheKey.mix(RiverFootprint.pack(secondChunkX, 0)));
        assertEquals((firstHash ^ (firstHash >>> 16)) & 127, (secondHash ^ (secondHash >>> 16)) & 127);
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            int blockX = invocation.getArgument(0);
            int blockZ = invocation.getArgument(1);
            if ((blockX == 0 || blockX == secondChunkX * 16) && blockZ == 0) {
                started.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return Optional.empty();
        }).when(tile).columnAt(anyInt(), anyInt());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 64);
            Future<?> first = executor.submit(() -> cache.prepareChunkColumns(0, 0));
            Future<?> second = executor.submit(() -> cache.prepareChunkColumns(secondChunkX * 16, 0));
            assertTrue("colliding chunks must compose before either finishes", started.await(5, TimeUnit.SECONDS));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void concurrentSameChunkRequestsComposeOnlyOnce() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        when(tile.columnAt(anyInt(), anyInt())).thenReturn(Optional.empty());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 64);
            List<Callable<Optional<HydrologyColumnSample>>> calls = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                calls.add(() -> cache.columnAt(0, 0));
            }
            for (Future<Optional<HydrologyColumnSample>> result : executor.invokeAll(calls, 5, TimeUnit.SECONDS)) {
                assertTrue(result.get(5, TimeUnit.SECONDS).isEmpty());
            }
            verify(tile, times(256)).columnAt(anyInt(), anyInt());
            verify(planner, times(1)).plan(new HydrologyTileKey(0, 0));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void inlineWorkerCanClaimItsQueuedPrefetch() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        ForkJoinPool executor = new ForkJoinPool(1);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 8, executor);
            HydrologyTileKey key = new HydrologyTileKey(0, 0);
            HydrologyTile result = executor.submit(() -> {
                cache.prefetchArea(0, 0, 0, 0, 0, 0);
                return cache.get(key);
            }).get(5, TimeUnit.SECONDS);
            assertSame(tile, result);
            assertTrue(executor.awaitQuiescence(5, TimeUnit.SECONDS));
            verify(planner, times(1)).plan(key);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void clearDoesNotJoinOrPublishAnOldTilePlan() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile oldTile = mock(HydrologyTile.class);
        HydrologyTile newTile = mock(HydrologyTile.class);
        HydrologyTileKey key = new HydrologyTileKey(0, 0);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                started.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return oldTile;
            }
            return newTile;
        }).when(planner).plan(key);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 8, executor);
            Future<HydrologyTile> oldResult = callers.submit(() -> cache.get(key));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            cache.clear();
            assertSame(newTile, callers.submit(() -> cache.get(key)).get(5, TimeUnit.SECONDS));
            release.countDown();
            assertSame(oldTile, oldResult.get(5, TimeUnit.SECONDS));
            assertSame(newTile, cache.get(key));
            assertEquals(2, calls.get());
        } finally {
            release.countDown();
            callers.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test
    public void queuedPrefetchCannotPublishAcrossClear() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        List<Runnable> tasks = new ArrayList<>();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 8, tasks::add);

        cache.prefetchArea(0, 0, 0, 0, 0, 0);
        cache.clear();
        tasks.removeFirst().run();

        assertEquals(0, cache.size());
        cache.prefetchArea(0, 0, 0, 0, 0, 0);
        tasks.removeFirst().run();
        assertEquals(1, cache.size());
        verify(planner, times(2)).plan(new HydrologyTileKey(0, 0));
    }

    @Test
    public void clearDoesNotJoinOrPublishOldChunkColumns() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile oldTile = mock(HydrologyTile.class);
        HydrologyTile newTile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(oldTile, newTile);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            int blockX = invocation.getArgument(0);
            int blockZ = invocation.getArgument(1);
            if (blockX == 0 && blockZ == 0) {
                started.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return Optional.empty();
        }).when(oldTile).columnAt(anyInt(), anyInt());
        HydrologyColumnSample sample = new HydrologyColumnSample(0, 0, 90, 63, false, "parent", List.of());
        when(newTile.columnAt(anyInt(), anyInt())).thenReturn(Optional.of(sample));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 8);
            Future<Optional<HydrologyColumnSample>> oldResult = executor.submit(() -> cache.columnAt(0, 0));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            cache.clear();
            assertEquals(Optional.of(sample), executor.submit(() -> cache.columnAt(0, 0)).get(5, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(oldResult.get(5, TimeUnit.SECONDS).isEmpty());
            assertEquals(Optional.of(sample), cache.columnAt(0, 0));
            verify(newTile, times(256)).columnAt(anyInt(), anyInt());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void recursiveChunkCompositionFailsInsteadOfJoiningItself() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 8);
        doAnswer(invocation -> cache.columnAt(0, 0)).when(tile).columnAt(anyInt(), anyInt());

        org.junit.Assert.assertThrows(IllegalStateException.class, () -> cache.columnAt(0, 0));
    }

    @Test
    public void tileBatchesNeverKeepMoreThanHalfTheCacheBoundInFlight() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        List<HydrologyTileKey> planned = java.util.Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            awaitConcurrentPlan(inFlight, 20L);
            planned.add(invocation.getArgument(0));
            inFlight.decrementAndGet();
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 4, executor);
            ArrayList<HydrologyTileKey> keys = new ArrayList<>();
            for (int tileX = 0; tileX < 6; tileX++) {
                keys.add(new HydrologyTileKey(tileX, 0));
            }

            List<HydrologyTile> tiles = cache.tiles(keys);

            assertEquals(6, tiles.size());
            assertEquals(6, planned.size());
            assertTrue("in flight " + peak.get(), peak.get() <= 2);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitConcurrentPlan(AtomicInteger inFlight, long boundMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(boundMillis);
        while (inFlight.get() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
    }

    private static void awaitParkedCallers(List<Thread> callers, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (callers.size() == expected && allParked(callers)) {
                return;
            }
            Thread.sleep(1L);
        }
        throw new AssertionError("Callers did not park on the in-flight plan");
    }

    private static boolean allParked(List<Thread> callers) {
        for (Thread caller : callers) {
            Thread.State state = caller.getState();
            if (state != Thread.State.WAITING
                    && state != Thread.State.TIMED_WAITING
                    && state != Thread.State.BLOCKED) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void tileBatchesWithoutAPrefetchExecutorPlanInlineInKeyOrder() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        when(planner.settings()).thenReturn(emptySettings());
        List<HydrologyTileKey> planned = new ArrayList<>();
        doAnswer(invocation -> {
            planned.add(invocation.getArgument(0));
            return mock(HydrologyTile.class);
        }).when(planner).plan(any(HydrologyTileKey.class));
        HydrologyTileCache cache = new HydrologyTileCache(planner, 8);
        List<HydrologyTileKey> keys = List.of(new HydrologyTileKey(2, 1), new HydrologyTileKey(0, 0), new HydrologyTileKey(1, 1));

        List<HydrologyTile> tiles = cache.tiles(keys);

        assertEquals(keys, planned);
        assertEquals(3, tiles.size());
        assertSame(cache.get(keys.get(1)), tiles.get(1));
    }

    @Test
    public void tileBatchesJoinAPlanAlreadyInFlightInsteadOfQueueingAnother() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger plans = new AtomicInteger();
        doAnswer(invocation -> {
            plans.incrementAndGet();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 64, executor);
            HydrologyTileKey key = new HydrologyTileKey(5, 5);
            ExecutorService callers = Executors.newFixedThreadPool(3);
            try {
                List<Thread> callerThreads = new CopyOnWriteArrayList<>();
                List<Future<List<HydrologyTile>>> results = new ArrayList<>();
                for (int caller = 0; caller < 3; caller++) {
                    results.add(callers.submit(() -> {
                        callerThreads.add(Thread.currentThread());
                        return cache.tiles(List.of(key));
                    }));
                }
                awaitParkedCallers(callerThreads, 3);
                release.countDown();
                for (Future<List<HydrologyTile>> result : results) {
                    assertSame(tile, result.get(5, TimeUnit.SECONDS).getFirst());
                }
            } finally {
                callers.shutdownNow();
            }
            assertEquals(1, plans.get());
        } finally {
            executor.shutdownNow();
        }
    }
    @Test
    public void interruptedPlanningIsNotPublishedAsAnEmptyTile() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        AtomicInteger plans = new AtomicInteger();
        doAnswer(invocation -> {
            if (plans.incrementAndGet() == 1) {
                throw new IllegalStateException("Interrupted", new InterruptedException());
            }
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        HydrologyTileCache cache = new HydrologyTileCache(planner, 8);
        HydrologyTileKey key = new HydrologyTileKey(3, 4);

        IllegalStateException interrupted = org.junit.Assert.assertThrows(IllegalStateException.class, () -> cache.get(key));

        assertEquals("Interrupted", interrupted.getMessage());
        assertEquals(0, cache.size());
        assertSame(tile, cache.get(key));
        assertEquals(2, plans.get());
        verify(planner, org.mockito.Mockito.never()).emptyTile(any(HydrologyTileKey.class));
    }

    @Test
    public void directTileRequestsPlanOnThePrefetchExecutorInsteadOfTheCaller() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        List<String> planningThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> {
            planningThreads.add(Thread.currentThread().getName());
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "hydrology-planning-executor"));
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 8, executor);

            assertSame(tile, cache.get(new HydrologyTileKey(7, 7)));

            assertEquals(List.of("hydrology-planning-executor"), planningThreads);
        } finally {
            executor.shutdownNow();
        }
    }
    @Test
    public void unrelatedForkJoinWorkersUseTheHydrologyExecutor() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        List<String> planningThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> {
            planningThreads.add(Thread.currentThread().getName());
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "hydrology-planning-executor"));
        ForkJoinPool pool = new ForkJoinPool(1);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 8, executor);

            HydrologyTile planned = pool.submit(() -> cache.get(new HydrologyTileKey(9, 9))).get(5, TimeUnit.SECONDS);
            List<HydrologyTile> batch = pool.submit(() -> cache.tiles(List.of(new HydrologyTileKey(10, 9), new HydrologyTileKey(11, 9)))).get(5, TimeUnit.SECONDS);

            assertSame(tile, planned);
            assertEquals(2, batch.size());
            assertEquals(3, planningThreads.size());
            assertEquals(List.of(
                    "hydrology-planning-executor",
                    "hydrology-planning-executor",
                    "hydrology-planning-executor"
            ), planningThreads);
        } finally {
            pool.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test
    public void hydrologyForkJoinWorkersPlanInlineInTheirOwnPool() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        List<String> planningThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> {
            planningThreads.add(Thread.currentThread().getName());
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        ForkJoinPool executor = new ForkJoinPool(1);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 8, executor);

            HydrologyTile planned = executor.submit(() -> cache.get(new HydrologyTileKey(9, 9)))
                    .get(5, TimeUnit.SECONDS);

            assertSame(tile, planned);
            assertEquals(1, planningThreads.size());
            assertTrue(planningThreads.getFirst().startsWith("ForkJoinPool-"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void multiBurstWorkersPlanInlineInTheirOwnPool() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        AtomicReference<Thread> planningThread = new AtomicReference<>();
        doAnswer(invocation -> {
            planningThread.set(Thread.currentThread());
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        MultiBurst executor = new MultiBurst("Hydrology cache test", () -> 1);
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 8, executor);

            Boolean plannedInline = executor.submit(() -> {
                Thread caller = Thread.currentThread();
                HydrologyTile planned = cache.get(new HydrologyTileKey(9, 9));
                return planned == tile && planningThread.get() == caller;
            }).get(5, TimeUnit.SECONDS);

            assertTrue(plannedInline);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void areaPrefetchSerializesSpeculativeRootsInsideTheCacheBound() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        List<Runnable> queued = new ArrayList<>();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 4, queued::add);
        int tileSize = emptySettings().routing().tileSize();

        cache.prefetchArea(0, 0, tileSize * 8 - 1, tileSize - 1, 0, 0);

        assertEquals(1, queued.size());
        verify(planner, org.mockito.Mockito.never()).plan(any(HydrologyTileKey.class));

        while (!queued.isEmpty()) {
            Runnable task = queued.removeFirst();
            task.run();
        }
        cache.prefetchArea(0, 0, tileSize * 8 - 1, tileSize - 1, 0, 0);

        assertTrue(queued.isEmpty());
        assertEquals(4, cache.size());
        verify(planner, times(4)).plan(any(HydrologyTileKey.class));
    }

    @Test
    public void speculativeQueueWaitsForEveryDemandAndSharesQueuedPlans() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        LinkedBlockingQueue<Runnable> queued = new LinkedBlockingQueue<>();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 64, queued::add);
        int tileSize = emptySettings().routing().tileSize();
        cache.prefetchArea(0, 0, tileSize * 4 - 1, 0, 0, 0);
        Runnable initialPrefetch = queued.poll(5, TimeUnit.SECONDS);
        assertNotNull(initialPrefetch);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            List<HydrologyTileKey> keys = List.of(
                    new HydrologyTileKey(0, 0), new HydrologyTileKey(1, 0), new HydrologyTileKey(2, 0));
            Future<List<HydrologyTile>> result = caller.submit(() -> cache.tiles(keys));
            Runnable firstDemand = queued.poll(5, TimeUnit.SECONDS);
            Runnable secondDemand = queued.poll(5, TimeUnit.SECONDS);
            assertNotNull(firstDemand);
            assertNotNull(secondDemand);

            initialPrefetch.run();
            assertTrue(queued.isEmpty());
            assertSame(tile, cache.get(new HydrologyTileKey(0, 0)));
            assertTrue(queued.isEmpty());
            firstDemand.run();
            assertTrue(queued.isEmpty());
            secondDemand.run();
            assertEquals(3, result.get(5, TimeUnit.SECONDS).size());
            Runnable resumedPrefetch = queued.poll(5, TimeUnit.SECONDS);
            assertNotNull(resumedPrefetch);
            resumedPrefetch.run();

            assertTrue(queued.isEmpty());
            for (int index = 0; index < 4; index++) {
                verify(planner, times(1)).plan(new HydrologyTileKey(index, 0));
            }
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    public void demandCompletingBeforeActivePrefetchDoesNotLoseResume() throws Exception {
        assertDemandPrefetchResume(true, false);
    }

    @Test
    public void immediateAsyncBatchRegistersAllDemandBeforePrefetchResumes() throws Exception {
        assertBatchDemandOrder(false);
    }

    @Test
    public void inlineBatchRegistersAllDemandBeforePrefetchResumes() throws Exception {
        assertBatchDemandOrder(true);
    }

    private void assertBatchDemandOrder(boolean inline) throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        ArrayList<HydrologyTileKey> order = new ArrayList<>();
        AtomicReference<HydrologyTileCache> cacheReference = new AtomicReference<>();
        int tileSize = emptySettings().routing().tileSize();
        doAnswer(invocation -> {
            HydrologyTileKey key = invocation.getArgument(0);
            order.add(key);
            if (key.tileX() == 1) {
                cacheReference.get().prefetchArea(0, 0, tileSize * 4 - 1, 0, 0, 0);
            }
            return tile;
        }).when(planner).plan(any(HydrologyTileKey.class));
        ForkJoinPool pool = new ForkJoinPool(1) {
            @Override
            public void execute(Runnable task) {
                task.run();
            }
        };
        try {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 64, inline ? pool : Runnable::run);
            cacheReference.set(cache);
            List<HydrologyTileKey> keys = List.of(new HydrologyTileKey(1, 0), new HydrologyTileKey(2, 0));
            if (inline) {
                assertEquals(2, pool.submit(() -> cache.tiles(keys)).get(5, TimeUnit.SECONDS).size());
            } else {
                assertEquals(2, cache.tiles(keys).size());
            }
            assertEquals(List.of(new HydrologyTileKey(1, 0), new HydrologyTileKey(2, 0),
                    new HydrologyTileKey(0, 0), new HydrologyTileKey(3, 0)), order);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void failedDemandResumesPausedPrefetch() throws Exception {
        assertDemandPrefetchResume(false, true);
    }

    private void assertDemandPrefetchResume(boolean demandFirst, boolean failDemand) throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        HydrologyTileKey demandKey = new HydrologyTileKey(1, 0);
        if (failDemand) {
            when(planner.plan(demandKey)).thenThrow(new IllegalStateException("Interrupted", new InterruptedException()));
        }
        LinkedBlockingQueue<Runnable> queued = new LinkedBlockingQueue<>();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 64, queued::add);
        int tileSize = emptySettings().routing().tileSize();
        cache.prefetchArea(0, 0, tileSize * 3 - 1, 0, 0, 0);
        Runnable initialPrefetch = queued.poll(5, TimeUnit.SECONDS);
        assertNotNull(initialPrefetch);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<HydrologyTile> result = caller.submit(() -> cache.get(demandKey));
            Runnable demand = queued.poll(5, TimeUnit.SECONDS);
            assertNotNull(demand);
            if (demandFirst) {
                demand.run();
                assertTrue(queued.isEmpty());
                initialPrefetch.run();
            } else {
                initialPrefetch.run();
                assertTrue(queued.isEmpty());
                demand.run();
            }
            if (failDemand) {
                assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
            } else {
                assertSame(tile, result.get(5, TimeUnit.SECONDS));
            }
            Runnable resumed = queued.poll(5, TimeUnit.SECONDS);
            assertNotNull(resumed);
            resumed.run();
            assertTrue(queued.isEmpty());
            verify(planner, times(1)).plan(new HydrologyTileKey(2, 0));
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    public void clearDiscardsOldDemandPauseAndSpeculativeQueue() throws Exception {
        assertDemandPrefetchInvalidation(false);
    }

    @Test
    public void closePreventsOldDemandFromResumingPrefetch() throws Exception {
        assertDemandPrefetchInvalidation(true);
    }

    private void assertDemandPrefetchInvalidation(boolean close) throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        LinkedBlockingQueue<Runnable> queued = new LinkedBlockingQueue<>();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 64, queued::add);
        int tileSize = emptySettings().routing().tileSize();
        cache.prefetchArea(0, 0, tileSize * 3 - 1, 0, 0, 0);
        Runnable initialPrefetch = queued.poll(5, TimeUnit.SECONDS);
        assertNotNull(initialPrefetch);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<HydrologyTile> result = caller.submit(() -> cache.tiles(List.of(new HydrologyTileKey(1, 0))).getFirst());
            Runnable oldDemand = queued.poll(5, TimeUnit.SECONDS);
            assertNotNull(oldDemand);
            initialPrefetch.run();
            assertTrue(queued.isEmpty());
            if (close) {
                cache.close();
            } else {
                cache.clear();
            }
            cache.prefetchArea(tileSize * 10, 0, tileSize * 10, 0, tileSize * 10, 0);
            if (!close) {
                Runnable freshPrefetch = queued.poll(5, TimeUnit.SECONDS);
                assertNotNull(freshPrefetch);
                freshPrefetch.run();
            }
            oldDemand.run();
            if (close) {
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> result.get(5, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof CancellationException);
                verify(planner, never()).plan(new HydrologyTileKey(1, 0));
            } else {
                assertSame(tile, result.get(5, TimeUnit.SECONDS));
            }
            assertTrue(queued.isEmpty());
            assertEquals(close ? 0 : 1, cache.size());
            verify(planner, never()).plan(new HydrologyTileKey(2, 0));
        } finally {
            caller.shutdownNow();
        }
    }
    @Test
    public void plannedCheckNeverPlansItselfButAsksThePrefetchExecutorForMissingTiles() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        when(tile.columnAt(anyInt(), anyInt())).thenReturn(Optional.empty());
        List<Runnable> queued = new ArrayList<>();
        HydrologyTileCache cache = new HydrologyTileCache(planner, 64, queued::add);

        assertFalse(cache.isPlanned(40, 40));
        assertFalse(queued.isEmpty());
        verify(planner, org.mockito.Mockito.never()).plan(any(HydrologyTileKey.class));

        for (Runnable task : List.copyOf(queued)) {
            task.run();
        }

        assertTrue(cache.isPlanned(40, 40));
        assertTrue(cache.columnAt(40, 40).isEmpty());
    }

    @Test
    public void plannedCheckWithoutAnExecutorReportsTheCacheOnly() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        when(tile.columnAt(anyInt(), anyInt())).thenReturn(Optional.empty());
        HydrologyTileCache cache = new HydrologyTileCache(planner, 64);

        assertFalse(cache.isPlanned(40, 40));
        cache.columnAt(40, 40);
        assertTrue(cache.isPlanned(40, 40));
    }

    @Test
    public void aThreadThatMayNotWaitSamplesRiverlesslyAndAsksThePrefetchExecutorInstead() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        when(tile.columnAt(anyInt(), anyInt())).thenReturn(Optional.empty());
        List<Runnable> queued = new ArrayList<>();
        AtomicBoolean forbidden = new AtomicBoolean(true);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 64, queued::add, forbidden::get);

        assertTrue(cache.columnAt(40, 40).isEmpty());
        verify(planner, org.mockito.Mockito.never()).plan(any(HydrologyTileKey.class));
        assertFalse(queued.isEmpty());

        for (Runnable task : List.copyOf(queued)) {
            task.run();
        }

        // Once the plan lands the same thread composes the real columns instead of the empty answer.
        assertTrue(cache.isPlanned(40, 40));
        assertTrue(cache.columnAt(40, 40).isEmpty());
        verify(tile, org.mockito.Mockito.atLeastOnce()).columnAt(anyInt(), anyInt());
    }

    @Test
    public void aThreadThatMayWaitStillPlansTheTilesItNeeds() {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyTile tile = mock(HydrologyTile.class);
        when(planner.settings()).thenReturn(emptySettings());
        when(planner.plan(any(HydrologyTileKey.class))).thenReturn(tile);
        when(tile.columnAt(anyInt(), anyInt())).thenReturn(Optional.empty());
        AtomicBoolean forbidden = new AtomicBoolean(false);
        HydrologyTileCache cache = new HydrologyTileCache(planner, 64, null, forbidden::get);

        assertTrue(cache.columnAt(40, 40).isEmpty());
        verify(planner, org.mockito.Mockito.atLeastOnce()).plan(any(HydrologyTileKey.class));
        assertTrue(cache.isPlanned(40, 40));
    }

}
