package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class HydrologyPlanCacheTest {
    private static final long SEED = 1337L;
    private static final long TIMEOUT_SECONDS = 120L;
    private static final WindowKey KEY = new WindowKey(2, 3);
    private static final WindowKey LAND = new WindowKey(4, 4);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<ExecutorService> pools = new ArrayList<>();
    private final List<HydrologyPlanCache> caches = new ArrayList<>();

    @After
    public void shutDown() {
        for (HydrologyPlanCache cache : caches) {
            cache.close();
        }
        for (ExecutorService pool : pools) {
            pool.shutdownNow();
        }
    }

    @Test
    public void concurrentRequestsForSameWindowPlanOnce() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        planners.hook = key -> {
            if (key instanceof WindowKey) {
                gate.pass();
            }
        };
        HydrologyPlanCache cache = cache(planners, pool(4), () -> false);
        int requesters = 12;
        IndexedWindow[] results = new IndexedWindow[requesters];
        Throwable[] failures = new Throwable[requesters];
        List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < requesters; index++) {
            int slot = index;
            threads.add(new Thread(() -> {
                try {
                    results[slot] = cache.window(KEY);
                } catch (Throwable failure) {
                    failures[slot] = failure;
                }
            }, "requester-" + index));
        }
        AtomicReference<Throwable> renderFailure = new AtomicReference<>();
        AtomicBoolean renderInterrupted = new AtomicBoolean();
        Thread render = new Thread(() -> {
            try {
                cache.window(KEY);
            } catch (Throwable failure) {
                renderFailure.set(failure);
                renderInterrupted.set(Thread.currentThread().isInterrupted());
            }
        }, "render");
        threads.add(render);
        for (Thread thread : threads) {
            thread.start();
        }
        await(() -> gate.arrivals() >= 1, "window planning started");
        await(() -> allWaiting(threads), "every requester waits on the plan");
        assertEquals("one plan runs for every requester", 1, gate.arrivals());
        render.interrupt();
        render.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertTrue("the interrupted render thread gave up: " + renderFailure.get(), renderFailure.get() instanceof CancellationException);
        assertTrue("the render thread keeps its interrupt", renderInterrupted.get());
        gate.release();
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }
        for (int index = 0; index < requesters; index++) {
            assertNull("requester " + index, failures[index]);
            assertSame("requester " + index + " shares the one plan", results[0], results[index]);
        }
        assertEquals(1, planners.windowCalls(KEY));
        List<RegionKey> regions = WindowPlanner.lakeRegions(KEY);
        assertEquals(regions.size(), planners.regionCalls.size());
        for (RegionKey region : regions) {
            assertEquals("region " + region, 1, planners.regionCalls(region));
        }
        assertSame(results[0], cache.windowIfReady(KEY).orElseThrow());
        assertSame(results[0], cache.window(KEY));
        assertEquals(1, planners.windowCalls(KEY));
        assertEquals(KEY, results[0].plan().key());
    }

    @Test
    public void interruptedPlanIsNotCached() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        WindowKey swallowed = new WindowKey(-5, 1);
        Gate gate = new Gate();
        AtomicReference<Thread> planning = new AtomicReference<>();
        AtomicBoolean firstThrowing = new AtomicBoolean(true);
        AtomicBoolean firstSwallowing = new AtomicBoolean(true);
        planners.hook = key -> {
            if (KEY.equals(key) && firstThrowing.compareAndSet(true, false)) {
                planning.set(Thread.currentThread());
                gate.pass();
            }
            if (swallowed.equals(key) && firstSwallowing.compareAndSet(true, false)) {
                Thread.currentThread().interrupt();
            }
        };
        HydrologyPlanCache cache = cache(planners, pool(4), () -> false);
        ExecutorService requester = pool(1);
        Future<Throwable> outcome = requester.submit(() -> failureOf(() -> cache.window(KEY)));
        await(() -> planning.get() != null && gate.arrivals() == 1, "window planning blocked");
        planning.get().interrupt();
        Throwable failure = outcome.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("interruption reaches the caller: " + failure, failure instanceof CancellationException);
        assertFalse(cache.windowIfReady(KEY).isPresent());
        IndexedWindow retried = cache.window(KEY);
        assertEquals("the interrupted plan was redone", 2, planners.windowCalls(KEY));
        assertSame(retried, cache.windowIfReady(KEY).orElseThrow());
        for (RegionKey region : WindowPlanner.lakeRegions(KEY)) {
            assertEquals("completed region plans stay cached", 1, planners.regionCalls(region));
        }

        Throwable partial = failureOf(() -> cache.window(swallowed));
        assertTrue("a plan finished on an interrupted thread is discarded: " + partial, partial instanceof CancellationException);
        assertFalse(cache.windowIfReady(swallowed).isPresent());
        IndexedWindow redone = cache.window(swallowed);
        assertEquals(2, planners.windowCalls(swallowed));
        assertSame(redone, cache.windowIfReady(swallowed).orElseThrow());
    }

    @Test
    public void mainThreadNeverWaits() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        planners.hook = key -> {
            if (key instanceof WindowKey) {
                gate.pass();
            }
        };
        Thread main = Thread.currentThread();
        HydrologyPlanCache cache = cache(planners, pool(4), () -> Thread.currentThread() == main);
        assertFalse(cache.windowIfReady(KEY).isPresent());
        assertMainThreadRefused(cache);
        assertEquals("the main thread never plans", 0, planners.totalCalls());

        Future<IndexedWindow> worker = pool(1).submit(() -> cache.window(KEY));
        await(() -> gate.arrivals() == 1, "worker plan in flight");
        assertFalse("an in-flight plan is not ready", cache.windowIfReady(KEY).isPresent());
        assertMainThreadRefused(cache);
        gate.release();
        IndexedWindow planned = worker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertSame(planned, cache.windowIfReady(KEY).orElseThrow());
        assertMainThreadRefused(cache);
        assertEquals(1, planners.windowCalls(KEY));
    }

    @Test
    public void prepareWindowsAroundReturnsImmediately() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        planners.hook = key -> {
            if (key instanceof RegionKey) {
                gate.pass();
            }
        };
        Thread main = Thread.currentThread();
        HydrologyPlanCache cache = cache(planners, pool(1), () -> Thread.currentThread() == main);
        CompletableFuture<Void> prepared = cache.prepareWindowsAround(0, 0, 31, 31, 1);
        assertFalse(prepared.isDone());
        List<WindowKey> expected = new ArrayList<>();
        for (int wz = -1; wz <= 1; wz++) {
            for (int wx = -1; wx <= 1; wx++) {
                expected.add(new WindowKey(wx, wz));
                assertFalse(cache.windowIfReady(new WindowKey(wx, wz)).isPresent());
            }
        }
        await(() -> gate.arrivals() == 1, "speculative planning started");
        assertFalse(prepared.isDone());
        gate.release();
        prepared.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        for (WindowKey key : expected) {
            assertTrue("window " + key + " is ready", cache.windowIfReady(key).isPresent());
            assertEquals("window " + key, 1, planners.windowCalls(key));
        }
        assertEquals(new HashSet<>(expected), planners.windowCalls.keySet());
        for (AtomicInteger calls : planners.regionCalls.values()) {
            assertEquals(1, calls.get());
        }
        List<WindowKey> order = planners.windowOrder();
        assertEquals(9, order.size());
        assertEquals("the requested window is planned first", new WindowKey(0, 0), order.get(0));
        assertEquals("edge neighbours before corners", Set.of(new WindowKey(1, 0), new WindowKey(-1, 0), new WindowKey(0, 1),
                new WindowKey(0, -1)), new HashSet<>(order.subList(1, 5)));
    }

    @Test
    public void plannerFailurePropagatesAndIsRetriedOnNextRequest() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        WindowKey regionFailure = new WindowKey(30, -30);
        RegionKey failingRegion = WindowPlanner.lakeRegions(regionFailure).get(1);
        AtomicInteger windowFailures = new AtomicInteger(1);
        AtomicInteger regionFailures = new AtomicInteger(1);
        planners.hook = key -> {
            if (KEY.equals(key) && windowFailures.getAndDecrement() > 0) {
                throw new IllegalStateException("synthetic window failure");
            }
            if (failingRegion.equals(key) && regionFailures.getAndDecrement() > 0) {
                throw new IllegalArgumentException("synthetic region failure");
            }
        };
        HydrologyPlanCache cache = cache(planners, pool(1), () -> false);
        Throwable failure = failureOf(() -> cache.window(KEY));
        assertTrue("" + failure, failure instanceof IllegalStateException);
        assertEquals("synthetic window failure", failure.getMessage());
        assertFalse(cache.windowIfReady(KEY).isPresent());
        IndexedWindow retried = cache.window(KEY);
        assertEquals(KEY, retried.plan().key());
        assertEquals(2, planners.windowCalls(KEY));
        for (RegionKey region : WindowPlanner.lakeRegions(KEY)) {
            assertEquals(1, planners.regionCalls(region));
        }

        Throwable regional = failureOf(() -> cache.window(regionFailure));
        assertTrue("" + regional, regional instanceof IllegalArgumentException);
        assertEquals("synthetic region failure", regional.getMessage());
        assertEquals("a failed region never reaches the window planner", 0, planners.windowCalls(regionFailure));
        assertEquals(regionFailure, cache.window(regionFailure).plan().key());
        assertEquals(2, planners.regionCalls(failingRegion));
        assertEquals(1, planners.windowCalls(regionFailure));
        for (RegionKey region : WindowPlanner.lakeRegions(regionFailure)) {
            if (!region.equals(failingRegion)) {
                assertEquals("region " + region, 1, planners.regionCalls(region));
            }
        }
    }

    @Test
    public void demandPlansAheadOfSpeculativeWindows() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        AtomicBoolean first = new AtomicBoolean(true);
        planners.hook = key -> {
            if (first.compareAndSet(true, false)) {
                gate.pass();
            }
        };
        HydrologyPlanCache cache = cache(planners, pool(1), () -> false);
        CompletableFuture<Void> prepared = cache.prepareWindowsAround(0, 0, 31, 31, 2);
        await(() -> gate.arrivals() == 1, "speculative planning holds the only pool thread");
        WindowKey far = new WindowKey(40, 40);
        AtomicReference<IndexedWindow> demanded = new AtomicReference<>();
        Thread requester = new Thread(() -> demanded.set(cache.window(far)), "demand");
        requester.start();
        await(() -> requester.getState() == Thread.State.WAITING, "demand request queued");
        gate.release();
        requester.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertEquals(far, demanded.get().plan().key());
        prepared.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<Object> order = planners.order();
        int farIndex = order.indexOf(far);
        for (RegionKey region : WindowPlanner.lakeRegions(far)) {
            assertTrue("region " + region + " before its window", order.indexOf(region) < farIndex);
        }
        assertEquals("the demanded window and its regions come right after the running job", 1 + WindowPlanner.lakeRegions(far).size(),
                farIndex);
        assertEquals(26, planners.windowCalls.size());
    }

    @Test
    public void speculativePlanningLeavesWorkersForNewAndPromotedDemand() throws Exception {
        for (int threads : List.of(2, 4)) {
            FakePlanners planners = new FakePlanners(unpersisted());
            Gate gate = new Gate();
            planners.hook = key -> {
                if (key instanceof RegionKey region && Math.abs(region.rx()) < 10 && Math.abs(region.rz()) < 10) {
                    gate.pass();
                }
            };
            ExecutorService workers = pool(threads);
            HydrologyPlanCache cache = cache(planners, workers, () -> false);
            CompletableFuture<Void> prepared = cache.prepareWindowsAround(0, 0, 31, 31, 2);
            try {
                await(() -> gate.arrivals() >= threads / 2, "speculative planning reaches its concurrency limit");
                WindowKey demanded = new WindowKey(400, 400);
                assertEquals(demanded, cache.demandWindow(demanded).get(5L, TimeUnit.SECONDS).plan().key());
                assertEquals("prefetch leaves workers available for demand", threads / 2, gate.arrivals());

                WindowKey promoted = new WindowKey(800, 800);
                CompletableFuture<IndexedWindow> prefetched = cache.prefetchWindow(promoted);
                workers.submit(() -> null).get(5L, TimeUnit.SECONDS);
                assertFalse("the prefetch waits for speculative capacity", prefetched.isDone());
                IndexedWindow ready = cache.demandWindow(promoted).get(5L, TimeUnit.SECONDS);
                assertEquals(promoted, ready.plan().key());
                assertSame("demand promotes the queued prefetch", ready, prefetched.get(5L, TimeUnit.SECONDS));
                assertEquals(threads / 2, gate.arrivals());
            } finally {
                gate.release();
            }
            prepared.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertEquals("all prepared windows finish after demand", 27, planners.windowCalls.size());
        }
    }

    @Test
    public void plansWithRealPlannersAndReloadsFromStore() throws Exception {
        HydrologySettings settings = settings(new IrisHydrology());
        SyntheticTerrain terrain = SyntheticTerrain.fractal(SEED);
        HydrologyPlanStore store = new HydrologyPlanStore(folder.getRoot().toPath(), "scope", 2, settings);
        HydrologyPlanCache cache = HydrologyPlanCache.create(settings, regionPlanner(settings, terrain), windowPlanner(settings, terrain),
                pool(4), () -> false);
        cache.attachStore(store);
        caches.add(cache);
        IndexedWindow window = cache.window(LAND);

        SyntheticTerrain direct = SyntheticTerrain.fractal(SEED);
        RegionPlanner regionPlanner = regionPlanner(settings, direct);
        List<RegionPlan> regions = new ArrayList<>();
        for (RegionKey region : WindowPlanner.lakeRegions(LAND)) {
            regions.add(regionPlanner.plan(region));
        }
        WindowPlan expected = windowPlanner(settings, direct).plan(LAND, regions);
        assertFalse(expected.reaches().isEmpty());
        assertEquals(expected, window.plan());
        assertEquals(expected, store.readWindow(LAND).orElseThrow());
        for (RegionPlan region : regions) {
            assertEquals(region, store.readRegion(region.key()).orElseThrow());
        }
        List<Reach> candidates = new ArrayList<>();
        Reach owned = expected.reaches().get(0);
        int last = owned.xs().length - 1;
        window.index().candidates(Math.floorDiv((int) Math.floor(owned.xs()[last]), 16), Math.floorDiv((int) Math.floor(owned.zs()[last]), 16),
                0, candidates, new ArrayList<>());
        assertTrue("the index finds an owned reach at its end", candidates.contains(owned));
        cache.close();

        SyntheticTerrain cold = SyntheticTerrain.fractal(SEED);
        HydrologyPlanCache reopened = HydrologyPlanCache.create(settings, regionPlanner(settings, cold), windowPlanner(settings, cold),
                pool(4), () -> false);
        reopened.attachStore(new HydrologyPlanStore(folder.getRoot().toPath(), "scope", 2, settings));
        caches.add(reopened);
        assertEquals(expected, reopened.window(LAND).plan());
        assertEquals("a stored window plans nothing", 0L, cold.baseSamples() + cold.naturalSamples());
    }

    @Test
    public void regionAccessDedupesAndGuardsMainThread() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        RegionKey key = new RegionKey(3, -2);
        Gate gate = new Gate();
        planners.hook = planned -> {
            if (key.equals(planned)) {
                gate.pass();
            }
        };
        Thread main = Thread.currentThread();
        HydrologyPlanCache cache = cache(planners, pool(4), () -> Thread.currentThread() == main);
        assertFalse(cache.regionIfReady(key).isPresent());
        Throwable refused = failureOf(() -> cache.region(key));
        assertTrue("" + refused, refused instanceof IllegalStateException && refused.getMessage().contains("main thread"));
        assertEquals("the main thread never plans", 0, planners.totalCalls());

        int requesters = 6;
        RegionPlan[] results = new RegionPlan[requesters];
        List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < requesters; index++) {
            int slot = index;
            threads.add(new Thread(() -> results[slot] = cache.region(key), "region-requester-" + index));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        await(() -> gate.arrivals() >= 1, "region planning started");
        await(() -> allWaiting(threads), "every requester waits on the region");
        assertFalse("an in-flight region is not ready", cache.regionIfReady(key).isPresent());
        gate.release();
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }
        for (int index = 0; index < requesters; index++) {
            assertSame("requester " + index + " shares the one plan", results[0], results[index]);
        }
        assertEquals(key, results[0].key());
        assertEquals(1, planners.regionCalls(key));
        assertSame(results[0], cache.regionIfReady(key).orElseThrow());

        WindowKey inside = new WindowKey(28, -12);
        assertTrue(WindowPlanner.lakeRegions(inside).contains(key));
        ExecutorService worker = pool(1);
        worker.submit(() -> cache.window(inside)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals("the window reuses the cached region", 1, planners.regionCalls(key));
        for (RegionKey region : WindowPlanner.lakeRegions(inside)) {
            RegionPlan ready = cache.regionIfReady(region).orElseThrow();
            assertSame(ready, worker.submit(() -> cache.region(region)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, planners.regionCalls(region));
        }
    }

    @Test
    public void leastRecentlyUsedWindowIsEvicted() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        HydrologyPlanCache cache = cache(planners, pool(4), () -> false);
        WindowKey kept = new WindowKey(0, 0);
        WindowKey stale = new WindowKey(1, 0);
        IndexedWindow keptWindow = cache.window(kept);
        cache.window(stale);
        assertEquals(0L, cache.windowEvictions());
        List<WindowKey> requested = new ArrayList<>(List.of(kept, stale));
        for (int index = 0; index < 300; index++) {
            WindowKey key = new WindowKey(index, 7);
            requested.add(key);
            cache.window(key);
            assertSame("a window touched after every plan stays", keptWindow, cache.window(kept));
        }
        assertFalse("the least recently used window left the cache", cache.windowIfReady(stale).isPresent());
        assertTrue(cache.windowIfReady(new WindowKey(299, 7)).isPresent());
        int ready = 0;
        for (WindowKey key : requested) {
            if (cache.windowIfReady(key).isPresent()) {
                ready++;
            }
        }
        assertTrue("the cache stays bounded: " + ready, ready < requested.size() / 2);
        await(() -> cache.windowEvictions() == requested.size() - 128, "all excess windows are evicted");
        long evictions = cache.windowEvictions();
        assertSame(keptWindow, cache.window(kept));
        assertEquals("a resident cache hit does not evict a window", evictions, cache.windowEvictions());
        cache.window(stale);
        assertEquals("an evicted window is planned again on demand", 2, planners.windowCalls(stale));
        await(() -> cache.windowEvictions() == evictions + 1, "replanning an evicted window advances the eviction count");
    }

    @Test
    public void newerPrefetchRunsBeforeOlderPrefetchAndDemandBeforeBoth() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        AtomicBoolean first = new AtomicBoolean(true);
        planners.hook = key -> {
            if (first.compareAndSet(true, false)) {
                gate.pass();
            }
        };
        HydrologyPlanCache cache = cache(planners, pool(1), () -> false);
        CompletableFuture<Void> older = cache.prepareWindowsAround(0, 0, 31, 31, 1);
        await(() -> gate.arrivals() == 1, "the older prefetch holds the only pool thread");
        WindowKey newer = new WindowKey(40, 40);
        int chunks = WindowKey.SIZE >> 4;
        CompletableFuture<Void> newerPrepared = cache.prepareWindowsAround(newer.wx() * chunks, newer.wz() * chunks,
                newer.wx() * chunks + chunks - 1, newer.wz() * chunks + chunks - 1, 0);
        WindowKey demanded = new WindowKey(-40, 40);
        AtomicReference<IndexedWindow> demandedWindow = new AtomicReference<>();
        Thread requester = new Thread(() -> demandedWindow.set(cache.window(demanded)), "demand");
        requester.start();
        await(() -> requester.getState() == Thread.State.WAITING, "demand request queued");
        gate.release();
        requester.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertEquals(demanded, demandedWindow.get().plan().key());
        older.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        newerPrepared.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<Object> order = planners.order();
        int demandedIndex = order.indexOf(demanded);
        int newerIndex = order.indexOf(newer);
        assertEquals("demand runs right after the running job", 1 + WindowPlanner.lakeRegions(demanded).size(), demandedIndex);
        assertEquals("the newer prefetch runs next", demandedIndex + 1 + WindowPlanner.lakeRegions(newer).size(), newerIndex);
        for (int wz = -1; wz <= 1; wz++) {
            for (int wx = -1; wx <= 1; wx++) {
                assertTrue("older window " + wx + "," + wz + " after the newer prefetch", order.indexOf(new WindowKey(wx, wz)) > newerIndex);
            }
        }
    }

    @Test
    public void cancellingAnEpochWithdrawsItsQueuedWindowsAndKeepsSharedOnes() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        AtomicBoolean first = new AtomicBoolean(true);
        planners.hook = key -> {
            if (first.compareAndSet(true, false)) {
                gate.pass();
            }
        };
        HydrologyPlanCache cache = cache(planners, pool(1), () -> false);
        int chunks = WindowKey.SIZE >> 4;
        Object epoch = new Object();
        CompletableFuture<Void> running = cache.prepareWindowsAround(0, 0, chunks - 1, chunks - 1, 0);
        await(() -> gate.arrivals() == 1, "an unrelated prefetch holds the only pool thread");
        CompletableFuture<Void> pregen = cache.prepareWindowsAround(10 * chunks, 10 * chunks, 12 * chunks - 1, 11 * chunks - 1, 0, epoch);
        WindowKey shared = new WindowKey(11, 10);
        CompletableFuture<IndexedWindow> demanded = cache.demandWindow(shared);

        cache.cancel(epoch);
        gate.release();

        assertEquals(shared, demanded.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).plan().key());
        running.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ExecutionException withdrawn = assertThrows(ExecutionException.class, () -> pregen.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(withdrawn.getCause() instanceof CancellationException);
        await(() -> planners.windowCalls(shared) == 1, "the shared window is planned");
        assertEquals("a withdrawn window is never planned", 0, planners.windowCalls(new WindowKey(10, 10)));
        assertTrue(cache.windowIfReady(new WindowKey(10, 10)).isEmpty());
        cache.prepareWindowsAround(10 * chunks, 10 * chunks, 10 * chunks, 10 * chunks, 0, new Object()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals("a withdrawn window can be prepared again", 1, planners.windowCalls(new WindowKey(10, 10)));
    }

    @Test
    public void cancellingAnEpochLeavesWindowsAnotherEpochOrDemandStillWants() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        AtomicBoolean first = new AtomicBoolean(true);
        planners.hook = key -> {
            if (first.compareAndSet(true, false)) {
                gate.pass();
            }
        };
        HydrologyPlanCache cache = cache(planners, pool(1), () -> false);
        int chunks = WindowKey.SIZE >> 4;
        cache.prepareWindowsAround(0, 0, chunks - 1, chunks - 1, 0);
        await(() -> gate.arrivals() == 1, "an unrelated prefetch holds the only pool thread");
        Object stopped = new Object();
        Object running = new Object();
        cache.prepareWindowsAround(20 * chunks, 20 * chunks, 20 * chunks, 20 * chunks, 0, stopped);
        CompletableFuture<Void> kept = cache.prepareWindowsAround(20 * chunks, 20 * chunks, 20 * chunks, 20 * chunks, 0, running);

        cache.cancel(stopped);
        gate.release();

        kept.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(1, planners.windowCalls(new WindowKey(20, 20)));
    }

    @Test
    public void speculativeTasksRunBehindDemandPlans() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        AtomicBoolean first = new AtomicBoolean(true);
        planners.hook = key -> {
            if (first.compareAndSet(true, false)) {
                gate.pass();
            }
        };
        HydrologyPlanCache cache = cache(planners, pool(1), () -> false);
        int chunks = WindowKey.SIZE >> 4;
        cache.prepareWindowsAround(0, 0, chunks - 1, chunks - 1, 0);
        await(() -> gate.arrivals() == 1, "an unrelated prefetch holds the only pool thread");
        CountDownLatch ran = new CountDownLatch(1);
        AtomicInteger dropped = new AtomicInteger();
        cache.runSpeculative(() -> {
            planners.order.add("speculative");
            ran.countDown();
        }, dropped::incrementAndGet);
        WindowKey demanded = new WindowKey(-30, 30);
        CompletableFuture<IndexedWindow> plan = cache.demandWindow(demanded);

        gate.release();

        plan.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(ran.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        List<Object> order = planners.order();
        assertTrue("demand planning finishes before the speculative task", order.indexOf(demanded) < order.indexOf("speculative"));
        assertEquals(0, dropped.get());
    }

    @Test
    public void droppedSpeculativeTasksAlwaysRunTheirDropCallback() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        AtomicBoolean first = new AtomicBoolean(true);
        planners.hook = key -> {
            if (first.compareAndSet(true, false)) {
                gate.pass();
            }
        };
        HydrologyPlanCache cache = cache(planners, pool(1), () -> false);
        int chunks = WindowKey.SIZE >> 4;
        cache.prepareWindowsAround(0, 0, chunks - 1, chunks - 1, 0);
        await(() -> gate.arrivals() == 1, "an unrelated prefetch holds the only pool thread");
        AtomicInteger ran = new AtomicInteger();
        AtomicInteger dropped = new AtomicInteger();

        cache.runSpeculative(ran::incrementAndGet, dropped::incrementAndGet);
        cache.close();
        cache.runSpeculative(ran::incrementAndGet, dropped::incrementAndGet);
        gate.release();
        ExecutorService rejecting = Executors.newSingleThreadExecutor();
        rejecting.shutdown();
        HydrologyPlanCache refused = cache(planners, rejecting, () -> false);
        refused.runSpeculative(ran::incrementAndGet, dropped::incrementAndGet);

        assertEquals("a job queued at close, one sent after it and one the pool refused are all dropped", 3, dropped.get());
        assertEquals(0, ran.get());
    }

    @Test
    public void demandMeetingAWithdrawnWindowPlansAFreshOne() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        AtomicBoolean first = new AtomicBoolean(true);
        planners.hook = key -> {
            if (first.compareAndSet(true, false)) {
                gate.pass();
            }
        };
        HydrologyPlanCache cache = cache(planners, pool(1), () -> false);
        int chunks = WindowKey.SIZE >> 4;
        cache.prepareWindowsAround(0, 0, chunks - 1, chunks - 1, 0);
        await(() -> gate.arrivals() == 1, "an unrelated prefetch holds the only pool thread");
        Object epoch = new Object();
        WindowKey east = new WindowKey(40, 0);
        WindowKey west = new WindowKey(-40, 0);
        CompletableFuture<Void> eastPrepared = cache.prepareWindowsAround(40 * chunks, 0, 40 * chunks, 0, 0, epoch);
        CompletableFuture<Void> westPrepared = cache.prepareWindowsAround(-40 * chunks, 0, -40 * chunks, 0, 0, epoch);
        AtomicReference<WindowKey> failedFirst = new AtomicReference<>();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        eastPrepared.whenComplete((ignored, failure) -> pauseFirst(failedFirst, east, paused, resume));
        westPrepared.whenComplete((ignored, failure) -> pauseFirst(failedFirst, west, paused, resume));
        ExecutorService canceller = pool(1);

        Future<?> cancelling = canceller.submit(() -> cache.cancel(epoch));
        assertTrue(paused.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        WindowKey pending = failedFirst.get().equals(east) ? west : east;
        CompletableFuture<IndexedWindow> demanded = cache.demandWindow(pending);
        resume.countDown();
        cancelling.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        gate.release();

        assertEquals(pending, demanded.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).plan().key());
        assertTrue(eastPrepared.isCompletedExceptionally());
        assertTrue(westPrepared.isCompletedExceptionally());
        assertEquals(1, planners.windowCalls(pending));
        assertEquals(0, planners.windowCalls(failedFirst.get()));
    }

    @Test(timeout = 180_000L)
    public void demandsRacingAnEpochCancelRetryPastWithdrawnWindows() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        ExecutorService pool = pool(1);
        CountDownLatch blocker = new CountDownLatch(1);
        pool.execute(() -> {
            try {
                blocker.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
            }
        });
        HydrologyPlanCache cache = cache(planners, pool, () -> false);
        int chunks = WindowKey.SIZE >> 4;
        int side = 128;
        ExecutorService demander = pool(1);
        for (int round = 0; round < 8; round++) {
            int originX = round * 4 * side;
            Object epoch = new Object();
            cache.prepareWindowsAround(originX * chunks, 0, (originX + side) * chunks - 1, side * chunks - 1, 0, epoch);
            List<WindowKey> keys = new ArrayList<>(side * side);
            for (int wz = 0; wz < side; wz++) {
                for (int wx = originX; wx < originX + side; wx++) {
                    keys.add(new WindowKey(wx, wz));
                }
            }
            Collections.shuffle(keys, new Random(round));
            CountDownLatch started = new CountDownLatch(1);
            List<CompletableFuture<IndexedWindow>> demanded = Collections.synchronizedList(new ArrayList<>());
            Future<?> demanding = demander.submit(() -> {
                for (WindowKey key : keys) {
                    demanded.add(cache.demandWindow(key));
                    if (demanded.size() == side) {
                        started.countDown();
                    }
                }
            });
            assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            cache.cancel(epoch);
            demanding.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertEquals(keys.size(), demanded.size());
            for (int index = 0; index < demanded.size(); index++) {
                assertFalse("demand " + index + " of round " + round + " joined a withdrawn window",
                        demanded.get(index).isCompletedExceptionally());
            }
        }
    }

    @Test(timeout = 60_000L)
    public void prepareRejectsAreasAndRingsThatWouldOverflowTheWindowCount() {
        HydrologyPlanCache cache = cache(new FakePlanners(unpersisted()), pool(1), () -> false);
        assertThrows(IllegalArgumentException.class, () -> cache.prepareWindowsAround(0, 0, 0, 0, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> cache.prepareWindowsAround(0, 0, 0, 0, 1 << 30, new Object()));
        assertThrows(IllegalArgumentException.class,
                () -> cache.prepareWindowsAround(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> cache.prepareWindowsAround(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE));
        assertFalse(cache.prepareWindowsAround(0, 0, 0, 0, 1).isCompletedExceptionally());
    }

    @Test
    public void windowDemandedWhileItsRegionsAreRequestedKeepsThemThroughCancel() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        PausingPool pausing = new PausingPool(pool(1));
        HydrologyPlanCache cache = cache(planners, pausing, () -> false);
        int chunks = WindowKey.SIZE >> 4;
        Object epoch = new Object();
        WindowKey window = new WindowKey(12, -7);
        ExecutorService preparer = pool(1);

        pausing.pauseNext();
        Future<CompletableFuture<Void>> preparing = preparer.submit(
                () -> cache.prepareWindowsAround(12 * chunks, -7 * chunks, 12 * chunks, -7 * chunks, 0, epoch));
        assertTrue(pausing.paused.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        CompletableFuture<IndexedWindow> demanded = cache.demandWindow(window);
        cache.cancel(epoch);
        pausing.resume.countDown();
        CompletableFuture<Void> prepared = preparing.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(window, demanded.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).plan().key());
        prepared.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(1, planners.windowCalls(window));
        for (RegionKey region : WindowPlanner.lakeRegions(window)) {
            assertEquals(1, planners.regionCalls(region));
        }
    }

    @Test
    public void previewReadsAndRequestsNeverEvictGenerationWindows() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        HydrologyPlanCache cache = cache(planners, pool(4), () -> false);
        List<WindowKey> generation = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            WindowKey key = new WindowKey(index, 3);
            generation.add(key);
            cache.window(key);
        }

        for (int index = 0; index < 20; index++) {
            cache.previewWindow(new WindowKey(index, -9)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        for (WindowKey key : generation) {
            assertTrue("generation window " + key + " survives the previews", cache.windowIfReady(key).isPresent());
        }
        assertTrue(cache.windowIfReady(generation.getFirst()).isPresent());
        cache.window(new WindowKey(500, 3));
        await(() -> readyCount(cache, generation) == generation.size() - 1, "the new window evicts one generation window");

        assertFalse("a preview read does not refresh the oldest window", cache.windowIfReady(generation.getFirst()).isPresent());
        assertTrue(cache.windowIfReady(generation.get(1)).isPresent());
        for (WindowKey key : generation) {
            assertEquals(1, planners.windowCalls(key));
        }
    }

    @Test
    public void closeCancelsWaitersAndRefusesWork() throws Exception {
        FakePlanners planners = new FakePlanners(unpersisted());
        Gate gate = new Gate();
        planners.hook = key -> {
            if (key instanceof WindowKey) {
                gate.pass();
            }
        };
        ExecutorService planning = pool(1);
        HydrologyPlanCache cache = cache(planners, planning, () -> false);
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        Thread waiter = new Thread(() -> waiterFailure.set(failureOf(() -> cache.window(KEY))), "waiter");
        waiter.start();
        await(() -> gate.arrivals() == 1 && waiter.getState() == Thread.State.WAITING, "a waiter on a window being planned");
        CompletableFuture<Void> pending = cache.prepareWindowsAround(-1600, -1600, -1569, -1569, 1);
        assertFalse(pending.isDone());

        cache.close();
        waiter.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertTrue("an in-flight waiter is cancelled: " + waiterFailure.get(), waiterFailure.get() instanceof CancellationException);
        assertTrue("queued prefetch is cancelled", pending.isCompletedExceptionally());
        CompletableFuture<Void> afterClose = cache.prepareWindowsAround(0, 0, 31, 31, 1);
        assertTrue(afterClose.isDone() && !afterClose.isCompletedExceptionally());
        assertTrue(failureOf(() -> cache.window(KEY)) instanceof CancellationException);
        assertTrue(failureOf(() -> cache.region(new RegionKey(0, 0))) instanceof CancellationException);

        gate.release();
        planning.submit(() -> {
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertFalse("a plan finishing after close is not kept", cache.windowIfReady(KEY).isPresent());
        assertEquals(1, planners.windowCalls(KEY));
        assertEquals("queued work never runs after close", 1, planners.windowCalls.size());
    }

    private static int readyCount(HydrologyPlanCache cache, List<WindowKey> keys) {
        int ready = 0;
        for (WindowKey key : keys) {
            if (cache.windowIfReady(key).isPresent()) {
                ready++;
            }
        }
        return ready;
    }

    private static void pauseFirst(AtomicReference<WindowKey> failedFirst, WindowKey key, CountDownLatch paused, CountDownLatch resume) {
        if (!failedFirst.compareAndSet(null, key)) {
            return;
        }
        paused.countDown();
        try {
            if (!resume.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The paused cancel was never resumed.");
            }
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertMainThreadRefused(HydrologyPlanCache cache) {
        try {
            cache.window(KEY);
            fail("window() on the main thread must refuse");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("main thread"));
        }
    }

    private static Throwable failureOf(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static boolean allWaiting(List<Thread> threads) {
        for (Thread thread : threads) {
            Thread.State state = thread.getState();
            if (state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING) {
                return false;
            }
        }
        return true;
    }

    private static void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for: " + description);
            }
            Thread.sleep(10L);
        }
    }

    @Test(timeout = 180_000L)
    public void attachedStoreTakesLaterPlansAndCloseFinishesItsPendingWrites() throws Exception {
        FakePlanners planners = new FakePlanners(settings(new IrisHydrology().setPersist(true)));
        HydrologyPlanCache cache = cache(planners, pool(2), () -> false);
        WindowKey early = new WindowKey(10, 10);
        cache.window(early);
        HydrologyPlanStore store = mock(HydrologyPlanStore.class);
        when(store.readWindow(any())).thenReturn(Optional.empty());
        when(store.readRegion(any())).thenReturn(Optional.empty());
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            writing.countDown();
            assertTrue(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            return null;
        }).when(store).writeWindow(any());
        cache.attachStore(store);
        assertThrows(IllegalStateException.class, () -> cache.attachStore(mock(HydrologyPlanStore.class)));
        cache.demandWindow(KEY);
        assertTrue(writing.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Thread closer = new Thread(cache::close, "plan-cache-closer");
        closer.start();
        await(() -> closer.getState() == Thread.State.WAITING, "close to wait for the pending write");
        assertTrue(closer.isAlive());
        release.countDown();
        closer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertFalse(closer.isAlive());
        verify(store).writeWindow(argThat(plan -> plan.key().equals(KEY)));
        verify(store, never()).writeWindow(argThat(plan -> plan.key().equals(early)));
        HydrologyPlanStore late = mock(HydrologyPlanStore.class);
        cache.attachStore(late);
        cache.attachStore(mock(HydrologyPlanStore.class));
        verifyNoInteractions(late);
    }

    @Test(timeout = 180_000L)
    public void detachedStoreStopsReceivingPlansAndANewStoreTakesOver() throws Exception {
        FakePlanners planners = new FakePlanners(settings(new IrisHydrology().setPersist(true)));
        HydrologyPlanCache cache = cache(planners, pool(2), () -> false);
        HydrologyPlanStore demoted = mock(HydrologyPlanStore.class);
        HydrologyPlanStore promoted = mock(HydrologyPlanStore.class);
        when(demoted.readWindow(any())).thenReturn(Optional.empty());
        when(demoted.readRegion(any())).thenReturn(Optional.empty());
        when(promoted.readWindow(any())).thenReturn(Optional.empty());
        when(promoted.readRegion(any())).thenReturn(Optional.empty());
        WindowKey first = new WindowKey(20, 20);
        WindowKey second = new WindowKey(-20, -20);
        cache.attachStore(demoted);
        cache.window(first);
        cache.detachStore();
        cache.attachStore(promoted);
        cache.window(second);
        verify(demoted).writeWindow(argThat(plan -> plan.key().equals(first)));
        verify(demoted, never()).writeWindow(argThat(plan -> plan.key().equals(second)));
        verify(promoted).writeWindow(argThat(plan -> plan.key().equals(second)));
        verify(promoted, never()).writeWindow(argThat(plan -> plan.key().equals(first)));
    }

    private HydrologyPlanCache cache(FakePlanners planners, ExecutorService pool, BooleanSupplier mainThread) {
        HydrologyPlanCache cache = new HydrologyPlanCache(planners.settings, planners::region, planners::window, pool, mainThread);
        caches.add(cache);
        return cache;
    }

    private ExecutorService pool(int threads) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        pools.add(pool);
        return pool;
    }

    private static HydrologySettings unpersisted() {
        return settings(new IrisHydrology().setPersist(false));
    }

    private static HydrologySettings settings(IrisHydrology config) {
        return HydrologySettings.compile(config, SEED, SyntheticTerrain.SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
    }

    private static RegionPlanner regionPlanner(HydrologySettings settings, SyntheticTerrain terrain) {
        return new RegionPlanner(settings, new HeightLattice(terrain, RegionPlanner.CELL, true, HydrologyPlanCache.REGION_LATTICE_CELLS),
                new HeightLattice(terrain, RegionPlanner.FINE_CELL, false, HydrologyPlanCache.FINE_LATTICE_CELLS), new PondPlanner(settings),
                new ReachShaper(settings));
    }

    private static WindowPlanner windowPlanner(HydrologySettings settings, SyntheticTerrain terrain) {
        return new WindowPlanner(settings, new HeightLattice(terrain, WindowPlanner.CELL, false, HydrologyPlanCache.WINDOW_LATTICE_CELLS),
                new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, HydrologyPlanCache.FINE_LATTICE_CELLS), new PondPlanner(settings),
                new ReachShaper(settings));
    }

    private static final class PausingPool extends AbstractExecutorService {
        private final ExecutorService delegate;
        private final AtomicBoolean armed = new AtomicBoolean();
        private final CountDownLatch paused = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);

        private PausingPool(ExecutorService delegate) {
            this.delegate = delegate;
        }

        void pauseNext() {
            armed.set(true);
        }

        @Override
        public void execute(Runnable command) {
            if (armed.compareAndSet(true, false)) {
                paused.countDown();
                try {
                    if (!resume.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("The paused pool was never resumed.");
                    }
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                }
            }
            delegate.execute(command);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }

    private static final class Gate {
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger arrivals = new AtomicInteger();

        void pass() {
            arrivals.incrementAndGet();
            try {
                if (!release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Gate never opened.");
                }
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Planning interrupted.");
            }
        }

        int arrivals() {
            return arrivals.get();
        }

        void release() {
            release.countDown();
        }
    }

    private static final class FakePlanners {
        private final HydrologySettings settings;
        private final Map<RegionKey, AtomicInteger> regionCalls = new ConcurrentHashMap<>();
        private final Map<WindowKey, AtomicInteger> windowCalls = new ConcurrentHashMap<>();
        private final List<Object> order = Collections.synchronizedList(new ArrayList<>());
        private volatile Consumer<Object> hook = key -> {
        };

        private FakePlanners(HydrologySettings settings) {
            this.settings = settings;
        }

        RegionPlan region(RegionKey key) {
            regionCalls.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
            order.add(key);
            hook.accept(key);
            return new RegionPlan(key, List.of(), List.of());
        }

        WindowPlan window(WindowKey key, List<RegionPlan> plans) {
            windowCalls.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
            order.add(key);
            Set<RegionKey> given = new HashSet<>();
            for (RegionPlan plan : plans) {
                given.add(plan.key());
            }
            List<RegionKey> needed = WindowPlanner.lakeRegions(key);
            if (plans.size() != needed.size() || !given.equals(new HashSet<>(needed))) {
                throw new AssertionError("Window " + key + " got region plans " + given + ", needs " + needed);
            }
            hook.accept(key);
            return new WindowPlan(key, List.of(reach(key)), List.of(), List.of());
        }

        private Reach reach(WindowKey key) {
            HydrologyTier tier = settings.tiers().get(0);
            float x = key.wx() * WindowKey.SIZE + 256f;
            float z = key.wz() * WindowKey.SIZE + 256f;
            return new Reach(key.wx() * 1_000_003L + key.wz(), tier, -1L, new float[]{x - 8f, x}, new float[]{z, z}, new int[]{70, 70},
                    new float[]{1f, 1f}, new float[]{1f, 1f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.RUN}, 1600f);
        }

        int regionCalls(RegionKey key) {
            AtomicInteger calls = regionCalls.get(key);
            return calls == null ? 0 : calls.get();
        }

        int windowCalls(WindowKey key) {
            AtomicInteger calls = windowCalls.get(key);
            return calls == null ? 0 : calls.get();
        }

        int totalCalls() {
            return order.size();
        }

        List<Object> order() {
            synchronized (order) {
                return new ArrayList<>(order);
            }
        }

        List<WindowKey> windowOrder() {
            List<WindowKey> windows = new ArrayList<>();
            for (Object key : order()) {
                if (key instanceof WindowKey window) {
                    windows.add(window);
                }
            }
            return windows;
        }
    }
}
