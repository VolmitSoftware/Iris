package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyPlannerTest {
    private static final HydrologyTileKey TILE = new HydrologyTileKey(0, 0);
    private static final HydrologyTileKey EARLY_OWNER_TILE = new HydrologyTileKey(-1, -1);
    private static final HydrologyPlannerSettings EARLY_OWNER_SETTINGS =
            withPeriodTwo(standardSettings(4D, 2D, true, false, List.of()));
    private static final HydrologyTerrainSampler EARLY_OWNER_TERRAIN =
            shiftedByTile(rollingCoast(112), EARLY_OWNER_SETTINGS.routing().tileSize());
    private static final HydrologyTile EARLY_OWNER_BASELINE =
            new HydrologyPlanner(77L, EARLY_OWNER_SETTINGS, EARLY_OWNER_TERRAIN).plan(EARLY_OWNER_TILE);

    @Test
    public void sharedOwnerFutureExcludesMutableCompilerWhileCreatorRetainsIt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HydrologyPlanner planner = routingContextPlanner(request -> {
            entered.countDown();
            awaitContext(release);
        });
        Method resolve = HydrologyPlanner.class.getDeclaredMethod("resolveCrossTileOwner", HydrologyTileKey.class);
        resolve.setAccessible(true);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<Object> creator = caller.submit(() -> resolve.invoke(planner, TILE));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            CompletableFuture<?> shared = runningOwners(planner).get(TILE);
            assertNotNull(shared);
            release.countDown();
            Object creatorDraft = ownerDraft(creator.get(5, TimeUnit.SECONDS));
            Object sharedDraft = ownerDraft(shared.get(5, TimeUnit.SECONDS));
            Method compiler = creatorDraft.getClass().getDeclaredMethod("footprintCompiler");
            compiler.setAccessible(true);
            assertNotNull(compiler.invoke(creatorDraft));
            assertNull(compiler.invoke(sharedDraft));
            Method withoutCompiler = creatorDraft.getClass().getDeclaredMethod("withoutFootprintCompiler");
            withoutCompiler.setAccessible(true);
            assertEquals(withoutCompiler.invoke(creatorDraft), sharedDraft);
            assertEquals(sharedDraft, ownerDraft(resolve.invoke(planner, TILE)));
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }

    private static Object ownerDraft(Object resolution) throws Exception {
        Method draft = resolution.getClass().getDeclaredMethod("draft");
        draft.setAccessible(true);
        return draft.invoke(resolution);
    }

    @Test
    public void collidingRoutingContextKeysCompileIndependently() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger grids = new AtomicInteger();
        HydrologyPlanner planner = routingContextPlanner(request -> {
            grids.incrementAndGet();
            if (request.minimumZ() == -32) {
                entered.countDown();
                awaitContext(release);
            }
        });
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = callers.submit(() -> routingContext(planner, new HydrologyTileKey(0, 0)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<Object> second = callers.submit(() -> routingContext(planner, new HydrologyTileKey(0, -1)));
            assertNotNull(second.get(5, TimeUnit.SECONDS));
            assertFalse(first.isDone());
            assertEquals(2, grids.get());
            release.countDown();
            assertNotNull(first.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void concurrentSameKeyRoutingContextsShareOneCompilation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger grids = new AtomicInteger();
        HydrologyPlanner planner = routingContextPlanner(request -> {
            grids.incrementAndGet();
            entered.countDown();
            awaitContext(release);
        });
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = callers.submit(() -> routingContext(planner, TILE));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<Object> second = callers.submit(() -> {
                secondEntered.countDown();
                return routingContext(planner, TILE);
            });
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(50, TimeUnit.MILLISECONDS));
            assertEquals(1, grids.get());
            release.countDown();
            Object expected = first.get(5, TimeUnit.SECONDS);
            assertSame(expected, second.get(5, TimeUnit.SECONDS));
            assertSame(expected, routingContext(planner, TILE));
            assertEquals(1, grids.get());
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void failedRoutingContextCanRetryWithoutRetainingFailedWork() throws Exception {
        AtomicInteger grids = new AtomicInteger();
        IllegalStateException expected = new IllegalStateException("routing input failed");
        HydrologyPlanner planner = routingContextPlanner(request -> {
            if (grids.incrementAndGet() == 1) {
                throw expected;
            }
        });
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> routingContext(planner, TILE));
        assertSame(expected, failure.getCause());
        Object retried = routingContext(planner, TILE);
        assertSame(retried, routingContext(planner, TILE));
        assertEquals(2, grids.get());
        Field pending = HydrologyPlanner.class.getDeclaredField("resolvingRoutingContexts");
        pending.setAccessible(true);
        assertTrue(((Map<?, ?>) pending.get(planner)).isEmpty());
    }

    @Test(timeout = 5000)
    public void recursiveRoutingContextFailsImmediatelyAndCanRetry() throws Exception {
        AtomicInteger grids = new AtomicInteger();
        AtomicReference<HydrologyPlanner> reference = new AtomicReference<>();
        HydrologyPlanner planner = routingContextPlanner(request -> {
            if (grids.incrementAndGet() != 1) {
                return;
            }
            try {
                routingContext(reference.get(), TILE);
            } catch (InvocationTargetException failure) {
                throw (RuntimeException) failure.getCause();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });
        reference.set(planner);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> routingContext(planner, TILE));
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertTrue(failure.getCause().getMessage().contains("recursively load"));
        Object retried = routingContext(planner, TILE);
        assertSame(retried, routingContext(planner, TILE));
        assertEquals(2, grids.get());
    }

    private HydrologyPlanner routingContextPlanner(Consumer<HydrologyRoutingTerrainSampler.GridRequest> beforeGrid) {
        HydrologyTerrainSample sample = blockedTerrain();
        HydrologyTerrainSampler terrain = (x, z) -> sample;
        HydrologyPlannerSettings settings = standardSettings(4D, 2D, true, false, List.of());
        return new HydrologyPlanner(77L, settings, terrain, new ContextRoutingSampler(sample, beforeGrid),
                HydrologyGeometrySampler.deterministic(terrain), -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(terrain, settings.seaLevel(), -4096, 4096));
    }

    private static Object routingContext(HydrologyPlanner planner, HydrologyTileKey key) throws Exception {
        Method method = HydrologyPlanner.class.getDeclaredMethod("sourceRoutingContext", HydrologyTileKey.class);
        method.setAccessible(true);
        return method.invoke(planner, key);
    }

    private static void awaitContext(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Routing context did not release");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static final class ContextRoutingSampler implements HydrologyRoutingTerrainSampler {
        private final HydrologyTerrainSample sample;
        private final Consumer<GridRequest> beforeGrid;

        private ContextRoutingSampler(HydrologyTerrainSample sample, Consumer<GridRequest> beforeGrid) {
            this.sample = sample;
            this.beforeGrid = beforeGrid;
        }

        @Override
        public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
            beforeGrid.accept(request);
            HydrologyTerrainSample[] samples = new HydrologyTerrainSample[request.width() * request.width()];
            Arrays.fill(samples, sample);
            return samples;
        }

        @Override
        public NaturalClassification classifyNatural(int blockX, int blockZ) {
            return NaturalClassification.UNAVAILABLE;
        }
    }

    @Test
    public void earlyOwnersPrioritizeHigherRanksAndSkipRunningOwners() throws Exception {
        HydrologyPlannerSettings settings = withPeriodTwo(standardSettings(4D, 2D, true, false, List.of()));
        HydrologyPlanner planner = new HydrologyPlanner(77L, settings, (x, z) -> blockedTerrain());
        AutoCloseable admission = earlyAdmission(planner, new HydrologyTileKey(-1, -1));
        Map<HydrologyTileKey, HydrologyForkJoin.Task<?>> tasks = earlyTasks(admission);
        Map<HydrologyTileKey, CompletableFuture<?>> running = runningOwners(planner);
        HydrologyTileKey alreadyRunning = new HydrologyTileKey(0, -1);
        CompletableFuture<?> existing = new CompletableFuture<>();
        running.put(alreadyRunning, existing);
        Method prepare = admission.getClass().getDeclaredMethod("prepare");
        prepare.setAccessible(true);
        CapturingHydrologyPool pool = new CapturingHydrologyPool();
        try {
            pool.submit(() -> {
                prepare.invoke(admission);
                prepare.invoke(admission);
                return null;
            }).get(5, TimeUnit.SECONDS);
            assertEquals(List.of(new HydrologyTileKey(-2, -1),
                    new HydrologyTileKey(-1, -2), new HydrologyTileKey(-1, 0),
                    new HydrologyTileKey(-2, -2), new HydrologyTileKey(-2, 0),
                    new HydrologyTileKey(0, -2), new HydrologyTileKey(0, 0)), new ArrayList<>(tasks.keySet()));
            assertEquals(new ArrayList<>(tasks.values()), pool.submitted);
            assertEquals(existing, running.get(alreadyRunning));
        } finally {
            running.remove(alreadyRunning, existing);
            admission.close();
            pool.shutdownNow();
        }
    }

    private static final class CapturingHydrologyPool extends ForkJoinPool {
        private final ArrayList<Runnable> submitted = new ArrayList<>();

        private CapturingHydrologyPool() {
            super(1);
        }

        @Override
        public void execute(Runnable task) {
            submitted.add(task);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<HydrologyTileKey, CompletableFuture<?>> runningOwners(HydrologyPlanner planner) throws Exception {
        Field field = HydrologyPlanner.class.getDeclaredField("resolvingOwners");
        field.setAccessible(true);
        return (Map<HydrologyTileKey, CompletableFuture<?>>) field.get(planner);
    }

    @Test
    public void earlyNeighborOwnersPreserveCompleteColdOwnerOutputs() throws Exception {
        Set<Thread> parallelThreads = ConcurrentHashMap.newKeySet();
        boolean observedParallelOwner = false;
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            for (boolean organic : new boolean[]{false, true}) {
                long seed = organic ? 642L : 77L;
                HydrologyPlannerSettings settings = organic
                        ? withPeriodTwo(organicShapeSettings())
                        : EARLY_OWNER_SETTINGS;
                assertEquals(2, settings.crossTileColorPeriod());
                HydrologyTerrainSampler terrain = organic
                        ? shiftedByTile(organicShapeTerrain(), settings.routing().tileSize())
                        : EARLY_OWNER_TERRAIN;
                HydrologyTile expected = organic
                        ? new HydrologyPlanner(seed, settings, terrain).plan(EARLY_OWNER_TILE)
                        : EARLY_OWNER_BASELINE;
                HydrologyTerrainSampler counted = (int x, int z) -> {
                    parallelThreads.add(Thread.currentThread());
                    return terrain.sample(x, z);
                };
                HydrologyPlanner candidate = new HydrologyPlanner(seed, settings, counted);
                parallelThreads.clear();
                HydrologyTile actual = pool.submit(() -> candidate.plan(EARLY_OWNER_TILE))
                        .get(30, TimeUnit.SECONDS);
                observedParallelOwner |= parallelThreads.size() > 1;
                assertTileContentsEqual(expected, actual);
                assertEquals(expected.courses(), actual.courses());
                assertEquals(expected.diagnosticCandidates(), actual.diagnosticCandidates());
            }
        } finally {
            pool.shutdownNow();
        }
        assertTrue("Expected selected sources to prepare lower-rank owners", observedParallelOwner);
    }

    @Test
    public void earlyOwnersCompleteWithOneWorkerAndSaturatedRootCallers() throws Exception {
        for (int workers : new int[]{1, 2}) {
            ForkJoinPool pool = new ForkJoinPool(workers);
            CountDownLatch started = new CountDownLatch(workers);
            ArrayList<Future<HydrologyTile>> results = new ArrayList<>();
            try {
                HydrologyPlanner shared = new HydrologyPlanner(77L, EARLY_OWNER_SETTINGS, EARLY_OWNER_TERRAIN);
                for (int index = 0; index < workers; index++) {
                    results.add(pool.submit(() -> {
                        started.countDown();
                        if (!started.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Owner callers did not start");
                        }
                        return shared.plan(EARLY_OWNER_TILE);
                    }));
                }
                for (Future<HydrologyTile> result : results) {
                    assertTileContentsEqual(EARLY_OWNER_BASELINE, result.get(30, TimeUnit.SECONDS));
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private static HydrologyTerrainSampler shiftedByTile(HydrologyTerrainSampler sampler, int tileSize) {
        return (int x, int z) -> sampler.sample(x + tileSize, z + tileSize);
    }

    private static void assertTileContentsEqual(HydrologyTile expected, HydrologyTile actual) {
        assertEquals("nodes", expected.nodes(), actual.nodes());
        assertEquals("edges", expected.edges(), actual.edges());
        assertEquals("outlets", expected.outlets(), actual.outlets());
        assertEquals("courses", expected.courses(), actual.courses());
        assertEquals("cave plans", expected.cavePlans(), actual.cavePlans());
        assertEquals("diagnostics", expected.diagnosticCandidates(), actual.diagnosticCandidates());
        assertEquals("footprint column count", expected.footprint().columns().size(), actual.footprint().columns().size());
        for (Map.Entry<Long, HydrologyColumnSample> entry : expected.footprint().columns().entrySet()) {
            assertEquals("footprint column " + entry.getKey(), entry.getValue(), actual.footprint().columns().get(entry.getKey()));
        }
        assertEquals(expected, actual);
    }

    @Test
    public void emptySelectionsAndLargerColorPeriodsDoNotExpandOwnerWork() throws Exception {
        HydrologyPlannerSettings base = standardSettings(4D, 2D, true, false, List.of());
        HydrologyPlannerSettings periodTwo = withPeriodTwo(base);
        HydrologyTileKey key = new HydrologyTileKey(-1, -1);
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            HydrologyPlanner empty = new HydrologyPlanner(77L, periodTwo, (x, z) -> blockedTerrain());
            HydrologyTile expectedEmpty = new HydrologyPlanner(77L, periodTwo, (x, z) -> blockedTerrain()).plan(key);
            assertEquals(expectedEmpty, pool.submit(() -> empty.plan(key)).get(30, TimeUnit.SECONDS));
            assertEquals(Set.of(key), cachedOwnerKeys(empty));
            assertTrue(base.crossTileColorPeriod() > 2);
            HydrologyPlanner larger = new HydrologyPlanner(77L, base, rollingCoast(112));
            HydrologyPlanner serial = new HydrologyPlanner(77L, base, rollingCoast(112));
            HydrologyTile expected = serial.plan(key);
            assertEquals(expected, pool.submit(() -> larger.plan(key)).get(30, TimeUnit.SECONDS));
            assertEquals(cachedOwnerKeys(serial), cachedOwnerKeys(larger));
        } finally {
            pool.shutdownNow();
        }
    }

    private static HydrologyPlannerSettings withPeriodTwo(HydrologyPlannerSettings settings) {
        HydrologyPlannerSettings.Routing routing = settings.routing();
        int spacing = routing.sampleSpacing();
        int tileSize = (2 * (settings.publicationRadius() + 1) / spacing + 1) * spacing;
        int latticeWidth = tileSize / spacing + 5;
        return new HydrologyPlannerSettings(settings.seaLevel(),
                new HydrologyPlannerSettings.Routing(tileSize, spacing, Math.max(routing.maximumRouteNodes(), latticeWidth * latticeWidth),
                        routing.maximumRouteLength(), routing.minimumSurfaceCourseLength(), routing.minimumUndergroundCourseLength(),
                        routing.valleyPreference(), routing.uphillPenalty(), routing.slopePenalty(),
                        routing.confluenceAttraction(), routing.lengthPreference(), routing.tributaries()),
                settings.surface(), settings.hydraulics(), settings.underground(), settings.outlets(), settings.geometry(),
                settings.deepFluids(), settings.surfacePools(), settings.widestShoreBiomeWidth(), settings.seaCaves());
    }

    private static Set<?> cachedOwnerKeys(HydrologyPlanner planner) throws Exception {
        Field cacheField = HydrologyPlanner.class.getDeclaredField("resolvedOwners");
        cacheField.setAccessible(true);
        Cache<?, ?> cache = (Cache<?, ?>) cacheField.get(planner);
        return Set.copyOf(cache.asMap().keySet());
    }

    @Test
    public void earlyOwnerFailureDrainsRemainingTasksAndPreservesParentFailure() throws Exception {
        HydrologyPlanner planner = new HydrologyPlanner(19L,
                standardSettings(4D, 2D, true, false, List.of()), rollingCoast(112));
        HydrologyTileKey key = new HydrologyTileKey(-1, -1);
        AutoCloseable admission = earlyAdmission(planner, key);
        Map<HydrologyTileKey, HydrologyForkJoin.Task<?>> tasks = earlyTasks(admission);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch parentFailed = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        AssertionError childFailure = new AssertionError("owner failure");
        IllegalArgumentException parentFailure = new IllegalArgumentException("parent failure");
        try (ForkJoinPool pool = new ForkJoinPool(3)) {
            HydrologyForkJoin.Task<?> failedTask = new HydrologyForkJoin.Task<>(() -> { throw childFailure; });
            tasks.put(new HydrologyTileKey(-2, -2), failedTask);
            pool.execute(failedTask);
            HydrologyForkJoin.Task<?> delayedTask = new HydrologyForkJoin.Task<>(() -> {
                started.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Owner did not release");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                completed.incrementAndGet();
                return null;
            });
            tasks.put(new HydrologyTileKey(-2, -1), delayedTask);
            pool.execute(delayedTask);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<?> parent = pool.submit(() -> {
                try (admission) {
                    parentFailed.countDown();
                    throw parentFailure;
                }
            });
            try {
                assertTrue(parentFailed.await(5, TimeUnit.SECONDS));
                assertFalse(parent.isDone());
            } finally {
                release.countDown();
            }
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> parent.get(5, TimeUnit.SECONDS));
            Throwable cause = failure.getCause();
            if (cause != parentFailure) {
                cause = cause.getCause();
            }
            assertEquals(parentFailure, cause);
            assertEquals(1, parentFailure.getSuppressed().length);
            assertEquals(1, completed.get());
        }
    }

    @Test
    public void unusedOwnerFailureDoesNotFailPublicationButDemandUsesItsOriginalFailure() throws Exception {
        HydrologyPlanner planner = new HydrologyPlanner(19L,
                standardSettings(4D, 2D, true, false, List.of()), rollingCoast(112));
        HydrologyTileKey key = new HydrologyTileKey(-1, -1);
        HydrologyTileKey dependency = new HydrologyTileKey(-2, -2);
        AutoCloseable admission = earlyAdmission(planner, key);
        Map<HydrologyTileKey, HydrologyForkJoin.Task<?>> tasks = earlyTasks(admission);
        AtomicInteger attempts = new AtomicInteger();
        IllegalStateException expected = new IllegalStateException("unused owner failure");
        HydrologyForkJoin.Task<?> failed = new HydrologyForkJoin.Task<>(() -> {
            attempts.incrementAndGet();
            throw expected;
        });
        tasks.put(dependency, failed);
        assertThrows(IllegalStateException.class, failed::await);
        Field contextField = admission.getClass().getDeclaredField("context");
        contextField.setAccessible(true);
        Object context = contextField.get(admission);
        Method resolve = HydrologyPlanner.class.getDeclaredMethod("resolveLowerRankOwners",
                List.class, context.getClass(), Map.class);
        resolve.setAccessible(true);
        for (int demand = 0; demand < 2; demand++) {
            InvocationTargetException failure = assertThrows(
                    InvocationTargetException.class,
                    () -> resolve.invoke(planner, List.of(dependency), context, tasks));
            assertEquals(expected, failure.getCause());
        }
        assertEquals(1, attempts.get());
        admission.close();
    }

    @Test
    public void demandedOwnerErrorIsNotSuppressedOntoItselfDuringDrain() throws Exception {
        HydrologyPlanner planner = new HydrologyPlanner(19L,
                standardSettings(4D, 2D, true, false, List.of()), rollingCoast(112));
        AutoCloseable admission = earlyAdmission(planner, new HydrologyTileKey(-1, -1));
        AssertionError expected = new AssertionError("demanded owner failure");
        HydrologyForkJoin.Task<?> failed = new HydrologyForkJoin.Task<>(() -> { throw expected; });
        earlyTasks(admission).put(new HydrologyTileKey(-2, -2), failed);
        assertThrows(AssertionError.class, failed::await);
        Field primary = admission.getClass().getDeclaredField("primaryFailure");
        primary.setAccessible(true);
        primary.set(admission, expected);
        AssertionError actual = assertThrows(AssertionError.class, () -> {
            try (admission) {
                throw expected;
            }
        });
        assertEquals(expected, actual);
        assertEquals(0, actual.getSuppressed().length);
    }

    private static AutoCloseable earlyAdmission(HydrologyPlanner planner, HydrologyTileKey key) throws Exception {
        Class<?> contextType = CrossTileResolutionContext.class;
        Constructor<?> contextConstructor = contextType.getDeclaredConstructor(HydrologyTileKey.class, long.class, int.class);
        contextConstructor.setAccessible(true);
        Object context = contextConstructor.newInstance(key, 64L, 4096);
        Class<?> admissionType = Class.forName(HydrologyPlanner.class.getName() + "$ColorRankedDraftAdmission");
        Constructor<?> admissionConstructor = admissionType.getDeclaredConstructor(
                HydrologyPlanner.class, HydrologyTileKey.class, int.class, contextType);
        admissionConstructor.setAccessible(true);
        return (AutoCloseable) admissionConstructor.newInstance(planner, key, 3, context);
    }

    @SuppressWarnings("unchecked")
    private static Map<HydrologyTileKey, HydrologyForkJoin.Task<?>> earlyTasks(AutoCloseable admission) throws Exception {
        Field prepared = admission.getClass().getDeclaredField("preparedOwners");
        prepared.setAccessible(true);
        return (Map<HydrologyTileKey, HydrologyForkJoin.Task<?>>) prepared.get(admission);
    }

    @Test
    public void anchorRadiusAndAdmissionRejectBeforeSlopeSampling() throws Exception {
        HydrologyTerrainSample land = terrain(100, 7D, false, false, false, false, false, false);
        CountingNaturalSampler outside = new CountingNaturalSampler((x, z) -> land);
        assertEquals(Double.POSITIVE_INFINITY, anchorScore(countedPlanner(outside), land, 20, 0), 0D);
        assertEquals(0, outside.basisCalls);
        assertEquals(0, outside.slopeCalls);

        CountingNaturalSampler blocked = new CountingNaturalSampler((x, z) -> blockedTerrain());
        assertEquals(Double.POSITIVE_INFINITY, anchorScore(countedPlanner(blocked), land, 2, 0), 0D);
        assertEquals(1, blocked.basisCalls);
        assertEquals(0, blocked.slopeCalls);

        CountingNaturalSampler crossing = new CountingNaturalSampler((x, z) -> x == 1 ? blockedTerrain() : land);
        assertEquals(Double.POSITIVE_INFINITY, anchorScore(countedPlanner(crossing), land, 2, 0), 0D);
        assertTrue(crossing.basisCalls > 1);
        assertEquals(0, crossing.slopeCalls);

        CountingNaturalSampler admitted = new CountingNaturalSampler((x, z) -> land);
        HydrologyPlannerSettings.Routing routing = standardSettings(4D, 0D, true, false, List.of()).routing();
        double expected = land.naturalHeight() * routing.valleyPreference()
                + land.slope() * routing.slopePenalty()
                + land.routingCost() * land.routingMultiplier() + 2D * 2.4D + 2D * 0.08D;
        expected += HydrologyHash.unit(HydrologyHash.mix(91L, 719L, 2, 0)) * 1.0E-6D;
        assertEquals(Double.doubleToLongBits(expected),
                Double.doubleToLongBits(anchorScore(countedPlanner(admitted), land, 2, 0)));
        assertEquals(1, admitted.slopeCalls);
    }

    @Test
    public void refinedRouteSamplesFallbackOnlyWhenAllCandidatesFailAdmission() throws Exception {
        HydrologyTerrainSample land = terrain(100, 7D, false, false, false, false, false, false);
        AtomicInteger fallbackCalls = new AtomicInteger();
        CountingNaturalSampler admitted = new CountingNaturalSampler((x, z) -> {
            if (x == 100) {
                fallbackCalls.incrementAndGet();
            }
            return land;
        });
        assertEquals(3, routeCandidates(countedPlanner(admitted)).size());
        assertEquals(0, fallbackCalls.get());
        CountingNaturalSampler rejected = new CountingNaturalSampler((x, z) -> {
            if (x == 100) {
                fallbackCalls.incrementAndGet();
                return land;
            }
            return blockedTerrain();
        });
        assertEquals(1, routeCandidates(countedPlanner(rejected)).size());
        assertEquals(1, fallbackCalls.get());
    }

    @Test
    public void surfaceRouteMemoPreservesCompleteOwnerOutputAcrossPublicationPasses() throws Exception {
        for (boolean organic : new boolean[]{false, true}) {
            HydrologyPlannerSettings settings = organic ? organicShapeSettings()
                    : standardSettings(4D, 2D, true, false, List.of());
            HydrologyTerrainSampler terrain = organic ? organicShapeTerrain() : rollingCoast(112);
            SurfaceRouteMemos uncached = new SurfaceRouteMemos(false);
            SurfaceRouteMemos cached = new SurfaceRouteMemos(true);
            HydrologyTile expected = planWithSurfaceRouteMemos(new HydrologyPlanner(642L, settings, terrain), uncached);
            HydrologyTile actual = planWithSurfaceRouteMemos(new HydrologyPlanner(642L, settings, terrain), cached);

            assertEquals(expected, actual);
            assertEquals(expected.courses(), actual.courses());
            assertEquals(expected.diagnosticCandidates(), actual.diagnosticCandidates());
            assertTrue(cached.computations <= uncached.computations);
            if (organic) {
                assertTrue("hits=" + cached.hits, cached.hits > 0);
                assertTrue("uncached=" + uncached.computations + " cached=" + cached.computations,
                        cached.computations < uncached.computations);
            }
        }
    }

    @Test
    public void biomeIncisionMultiplierCannotExceedTheConfiguredSurfaceMaximum() {
        assertEquals(6, HydrologySourcePlanner.permittedSurfaceIncision(6, 2D));
        assertEquals(3, HydrologySourcePlanner.permittedSurfaceIncision(6, 0.5D));
    }

    @Test
    public void outletFirstPlanIsAcyclicAndHydraulicallyNonRising() {
        HydrologyPlanner planner = planner(77L, standardSettings(4D, 0D, true, false, List.of()), rollingCoast(112));

        HydrologyTile tile = planner.plan(TILE);

        assertFalse("diagnostics=" + tile.diagnosticCandidates(), tile.outlets().isEmpty());
        assertTrue(tile.outlets().stream().allMatch(RiverOutlet::directOcean));
        assertTrue(tile.acyclic());
        assertFalse(surfaceCourses(tile).isEmpty());
        for (DrainageEdge edge : tile.edges()) {
            DrainageNode upstream = tile.node(edge.upstreamNodeId()).orElseThrow();
            DrainageNode downstream = tile.node(edge.downstreamNodeId()).orElseThrow();
            assertTrue(downstream.potential() < upstream.potential());
        }
        for (RiverCourse course : tile.courses()) {
            assertTrue(course.hydraulicallyNonRising());
            for (HydraulicSegment segment : course.segments()) {
                if (segment.drop() > 0) {
                    assertTrue(segment.type().isDrop());
                    assertFalse(segment.fallingFluid());
                }
            }
        }
    }

    @Test
    public void acceptedSurfaceCourseUsesOneDeterministicContinuousOrganicCurve() {
        HydrologyPlannerSettings settings = organicShapeSettings();
        HydrologyTerrainSampler terrain = organicShapeTerrain();
        HydrologyTile first = new HydrologyPlanner(642L, settings, terrain).plan(TILE);
        HydrologyTile replay = new HydrologyPlanner(642L, settings, terrain).plan(TILE);

        assertEquals(first.courses(), replay.courses());
        List<RiverCourse> courses = surfaceCourses(first);
        assertTrue(first.diagnosticCandidates().toString(), courses.size() <= 4);
        RiverCourse mainCourse = courses.getFirst();
        for (RiverCourse course : courses) {
            if (course.drainageEdges().size() > mainCourse.drainageEdges().size()) {
                mainCourse = course;
            }
        }
        ArrayList<HydrologyPoint> centerline = new ArrayList<>();
        for (HydraulicSegment segment : mainCourse.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                if (centerline.isEmpty() || !centerline.getLast().equals(point)) {
                    centerline.add(point);
                }
            }
        }
        assertTrue(centerline.size() >= 64);
        double pathLength = 0D;
        for (int index = 1; index < centerline.size(); index++) {
            HydrologyPoint previous = centerline.get(index - 1);
            HydrologyPoint point = centerline.get(index);
            pathLength += StrictMath.hypot(point.x() - previous.x(), point.z() - previous.z());
        }
        HydrologyPoint start = centerline.getFirst();
        HydrologyPoint end = centerline.getLast();
        double directLength = StrictMath.hypot(end.x() - start.x(), end.z() - start.z());
        assertTrue("sinuosity=" + pathLength / directLength, pathLength / directLength <= 1.5D);
        assertTrue(
                "deviation=" + maximumChordDeviationRatio(centerline),
                maximumChordDeviationRatio(centerline) <= 0.19D
        );
        double maximumStickLength = maximumQuantizedStickLength(centerline);
        assertTrue(
                "maximumStickLength=" + maximumStickLength,
                maximumStickLength <= settings.routing().sampleSpacing() * 0.75D
        );
    }

    @Test
    public void fallbackSourceTargetCannotExceedAvailableOutlets() {
        assertEquals(1, HydrologySourcePlanner.effectiveSourceTarget(true, 8, 1));
        assertEquals(0, HydrologySourcePlanner.effectiveSourceTarget(true, 8, 0));
        assertEquals(8, HydrologySourcePlanner.effectiveSourceTarget(false, 8, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> HydrologySourcePlanner.effectiveSourceTarget(true, -1, 1)
        );
    }

    @Test
    public void zeroMouthLevelingDistanceKeepsAcceptedCoursesHydraulicallyNonRising() {
        HydrologyPlannerSettings base = standardSettings(4D, 0D, true, false, List.of());
        HydrologyPlannerSettings.Outlets configured = base.outlets();
        HydrologyPlannerSettings settings = withOutlets(base, HydrologyPlannerSettings.Outlets.of(
                configured.oceanEnabled(),
                configured.coastalGrotto(),
                configured.inlandGrotto(),
                configured.surfaceSinkholesEnabled(),
                configured.coastalCliffMinimumHeight(),
                0,
                configured.maximumOceanApron(),
                configured.maximumPerTile(),
                configured.maximumPerTile())
        );

        HydrologyTile tile = planner(78L, settings, rollingCoast(112)).plan(TILE);

        assertFalse(tile.diagnosticCandidates().toString(), surfaceCourses(tile).isEmpty());
        assertTrue(surfaceCourses(tile).stream().allMatch(RiverCourse::hydraulicallyNonRising));
    }

    @Test
    public void requiredSourceQuotaOnlyAppliesWhenAProvenOutletExists() {
        HydrologyPlannerSettings base = standardSettings(0D, 0D, true, false, List.of());
        HydrologyPlannerSettings settings = withSurfaceSources(base, new HydrologyPlannerSettings.Source(
                true,
                0D,
                80,
                0,
                0,
                24
        ));
        HydrologyTerrainSampler connected = (int x, int z) -> terrain(
                110 - Math.floorDiv(x, 16),
                1D,
                x >= 112,
                true,
                x >= 0 && x <= 16,
                true,
                false,
                false
        );
        HydrologyTerrainSampler disconnected = (int x, int z) -> terrain(
                110,
                0D,
                false,
                true,
                x >= 0 && x <= 16,
                true,
                false,
                false
        );

        HydrologyTile connectedTile = new HydrologyPlanner(81L, settings, connected).plan(TILE);
        HydrologyTile disconnectedTile = new HydrologyPlanner(81L, settings, disconnected).plan(TILE);

        assertEquals(0, settings.surface().sources().maximumPerTile());
        assertTrue(settings.publicationRadius() > 0);
        assertFalse(surfaceCourses(connectedTile).isEmpty());
        assertTrue(surfaceCourses(disconnectedTile).isEmpty());
        assertTrue(disconnectedTile.outlets().isEmpty());
    }

    @Test
    public void rejectedRequiredCourseBackfillsTheNextRankedRequiredCandidate() {
        HydrologyPlannerSettings base = standardSettings(0D, 0D, true, false, List.of());
        HydrologyPlannerSettings settings = withUndergroundSources(base, new HydrologyPlannerSettings.Source(
                true,
                0D,
                Integer.MIN_VALUE,
                0,
                0,
                32
        ));
        HydrologyTerrainSampler terrain = (int x, int z) -> terrain(
                120 - Math.floorDiv(x, 16) - (z >= 64 ? 4 : 0),
                1D,
                x >= 112,
                true,
                false,
                false,
                x == 0 && (z == 0 || z == 96),
                x == 0 && (z == 0 || z == 96)
        );
        HydrologyPlanner acceptedPlanner = new HydrologyPlanner(8101L, settings, terrain, solidCaveView());
        HydrologyTile accepted = acceptedPlanner.plan(TILE);
        RiverCourse initiallySelected = courses(accepted, RiverCourseType.UNDERGROUND).getFirst();
        DrainageNode initialSource = accepted.node(initiallySelected.sourceNodeId().orElseThrow()).orElseThrow();
        CavePosition hazard = accepted.cavePlan(initiallySelected.id()).orElseThrow().actions().entrySet().stream()
                .filter((Map.Entry<CavePosition, HydrologyCaveAction> entry) ->
                        entry.getKey().x() == initialSource.x()
                                && entry.getKey().z() == initialSource.z()
                                && entry.getValue() == HydrologyCaveAction.WET_SOURCE)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        HydrologyPlanner backfillPlanner = new HydrologyPlanner(
                8101L,
                settings,
                terrain,
                selectiveCaveView(Map.of(hazard, CaveVoxel.LAVA))
        );
        HydrologyTile backfilled = backfillPlanner.plan(TILE);
        HydrologyTile repeated = backfillPlanner.plan(TILE);

        assertEquals(backfilled, repeated);
        assertEquals(1, courses(backfilled, RiverCourseType.UNDERGROUND).size());
        RiverCourse replacement = courses(backfilled, RiverCourseType.UNDERGROUND).getFirst();
        DrainageNode replacementSource = backfilled.node(replacement.sourceNodeId().orElseThrow()).orElseThrow();
        assertTrue(initialSource.z() < replacementSource.z());
        assertFalse(replacement.sourceNodeId().equals(initiallySelected.sourceNodeId()));
        assertTrue(backfilled.diagnosticCandidates().stream().anyMatch(
                (HydrologyDiagnosticCandidate candidate) ->
                        candidate.rejection() == HydrologyCandidateRejection.CAVE_CONTAINMENT
                                && candidate.point().x() == initialSource.x()
                                && candidate.point().z() == initialSource.z()
        ));
    }

    @Test
    public void requiredBackfillRestoresTheRequestedAcceptedCount() {
        HydrologyPlannerSettings base = standardSettings(0D, 0D, true, false, List.of());
        HydrologyPlannerSettings settings = withUndergroundSources(base, new HydrologyPlannerSettings.Source(
                true,
                2D,
                Integer.MIN_VALUE,
                1,
                2,
                32
        ));
        HydrologyTerrainSampler terrain = (int x, int z) -> terrain(
                120 - Math.floorDiv(x, 16) - Math.floorDiv(Math.max(0, z), 48),
                1D,
                x >= 112,
                true,
                false,
                false,
                x == 0 && (z == 0 || z == 48 || z == 96),
                x == 0 && (z == 0 || z == 48 || z == 96)
        );
        HydrologyTile accepted = new HydrologyPlanner(8102L, settings, terrain, solidCaveView()).plan(TILE);
        List<RiverCourse> initialCourses = courses(accepted, RiverCourseType.UNDERGROUND);
        assertEquals(2, initialCourses.size());
        RiverCourse rejectedCourse = initialCourses.getFirst();
        CavePosition hazard = accepted.cavePlan(rejectedCourse.id()).orElseThrow().actions().entrySet().stream()
                .filter((Map.Entry<CavePosition, HydrologyCaveAction> entry) ->
                        entry.getValue() == HydrologyCaveAction.WET_SOURCE)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
        Set<Long> initialSourceIds = new HashSet<>();
        for (RiverCourse course : initialCourses) {
            initialSourceIds.add(course.sourceNodeId().orElseThrow());
        }

        HydrologyTile filtered = new HydrologyPlanner(
                8102L,
                settings,
                terrain,
                selectiveCaveView(Map.of(hazard, CaveVoxel.LAVA))
        ).plan(TILE);

        List<RiverCourse> survivingCourses = courses(filtered, RiverCourseType.UNDERGROUND);
        assertEquals(2, survivingCourses.size());
        assertTrue(survivingCourses.stream().anyMatch(
                (RiverCourse course) -> initialSourceIds.contains(course.sourceNodeId().orElseThrow())
        ));
        assertTrue(survivingCourses.stream().anyMatch(
                (RiverCourse course) -> !initialSourceIds.contains(course.sourceNodeId().orElseThrow())
        ));
    }

    @Test
    public void rejectedOptionalCourseBackfillsTheRequestedAcceptedCount() {
        HydrologyPlannerSettings base = standardSettings(0D, 0D, true, false, List.of());
        HydrologyPlannerSettings settings = withUndergroundSources(base, new HydrologyPlannerSettings.Source(
                true,
                2D,
                Integer.MIN_VALUE,
                0,
                2,
                0
        ));
        HydrologyTerrainSampler terrain = (int x, int z) -> terrain(
                120 - Math.floorDiv(x, 16) - (z >= 64 ? 4 : 0),
                1D,
                x >= 112,
                true,
                false,
                false,
                x == 0 && (z == 0 || z == 48 || z == 96),
                false
        );
        HydrologyTile baseline = new HydrologyPlanner(8102L, settings, terrain, solidCaveView()).plan(TILE);
        List<RiverCourse> baselineCourses = courses(baseline, RiverCourseType.UNDERGROUND);
        assertEquals(2, baselineCourses.size());
        RiverCourse rejectedCourse = baselineCourses.getFirst();
        DrainageNode rejectedSource = baseline.node(rejectedCourse.sourceNodeId().orElseThrow()).orElseThrow();
        Set<Long> initialSourceIds = new HashSet<>();
        for (RiverCourse course : baselineCourses) {
            initialSourceIds.add(course.sourceNodeId().orElseThrow());
        }
        CavePosition hazard = baseline.cavePlan(rejectedCourse.id()).orElseThrow().actions().entrySet().stream()
                .filter((Map.Entry<CavePosition, HydrologyCaveAction> entry) ->
                        entry.getKey().x() == rejectedSource.x()
                                && entry.getKey().z() == rejectedSource.z()
                                && entry.getValue() == HydrologyCaveAction.WET_SOURCE)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        HydrologyTile rejected = new HydrologyPlanner(
                8102L,
                settings,
                terrain,
                selectiveCaveView(Map.of(hazard, CaveVoxel.LAVA))
        ).plan(TILE);
        List<RiverCourse> acceptedCourses = courses(rejected, RiverCourseType.UNDERGROUND);
        assertEquals(2, acceptedCourses.size());
        assertTrue(acceptedCourses.stream().anyMatch(
                (RiverCourse course) -> !initialSourceIds.contains(course.sourceNodeId().orElseThrow())
        ));
        assertTrue(acceptedCourses.stream().flatMap((RiverCourse course) -> course.drainageEdges().stream()).allMatch(
                (DrainageEdge edge) -> edge.contributingUndergroundSources() >= 1
        ));
    }

    @Test
    public void refinedRoutesRejectUnsampledOceanBarriersBetweenCoarseNodes() {
        HydrologyPlannerSettings settings = standardSettings(4D, 0D, true, false, List.of());
        HydrologyTerrainSampler terrain = (int x, int z) -> {
            if (x >= 4 && x <= 12 || x >= 112) {
                return oceanTerrain();
            }
            return terrain(
                    118 - Math.floorDiv(x, 12),
                    1D,
                    false,
                    true,
                    x == 0,
                    x == 0,
                    false,
                    false
            );
        };

        HydrologyTile tile = new HydrologyPlanner(82L, settings, terrain).plan(TILE);

        assertTrue(surfaceCourses(tile).isEmpty());
        assertTrue(tile.diagnosticCandidates().toString(), tile.diagnosticCandidates().stream().anyMatch(
                candidate -> candidate.rejection() == HydrologyCandidateRejection.NO_DRAINAGE_PATH
        ));
    }

    @Test
    public void rejectedCandidatesRemainIsolatedFromAcceptedGenerationRenderingAndLocators() {
        HydrologyPlannerSettings settings = standardSettings(0D, 0D, false, false, List.of());
        HydrologyTerrainSampler terrain = (int x, int z) -> terrain(
                110,
                0D,
                false,
                true,
                x >= 0 && x <= 16,
                true,
                false,
                false
        );
        HydrologyTile tile = new HydrologyPlanner(181L, settings, terrain).plan(TILE);

        assertTrue(tile.courses().isEmpty());
        assertTrue(tile.footprint().isEmpty());
        assertTrue(tile.features().isEmpty());
        assertFalse(tile.diagnosticCandidates().isEmpty());
        for (HydrologyDiagnosticCandidate candidate : tile.diagnosticCandidates()) {
            assertEquals(HydrologyCandidateKind.SOURCE, candidate.kind());
            assertEquals(HydrologyFeatureType.SURFACE_POOL, candidate.projectedType());
            assertEquals(HydrologyCandidateRejection.NO_LEGAL_OUTLET, candidate.rejection());
            assertTrue(tile.columnAt(candidate.point().x(), candidate.point().z()).isEmpty());
            assertFalse(tile.renderAt(candidate.point().x(), candidate.point().z()).present());
            assertTrue(tile.nearestFeature(
                    candidate.projectedType(),
                    candidate.point().x(),
                    candidate.point().z(),
                    0
            ).isEmpty());
            HydrologyDiagnosticRenderSample diagnostic = tile.diagnosticRenderAt(
                    candidate.point().x(),
                    candidate.point().z(),
                    0
            );
            assertTrue(diagnostic.present());
            assertTrue(diagnostic.candidates().contains(candidate));
        }
    }

    @Test
    public void rollingTerrainProducesPooledStepsAndCliffsProduceWaterfalls() {
        HydrologyPlannerSettings settings = standardSettings(3D, 0D, true, false, List.of());
        HydrologyTerrainSampler rollingTerrain = rollingCoast(112);
        HydrologyTile rolling = new HydrologyPlanner(19L, settings, rollingTerrain).plan(TILE);
        HydrologyTerrainSampler cliffTerrain = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            int height = x < 64 ? 122 : 78;
            return terrain(height, x >= 56 && x <= 64 ? 24D : 1D, false, true, x <= 16, true, false, false);
        };
        HydrologyTile cliff = new HydrologyPlanner(19L, settings, cliffTerrain).plan(TILE);

        assertTrue(
                "diagnostics=" + rolling.diagnosticCandidates() + " segments=" + rolling.courses().stream()
                        .flatMap((RiverCourse course) -> course.segments().stream())
                        .map((HydraulicSegment segment) -> segment.type() + ":" + segment.drop())
                        .toList(),
                hasAny(rolling, HydrologyFeatureType.RIFFLE, HydrologyFeatureType.CASCADE)
        );
        for (HydraulicSegment segment : segments(rolling, HydrologyFeatureType.CASCADE)) {
            assertTrue(segment.drop() > 0);
            assertFalse(segment.fallingFluid());
            assertTrue(segment.centerline().size() >= segment.drop() + 1);
            int previousHead = segment.upstreamHeadY();
            for (HydrologyPoint point : segment.centerline()) {
                assertTrue(point.y() <= previousHead);
                assertTrue(previousHead - point.y() <= 1);
                assertTrue(rollingTerrain.sample(point.x() - 1, point.z()).naturalHeight() >= point.y());
                assertTrue(rollingTerrain.sample(point.x() + 1, point.z()).naturalHeight() >= point.y());
                assertTrue(rollingTerrain.sample(point.x(), point.z() - 1).naturalHeight() >= point.y());
                assertTrue(rollingTerrain.sample(point.x(), point.z() + 1).naturalHeight() >= point.y());
                previousHead = point.y();
            }
            assertEquals(segment.downstreamHeadY(), previousHead);
        }
        assertTrue(
                "features=" + cliff.features().stream().map(HydrologyFeatureRef::type).toList()
                        + " diagnostics=" + cliff.diagnosticCandidates().stream()
                        .map((HydrologyDiagnosticCandidate candidate) ->
                                candidate.projectedType() + ":" + candidate.rejection())
                        .toList(),
                hasAny(cliff, HydrologyFeatureType.WATERFALL)
        );
        for (HydraulicSegment segment : segments(cliff, HydrologyFeatureType.WATERFALL)) {
            assertTrue(segment.drop() > 0);
            assertFalse(segment.fallingFluid());
            assertTrue(segment.centerline().size() >= 2);
            assertTrue(segment.width() >= 1);
        }
        boolean sawBlendedWaterfall = false;
        for (RiverCourse course : cliff.courses()) {
            for (int index = 1; index < course.segments().size() - 1; index++) {
                HydraulicSegment waterfall = course.segments().get(index);
                if (waterfall.type() != HydrologyFeatureType.WATERFALL) {
                    continue;
                }
                HydraulicSegment approach = course.segments().get(index - 1);
                HydraulicSegment receiver = course.segments().get(index + 1);
                assertTrue(approach.type().isSurface());
                assertTrue(receiver.type().isSurface());
                assertEquals(approach.end(), waterfall.start());
                assertEquals(waterfall.end(), receiver.start());
                sawBlendedWaterfall = true;
            }
        }
        assertTrue(sawBlendedWaterfall);
    }

    @Test
    public void adjacentSurfaceDropsCollapseIntoSeparatedTransitionComplexes() {
        HydrologyPlannerSettings settings = standardSettings(1D, 0D, false, true, List.of());
        HydrologyTerrainSampler terrain = (int x, int z) -> {
            boolean source = x == 0 && z == 0;
            boolean outlet = x == 64 && z == 64;
            int distance = x + z;
            int height = tieredTransitionHeight(distance);
            return new HydrologyTerrainSample(
                    height,
                    1D,
                    false,
                    true,
                    72,
                    74,
                    true,
                    outlet,
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
                    List.of("alpha"), List.of(),
                    Double.NaN,
                    null,
                    Double.NaN,
                    true
            );
        };
        HydrologyTile tile = new HydrologyPlanner(812L, settings, terrain, solidCaveView()).plan(TILE);

        assertFalse("diagnostics=" + tile.diagnosticCandidates(), surfaceCourses(tile).isEmpty());
        boolean aggregateTransition = false;
        for (RiverCourse course : surfaceCourses(tile)) {
            for (HydraulicSegment segment : course.segments()) {
                if (segment.drop() < settings.hydraulics().waterfallMinimumDrop()) {
                    continue;
                }
                if (segment.type() == HydrologyFeatureType.WATERFALL
                        || segment.type() == HydrologyFeatureType.UNDERGROUND_DROP) {
                    aggregateTransition = true;
                }
            }
        }
        assertTrue("courses=" + surfaceCourses(tile), aggregateTransition);
    }

    @Test
    public void anUnbridgeableTerrainCrevasseRejectsTheSurfaceCourse() {
        HydrologyTerrainSampler terrain = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            int height = x == 47
                    ? 82
                    : 118 - Math.floorDiv(x, 12) + (int) StrictMath.round(StrictMath.sin(z / 18D) * 2D);
            return terrain(height, 1D, false, true, x >= 0 && x <= 24, true, true, false);
        };
        HydrologyTile tile = new HydrologyPlanner(
                19L,
                standardSettings(3D, 0D, true, false, List.of()),
                terrain
        ).plan(TILE);

        assertTrue("courses=" + surfaceCourses(tile), surfaceCourses(tile).isEmpty());
        for (HydrologyColumnSample column : tile.footprint().columns().values()) {
            for (HydrologyColumnLayer layer : column.layers()) {
                if (!layer.feature().type().isSurface() || !layer.channel() || !layer.fluidOwned()) {
                    continue;
                }
                assertTrue(
                        column.x() + "," + column.z() + " " + layer.feature().type()
                                + " head=" + layer.fluidHeadY() + " natural=" + column.naturalHeight(),
                        layer.fluidHeadY() <= column.naturalHeight()
                );
            }
        }
    }

    @Test
    public void ridgeCoursesRequireConfiguredPublishedSurfaceExposure() {
        HydrologyPlannerSettings base = standardSettings(2D, 0D, true, false, List.of());
        HydrologyPlannerSettings.Routing routing = base.routing();
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(
                base.seaLevel(),
                new HydrologyPlannerSettings.Routing(
                        routing.tileSize(),
                        routing.sampleSpacing(),
                        routing.maximumRouteNodes(),
                        routing.maximumRouteLength(), 96, 0,
                        routing.valleyPreference(),
                        routing.uphillPenalty(),
                        routing.slopePenalty(),
                        routing.confluenceAttraction(), 1D, 0
                ),
                base.surface(),
                base.hydraulics(),
                base.underground(),
                base.outlets(),
                base.geometry(),
                base.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
        HydrologyTerrainSampler repeatedRidges = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            int baseHeight = 108 - Math.floorDiv(x, 8);
            boolean ridge = x >= 48 && x <= 72 && Math.abs(z) < 24;
            int height = ridge ? baseHeight + 48 : baseHeight;
            return terrain(height, ridge ? 18D : 1D, false, true, x <= 16, true, false, false);
        };

        HydrologyTile tile = new HydrologyPlanner(993L, settings, repeatedRidges).plan(TILE);

        assertFalse("courses=" + surfaceCourses(tile), surfaceCourses(tile).isEmpty());
        for (RiverCourse course : surfaceCourses(tile)) {
            assertTrue("segments=" + course.segments(), course.segments().getFirst().type().isSurface());
            double exposedLength = 0D;
            double exposedRunLength = 0D;
            double longestExposedRunLength = 0D;
            for (HydraulicSegment segment : course.segments()) {
                if (segment.type().isSurface() && segment.type() != HydrologyFeatureType.MOUTH) {
                    double length = segmentLength(segment);
                    exposedLength += length;
                    exposedRunLength += length;
                    longestExposedRunLength = Math.max(longestExposedRunLength, exposedRunLength);
                } else {
                    exposedRunLength = 0D;
                }
            }
            assertTrue("segments=" + course.segments(), exposedLength >= 96D);
            assertTrue(
                    "segments=" + course.segments(),
                    longestExposedRunLength >= Math.min(96, settings.routing().sampleSpacing() * 2)
            );
        }
    }

    @Test
    public void undergroundSourcesUseAnIndependentBudgetAndConfiguredHeight() {
        HydrologyPlannerSettings settings = standardSettings(0D, 3D, true, false, List.of());
        HydrologyTerrainSampler undergroundCoast = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            int height = 118 - Math.floorDiv(x, 12) + (int) StrictMath.round(StrictMath.sin(z / 18D) * 2D);
            return terrain(height, 1D, false, true, false, false, x >= 0 && x <= 24, true);
        };
        HydrologyTile tile = new HydrologyPlanner(331L, settings, undergroundCoast).plan(TILE);

        assertTrue(surfaceCourses(tile).isEmpty());
        List<RiverCourse> underground = courses(tile, RiverCourseType.UNDERGROUND);
        assertFalse(underground.isEmpty());
        for (RiverCourse course : underground) {
            int head = course.segments().getFirst().upstreamHeadY();
            DrainageNode source = tile.node(course.sourceNodeId().orElseThrow()).orElseThrow();
            assertEquals(source.terrain().caveFluidY(), head);
            assertEquals(74, head);
            assertTrue(head >= settings.underground().minimumFluidY());
            assertTrue(head <= settings.underground().maximumFluidY());
        }
    }

    @Test
    public void undergroundSourcesHonorStyledFluidLevelAtEachAcceptedSource() {
        HydrologyPlannerSettings settings = standardSettings(0D, 4D, true, false, List.of());
        HydrologyTerrainSampler styledCoast = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            int height = 118 - Math.floorDiv(x, 12);
            int fluidY = 68 + Math.floorMod(x + z, 15);
            return undergroundTerrain(height, fluidY, x >= 0 && x <= 24);
        };
        HydrologyTile tile = new HydrologyPlanner(331L, settings, styledCoast).plan(TILE);
        HashSet<Integer> acceptedHeads = new HashSet<>();

        for (RiverCourse course : courses(tile, RiverCourseType.UNDERGROUND)) {
            DrainageNode source = tile.node(course.sourceNodeId().orElseThrow()).orElseThrow();
            int expected = Math.max(
                    settings.underground().minimumFluidY(),
                    Math.min(settings.underground().maximumFluidY(), source.terrain().caveFluidY())
            );
            int actual = course.segments().getFirst().upstreamHeadY();
            assertEquals(expected, actual);
            acceptedHeads.add(actual);
        }
        assertTrue(acceptedHeads.size() > 1);
    }

    @Test
    public void undergroundCoastalOutletRaisesStyledHeadToSeaLevelWithinConfiguredRange() {
        HydrologyPlannerSettings settings = withUndergroundFluidRange(
                standardSettings(0D, 4D, true, false, List.of()),
                40,
                63
        );
        HydrologyTerrainSampler coast = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            return undergroundTerrain(118 - Math.floorDiv(x, 12), 48, x >= 0 && x <= 24);
        };
        HydrologyGeometrySampler geometry = request -> request.field()
                == HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL
                ? 48
                : request.minimum();

        HydrologyTile tile = new HydrologyPlanner(
                332L,
                settings,
                coast,
                geometry,
                -4096,
                footprint -> solidCaveView()
        ).plan(TILE);

        List<RiverCourse> underground = courses(tile, RiverCourseType.UNDERGROUND);
        assertFalse(underground.isEmpty());
        for (RiverCourse course : underground) {
            RiverOutlet outlet = tile.outlet(course.outletId().orElseThrow()).orElseThrow();
            assertTrue(outlet.directOcean());
            assertEquals(settings.seaLevel(), course.segments().getFirst().upstreamHeadY());
            assertTrue(course.segments().getFirst().upstreamHeadY() >= settings.underground().minimumFluidY());
            assertTrue(course.segments().getFirst().upstreamHeadY() <= settings.underground().maximumFluidY());
            assertTrue(course.hydraulicallyNonRising());
        }
    }

    @Test
    public void undergroundCoastalOutletRejectsWhenSeaLevelExceedsConfiguredMaximum() {
        HydrologyPlannerSettings settings = withUndergroundFluidRange(
                standardSettings(0D, 4D, true, false, List.of()),
                40,
                62
        );
        HydrologyTerrainSampler coast = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            return undergroundTerrain(118 - Math.floorDiv(x, 12), 48, x >= 0 && x <= 24);
        };

        HydrologyTile tile = new HydrologyPlanner(333L, settings, coast, solidCaveView()).plan(TILE);

        assertTrue(courses(tile, RiverCourseType.UNDERGROUND).isEmpty());
        assertTrue(tile.diagnosticCandidates().stream().anyMatch(
                (HydrologyDiagnosticCandidate candidate) ->
                        candidate.rejection() == HydrologyCandidateRejection.OUTLET_LEVEL
        ));
    }

    @Test
    public void undergroundUsesInlandGrottoWhenCoastIsAboveItsFluidRange() {
        HydrologyPlannerSettings base = withUndergroundFluidRange(
                standardSettings(0D, 4D, true, true, List.of()),
                40,
                62
        );
        HydrologyPlannerSettings.Outlets configured = base.outlets();
        HydrologyPlannerSettings settings = withOutlets(base, HydrologyPlannerSettings.Outlets.of(
                configured.oceanEnabled(),
                configured.coastalGrotto(),
                configured.inlandGrotto(),
                configured.surfaceSinkholesEnabled(),
                configured.coastalCliffMinimumHeight(),
                configured.mouthLevelingDistance(),
                configured.maximumOceanApron(),
                1,
                1)
        );
        HydrologyTerrainSampler coast = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            return undergroundTerrain(118 - Math.floorDiv(x, 12), 48, x >= 0 && x <= 24);
        };

        HydrologyTile tile = new HydrologyPlanner(
                333L,
                settings,
                coast,
                HydrologyGeometrySampler.deterministic(coast),
                -4096,
                footprint -> solidCaveView()
        ).plan(TILE);

        assertFalse(tile.diagnosticCandidates().toString(), courses(tile, RiverCourseType.UNDERGROUND).isEmpty());
        for (RiverCourse course : courses(tile, RiverCourseType.UNDERGROUND)) {
            RiverOutlet outlet = tile.outlet(course.outletId().orElseThrow()).orElseThrow();
            assertEquals(HydrologyFeatureType.INLAND_GROTTO, outlet.type());
            assertFalse(outlet.directOcean());
            assertTrue(course.hydraulicallyNonRising());
        }
    }

    @Test
    public void infeasibleUndergroundCourseSkipsWidthDependentRadialSampling() {
        HydrologyPlannerSettings base = standardSettings(0D, 1D, true, false, List.of());
        HydrologyPlannerSettings narrow = withUndergroundWidth(base, 4);
        HydrologyPlannerSettings wide = withUndergroundWidth(base, 12);
        HydrologyTerrainSampler terrain = (int x, int z) -> x >= 112
                ? oceanTerrain()
                : undergroundTerrain(72, 68, x == 0 && z == 0);
        AtomicInteger narrowSamples = new AtomicInteger();
        AtomicInteger wideSamples = new AtomicInteger();

        HydrologyTile narrowTile = plannerWithRoutingSampler(
                3331L,
                narrow,
                terrain,
                narrowSamples
        ).plan(TILE);
        HydrologyTile wideTile = plannerWithRoutingSampler(
                3331L,
                wide,
                terrain,
                wideSamples
        ).plan(TILE);

        assertTrue(courses(narrowTile, RiverCourseType.UNDERGROUND).isEmpty());
        assertTrue(courses(wideTile, RiverCourseType.UNDERGROUND).isEmpty());
        assertTrue(narrowTile.diagnosticCandidates().stream().anyMatch(
                (HydrologyDiagnosticCandidate candidate) ->
                        candidate.rejection() == HydrologyCandidateRejection.CAVE_CONTAINMENT
        ));
        assertTrue(wideTile.diagnosticCandidates().stream().anyMatch(
                (HydrologyDiagnosticCandidate candidate) ->
                        candidate.rejection() == HydrologyCandidateRejection.CAVE_CONTAINMENT
        ));
        assertEquals(narrowSamples.get(), wideSamples.get());
    }

    @Test
    public void undergroundInlandOutletKeepsStyledHeadAboveOutlet() {
        HydrologyPlannerSettings settings = standardSettings(0D, 1D, false, true, List.of());
        HydrologyGeometrySampler geometry = request -> {
            if (request.field() != HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL) {
                return request.minimum();
            }
            return request.x() < 32 && request.z() < 32 ? 79 : 71;
        };

        HydrologyTile tile = new HydrologyPlanner(
                334L,
                settings,
                inlandTerrain(false, true),
                geometry,
                -4096,
                footprint -> solidCaveView()
        ).plan(TILE);

        assertFalse(tile.diagnosticCandidates().toString(), courses(tile, RiverCourseType.UNDERGROUND).isEmpty());
        RiverCourse course = courses(tile, RiverCourseType.UNDERGROUND).getFirst();
        RiverOutlet outlet = tile.outlet(course.outletId().orElseThrow()).orElseThrow();
        assertFalse(
                "outlet=" + outlet.type() + " source=" + course.sourceNodeId()
                        + " diagnostics=" + tile.diagnosticCandidates(),
                outlet.directOcean()
        );
        assertEquals(71, outlet.connectionPoint().y());
        assertEquals(79, course.segments().getFirst().upstreamHeadY());
        assertEquals(71, course.segments().getLast().downstreamHeadY());
        assertTrue(course.hydraulicallyNonRising());
    }

    @Test
    public void inlandOutletSelectionUsesDeterministicDisplacedAnchors() {
        HydrologyPlannerSettings settings = standardSettings(0D, 0D, false, true, List.of());
        AtomicInteger firstOffLatticeSamples = new AtomicInteger();
        AtomicInteger secondOffLatticeSamples = new AtomicInteger();

        HydrologyTile first = plannerWithRoutingSampler(
                335L,
                settings,
                inlandTerrain(false, false),
                firstOffLatticeSamples
        ).plan(TILE);
        HydrologyTile second = plannerWithRoutingSampler(
                335L,
                settings,
                inlandTerrain(false, false),
                secondOffLatticeSamples
        ).plan(TILE);

        assertEquals(first.outlets(), second.outlets());
        assertEquals(firstOffLatticeSamples.get(), secondOffLatticeSamples.get());
        assertTrue(firstOffLatticeSamples.get() > 0);
    }

    @Test
    public void undergroundHeadSolverMatchesExhaustiveBoundedSequences() {
        Random random = new Random(0x485944524f4c4f47L);
        for (int iteration = 0; iteration < 400; iteration++) {
            int length = 1 + random.nextInt(5);
            int[] preferred = new int[length];
            int[] minimum = new int[length];
            int[] maximum = new int[length];
            int[] solved = new int[length];
            for (int index = 0; index < length; index++) {
                minimum[index] = random.nextInt(7) - 2;
                maximum[index] = minimum[index] + random.nextInt(5);
                preferred[index] = random.nextInt(11) - 3;
            }
            int outlet = random.nextInt(11) - 3;

            boolean expected = bruteForceUndergroundHeads(minimum, maximum, outlet, 0, Integer.MAX_VALUE);
            boolean actual = HydrologyUndergroundCoursePlanner.solveUndergroundHeads(
                    preferred,
                    minimum,
                    maximum,
                    outlet,
                    solved
            );

            assertEquals(expected, actual);
            if (!actual) {
                continue;
            }
            assertEquals(outlet, solved[length - 1]);
            for (int index = 0; index < length; index++) {
                assertTrue(solved[index] >= minimum[index]);
                assertTrue(solved[index] <= maximum[index]);
                if (index > 0) {
                    assertTrue(solved[index - 1] >= solved[index]);
                }
            }
        }
    }

    @Test
    public void deepFluidPlacementIsIndependentAndHonorsItsOwnRangeAndShapeSwitches() {
        HydrologyPlannerSettings.DeepFluid deepFluid = new HydrologyPlannerSettings.DeepFluid(
                "deep_lava",
                true,
                4D,
                32,
                -180,
                -120,
                3,
                4,
                2,
                3,
                8,
                24,
                3,
                2,
                3,
                8192,
                3,
                false,
                true
        );
        HydrologyPlannerSettings settings = standardSettings(0D, 0D, false, false, List.of(deepFluid));
        HydrologyTerrainSampler terrain = (int x, int z) -> terrain(90, 0D, false, true, false, false, false, false);

        HydrologyTile tile = new HydrologyPlanner(441L, settings, terrain).plan(TILE);

        assertTrue(surfaceCourses(tile).isEmpty());
        assertTrue(courses(tile, RiverCourseType.UNDERGROUND).isEmpty());
        List<RiverCourse> deepCourses = courses(tile, RiverCourseType.DEEP_FLUID);
        assertFalse(deepCourses.isEmpty());
        for (RiverCourse course : deepCourses) {
            assertEquals("deep_lava", course.profileKey());
            assertTrue(course.segments().stream().noneMatch(
                    (HydraulicSegment segment) -> segment.type() == HydrologyFeatureType.DEEP_POOL));
            assertTrue(course.segments().stream().allMatch(
                    (HydraulicSegment segment) -> segment.type() == HydrologyFeatureType.DEEP_CHANNEL));
            assertTrue(course.segments().stream().allMatch(
                    (HydraulicSegment segment) -> hasNonCollinearInterior(segment.centerline())));
            int head = course.segments().getFirst().upstreamHeadY();
            assertTrue(head >= -180 && head <= -120);
        }
    }

    @Test
    public void routingHaloFindsAnOceanOutsideTheOwnedTile() {
        HydrologyTerrainSampler neighboringCoast = (int x, int z) -> {
            if (x >= 144) {
                return oceanTerrain();
            }
            return terrain(118 - Math.floorDiv(x, 8), 1D, false, true, x <= 16, true, false, false);
        };
        HydrologyPlanner planner = new HydrologyPlanner(
                882L,
                standardSettings(2D, 0D, true, true, List.of()),
                neighboringCoast
        );
        HydrologyTile tile = planner.plan(TILE);

        assertFalse(
                "courses=" + tile.courses() + " diagnostics=" + tile.diagnosticCandidates(),
                tile.outlets().isEmpty()
        );
        assertTrue(tile.outlets().stream().allMatch(RiverOutlet::directOcean));
        assertTrue(tile.outlets().stream().anyMatch((RiverOutlet outlet) -> outlet.connectionPoint().x() >= 144));
        HydrologyColumnSample neighboringColumn = null;
        for (HydrologyColumnSample column : tile.footprint().columns().values()) {
            if (!TILE.contains(column.x(), column.z(), tile.tileSize()) && !column.ocean()) {
                neighboringColumn = column;
                break;
            }
        }
        assertNotNull(neighboringColumn);
        HydrologyColumnSample published = new HydrologyTileCache(planner, 8)
                .columnAt(neighboringColumn.x(), neighboringColumn.z())
                .orElseThrow();
        assertTrue(published.layers().containsAll(neighboringColumn.layers()));
    }

    @Test
    public void firstOceanClipPublishesNoRiverOwnedWritesBeyondTheCoast() {
        HydrologyTile tile = new HydrologyPlanner(
                52L,
                standardSettings(3D, 0D, true, false, List.of()),
                rollingCoast(112)
        ).plan(TILE);

        assertFalse(
                "courses=" + tile.courses() + " diagnostics=" + tile.diagnosticCandidates(),
                tile.footprint().isEmpty()
        );
        for (HydrologyColumnSample column : tile.footprint().columns().values()) {
            if (!column.ocean() && column.naturalHeight() > column.seaLevel()) {
                continue;
            }
            for (HydrologyColumnLayer layer : column.layers()) {
                assertFalse(layer.terrainOwned());
                assertFalse(layer.fluidOwned());
                assertFalse(layer.grading());
                assertFalse(layer.shore());
                assertTrue(layer.fluidHeadY() <= column.seaLevel());
            }
        }
    }

    @Test
    public void footprintPersistsResolvedProfileBiomeRolesAndParentGrading() {
        HydrologyTile tile = new HydrologyPlanner(
                712L,
                standardSettings(2D, 0D, true, false, List.of()),
                rollingCoast(112)
        ).plan(TILE);

        assertFalse("courses=" + tile.courses() + " diagnostics=" + tile.diagnosticCandidates(), tile.footprint().isEmpty());
        boolean sawGrading = false;
        for (HydrologyColumnSample column : tile.footprint().columns().values()) {
            assertEquals(column.ocean() ? "ocean_parent" : "parent", column.parentBiomeKey());
            for (HydrologyColumnLayer layer : column.layers()) {
                assertTrue(layer.profileKey().equals("alpha") || layer.profileKey().equals("beta"));
                assertEquals("surface", layer.surfaceBiomeKey());
                assertEquals("mouth", layer.mouthBiomeKey());
                assertEquals("shore", layer.shoreBiomeKey());
                assertEquals("dry", layer.bankBiomeKey());
                assertEquals("flooded", layer.floodedCaveBiomeKey());
                if (layer.grading() && !layer.shore() && !layer.channel()) {
                    sawGrading = true;
                    assertEquals("parent", column.parentBiomeKey());
                    assertEquals(layer.bankBiomeKey(), layer.biomeKey());
                }
            }
        }
        assertTrue(sawGrading);
    }

    @Test
    public void fractionalShoreContentAndPhysicalParentGradingRemainIndependent() {
        HydrologyPlannerSettings settings = standardSettings(3D, 0D, true, false, List.of());
        HydrologyTile tile = new HydrologyPlanner(501L, settings, rollingCoast(112)).plan(TILE);
        boolean sawFractionalShore = false;
        boolean sawChangedParentGrading = false;
        double maximumShoreDistance = 0D;
        double maximumGradingDistance = 0D;
        int maximumChannelRadius = 0;
        for (HydrologyColumnSample column : tile.footprint().columns().values()) {
            for (HydrologyColumnLayer layer : column.layers()) {
                if (layer.oceanApron() || !layer.feature().type().isSurface()) {
                    continue;
                }
                HydraulicSegment segment = segment(tile, layer.feature().segmentId());
                int channelRadius = Math.max(1, segment.width() / 2);
                maximumChannelRadius = Math.max(maximumChannelRadius, channelRadius);
                double distance = distanceToCenterline(
                        new HydrologyPoint(column.x(), layer.fluidHeadY(), column.z()),
                        segment.centerline()
                );
                if (layer.shore()) {
                    maximumShoreDistance = Math.max(maximumShoreDistance, distance - channelRadius);
                    sawFractionalShore |= distance > channelRadius + 1.25D;
                    assertEquals("shore", layer.biomeKey());
                } else if (layer.grading() && !layer.channel()) {
                    maximumGradingDistance = Math.max(maximumGradingDistance, distance - channelRadius);
                    sawChangedParentGrading |= layer.bedY() != column.naturalHeight();
                    assertEquals(layer.bankBiomeKey(), layer.biomeKey());
                    assertEquals("parent", column.parentBiomeKey());
                }
            }
        }

        assertTrue(
                "fractional=" + sawFractionalShore
                        + " grading=" + sawChangedParentGrading
                        + " shore=" + maximumShoreDistance
                        + " gradingDistance=" + maximumGradingDistance
                        + " radius=" + maximumChannelRadius
                        + " courses=" + surfaceCourses(tile).size()
                        + " edges=" + tile.edges().size(),
                sawFractionalShore
        );
        assertTrue(sawChangedParentGrading);
        assertTrue(maximumGradingDistance > 0D);
        assertTrue(maximumChannelRadius > 0);
    }

    @Test
    public void surfaceCompilationPublishesOneTrunkAndNoBranchesPerOutlet() {
        HydrologyPlannerSettings base = standardSettings(6D, 0D, true, false, List.of());
        HydrologyPlannerSettings.Surface deepeningSurface = new HydrologyPlannerSettings.Surface(
                base.surface().enabled(),
                base.surface().sources(),
                base.surface().minimumWidth(),
                base.surface().maximumWidth(),
                base.surface().minimumDepth(),
                8,
                128,
                base.surface().shoreWidth(),
                HydrologyPlannerSettings.Banks.defaults());
        HydrologyPlannerSettings shaped = new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                deepeningSurface,
                base.hydraulics(),
                base.underground(),
                base.outlets(),
                HydrologyPlannerSettings.Geometry.defaults(),
                base.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
        HydrologyPlannerSettings settings = withOutlets(shaped, HydrologyPlannerSettings.Outlets.of(
                true,
                base.outlets().coastalGrotto(),
                base.outlets().inlandGrotto(),
                base.outlets().surfaceSinkholesEnabled(),
                base.outlets().coastalCliffMinimumHeight(),
                base.outlets().mouthLevelingDistance(),
                base.outlets().maximumOceanApron(),
                1,
                1)
        );
        HydrologyTile tile = new HydrologyPlanner(642L, settings, optionalRollingCoast(112)).plan(TILE);

        List<RiverCourse> courses = surfaceCourses(tile);
        assertFalse(courses.isEmpty());
        HashSet<Long> outletIds = new HashSet<>();
        for (RiverCourse course : courses) {
            outletIds.add(course.outletId().orElseThrow());
        }
        assertEquals(1, outletIds.size());
        assertEquals(1, courses.size());
        HydrologyFeatureType terminal = courses.getFirst().segments().getLast().type();
        assertTrue(terminal == HydrologyFeatureType.MOUTH
                || terminal == HydrologyFeatureType.COASTAL_GROTTO
                || terminal == HydrologyFeatureType.INLAND_GROTTO);
    }

    @Test
    public void everyAcceptedDropCompilesAContinuousBedAndReceivingBasin() {
        HydrologyTerrainSampler cliffTerrain = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            int height = x < 64 ? 124 : 78;
            return terrain(height, x >= 56 && x <= 64 ? 24D : 1D, false, true, x <= 16, true, false, false);
        };
        HydrologyPlannerSettings settings = standardSettings(3D, 0D, true, false, List.of());
        HydrologyTile tile = new HydrologyPlanner(
                221L,
                settings,
                cliffTerrain
        ).plan(TILE);
        List<HydraulicSegment> drops = new ArrayList<>();
        for (RiverCourse course : surfaceCourses(tile)) {
            for (HydraulicSegment segment : course.segments()) {
                if (segment.drop() > 0) {
                    drops.add(segment);
                }
            }
        }

        assertFalse(drops.isEmpty());
        for (HydraulicSegment drop : drops) {
            boolean bed = false;
            boolean falling = false;
            boolean receiving = false;
            for (HydrologyColumnSample column : tile.footprint().columns().values()) {
                for (HydrologyColumnLayer layer : column.layers()) {
                    if (layer.feature().segmentId() != drop.id()) {
                        continue;
                    }
                    if (!layer.channel()) {
                        continue;
                    }
                    falling |= layer.fallingFluid();
                    assertTrue(layer.bedY() < layer.fluidHeadY());
                    bed = true;
                    if (layer.receivingPool()) {
                        receiving = true;
                        assertEquals(drop.downstreamHeadY(), layer.fluidHeadY());
                    }
                }
            }
            assertTrue(bed);
            assertEquals(drop.fallingFluid(), falling);
            assertFalse(drop.fallingFluid());
            if (!receiving) {
                HydrologyColumnSample endColumn = tile.columnAt(drop.end().x(), drop.end().z()).orElseThrow();
                receiving = endColumn.layers().stream().anyMatch((HydrologyColumnLayer layer) ->
                        layer.channel() && layer.fluidHeadY() == drop.downstreamHeadY());
            }
            assertTrue("drop=" + drop, receiving);
            if (drop.type() == HydrologyFeatureType.WATERFALL) {
                assertTrue(
                        "waterfall drop=" + drop.drop(),
                        drop.drop() >= settings.surface().banks().waterfallMinimumDrop()
                );
                continue;
            }
            int previousHead = drop.upstreamHeadY();
            for (HydrologyPoint point : drop.centerline()) {
                assertTrue(point.y() <= previousHead);
                assertTrue(
                        drop.type() + " drop=" + drop.drop() + " points=" + drop.centerline().size()
                                + " step=" + (previousHead - point.y()),
                        previousHead - point.y() <= settings.geometry().drops().stepLimit(drop.type())
                );
                previousHead = point.y();
            }
        }
    }

    @Test
    public void coastalGrottoIsASeaLevelEllipsoidWithADirectOceanConnection() {
        HydrologyPlannerSettings base = standardSettings(2D, 0D, true, true, List.of());
        HydrologyPlannerSettings settings = withOutlets(base, HydrologyPlannerSettings.Outlets.of(
                true,
                new HydrologyPlannerSettings.Grotto(true, 4, 3, 3, 4096),
                base.outlets().inlandGrotto(),
                base.outlets().surfaceSinkholesEnabled(),
                12,
                base.outlets().mouthLevelingDistance(),
                base.outlets().maximumOceanApron(),
                base.outlets().maximumPerTile(),
                base.outlets().maximumPerTile())
        );
        HydrologyTerrainSampler cliffCoast = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            return terrain(92, 2D, false, true, x <= 16, true, false, false);
        };
        HydrologyTile tile = new HydrologyPlanner(994L, settings, cliffCoast).plan(TILE);

        assertFalse("diagnostics=" + tile.diagnosticCandidates(), tile.outlets().isEmpty());
        for (RiverOutlet outlet : tile.outlets()) {
            assertEquals("outlet=" + outlet + " diagnostics=" + tile.diagnosticCandidates(),
                    HydrologyFeatureType.COASTAL_GROTTO, outlet.type());
            assertTrue(outlet.directOcean());
            assertEquals(settings.seaLevel(), outlet.connectionPoint().y());
            assertFalse(cliffCoast.sample(outlet.landwardPoint().x(), outlet.landwardPoint().z()).ocean());
            assertTrue(cliffCoast.sample(outlet.connectionPoint().x(), outlet.connectionPoint().z()).ocean());
            assertEquals(1L, outlet.landwardPoint().distanceSquared2D(outlet.connectionPoint()));
        }
        RiverCourse course = surfaceCourses(tile).stream()
                .filter((RiverCourse candidate) -> candidate.segments().getLast().type()
                        == HydrologyFeatureType.COASTAL_GROTTO)
                .findFirst()
                .orElseThrow();
        HydraulicSegment grotto = course.segments().getLast();
        assertEquals(HydrologyFeatureType.COASTAL_GROTTO, grotto.type());
        assertEquals(settings.seaLevel(), grotto.upstreamHeadY());
        HydrologyColumnLayer center = layerForSegment(
                tile.columnAt(grotto.start().x(), grotto.start().z()).orElseThrow(),
                grotto.id()
        );
        assertTrue(center.bedY() < settings.seaLevel());
        assertTrue(center.bedY() >= settings.seaLevel()
                - settings.outlets().coastalGrotto().verticalRadius() * 2);
        assertEquals(settings.seaLevel() + settings.outlets().coastalGrotto().headroom(), center.ceilingY());
        double maximumOwnedRadius = settings.outlets().coastalGrotto().horizontalRadius() + 0.25D;
        for (HydrologyColumnSample column : tile.footprint().columns().values()) {
            for (HydrologyColumnLayer layer : column.layers()) {
                if (layer.feature().segmentId() == grotto.id() && layer.terrainOwned()) {
                    assertTrue(StrictMath.hypot(
                            column.x() - grotto.start().x(),
                            column.z() - grotto.start().z()
                    ) <= maximumOwnedRadius);
                }
            }
        }
    }

    @Test
    public void coastalAndInlandOutletsAreBudgetedSeparately() {
        HydrologyPlannerSettings base = standardSettings(2D, 0D, true, true, List.of());
        HydrologyPlannerSettings settings = withOutlets(base, HydrologyPlannerSettings.Outlets.of(
                true,
                base.outlets().coastalGrotto(),
                base.outlets().inlandGrotto(),
                base.outlets().surfaceSinkholesEnabled(),
                base.outlets().coastalCliffMinimumHeight(),
                base.outlets().mouthLevelingDistance(),
                base.outlets().maximumOceanApron(),
                1,
                1
        ));
        HydrologyTerrainSampler terrain = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            if (x == 48) {
                return blockedTerrain();
            }
            boolean source = (x == 0 || x == 80) && z == 0;
            return terrain(90 - Math.floorDiv(x, 16), 1D, false, true, source, source, false, false);
        };

        HydrologyTile tile = new HydrologyPlanner(70143L, settings, terrain, solidCaveView()).plan(TILE);

        assertEquals(
                "outlets=" + tile.outlets() + " diagnostics=" + tile.diagnosticCandidates(),
                1,
                tile.outlets().stream().filter(RiverOutlet::directOcean).count()
        );
        assertEquals(
                "outlets=" + tile.outlets() + " diagnostics=" + tile.diagnosticCandidates(),
                1,
                tile.outlets().stream().filter((RiverOutlet outlet) -> !outlet.directOcean()).count()
        );
    }

    @Test
    public void aCliffAndABeachOnTheSameCoastSelectAMouthAndACoastalGrotto() {
        HydrologyTerrainSampler coast = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            if (z < 64) {
                return terrain(92, 2D, false, true, x <= 16, true, false, false);
            }
            return terrain(66 + Math.floorDiv((96 - x) * 3, 16), 1D, false, false, x <= 16, true, false, false);
        };

        HydrologyTile mixed = new HydrologyPlanner(995L, mixedCoastSettings(2), coast).plan(TILE);
        HydrologyTile single = new HydrologyPlanner(995L, mixedCoastSettings(1), coast).plan(TILE);

        assertTrue("outlets=" + mixed.outlets(), mixed.outlets().stream().allMatch(RiverOutlet::directOcean));
        assertTrue("outlets=" + mixed.outlets(), mixed.outlets().stream()
                .anyMatch((RiverOutlet outlet) -> outlet.type() == HydrologyFeatureType.MOUTH));
        assertTrue("outlets=" + mixed.outlets(), mixed.outlets().stream()
                .anyMatch((RiverOutlet outlet) -> outlet.type() == HydrologyFeatureType.COASTAL_GROTTO));
        assertEquals("outlets=" + single.outlets(), 1, single.outlets().size());
        assertEquals(HydrologyFeatureType.MOUTH, single.outlets().getFirst().type());
    }

    @Test
    public void ownedCoastOutranksHaloCoast() {
        HydrologyPlannerSettings settings = standardSettings(2D, 0D, true, false, List.of());
        HydrologyTerrainSampler twoCoasts = (int x, int z) -> {
            if (x >= 112 || x <= -32) {
                return oceanTerrain();
            }
            boolean source = x >= 48 && x <= 96;
            return terrain(88 - Math.floorDiv(x, 16), 1D, false, true, source, source, false, false);
        };

        HydrologyTile tile = new HydrologyPlanner(883L, settings, twoCoasts).plan(TILE);

        assertFalse("diagnostics=" + tile.diagnosticCandidates(), tile.outlets().isEmpty());
        assertFalse(surfaceCourses(tile).isEmpty());
        for (RiverOutlet outlet : tile.outlets()) {
            assertTrue(
                    "outlet=" + outlet,
                    TILE.contains(outlet.landwardPoint().x(), outlet.landwardPoint().z(), tile.tileSize())
            );
        }
    }

    @Test
    public void undergroundSeaLevelOutletsAreReportedAsOutletLevel() {
        HydrologyPlannerSettings base = standardSettings(2D, 1D, true, true, List.of());
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                base.surface(),
                base.hydraulics(),
                HydrologyPlannerSettings.Underground.of(
                        true,
                        base.underground().sources(),
                        40,
                        50,
                        4,
                        12,
                        2,
                        4,
                        5,
                        9,
                        true,
                        0
                ),
                base.outlets(),
                base.geometry(),
                base.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
        HydrologyTerrainSampler coast = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            boolean source = x <= 16;
            return terrain(88 - Math.floorDiv(x, 16), 1D, false, true, source, source, source, false);
        };

        HydrologyTile tile = new HydrologyPlanner(884L, settings, coast, solidCaveView()).plan(TILE);

        List<HydrologyDiagnosticCandidate> level = tile.diagnosticCandidates().stream()
                .filter((HydrologyDiagnosticCandidate candidate) ->
                        candidate.kind() == HydrologyCandidateKind.OUTLET
                                && candidate.rejection() == HydrologyCandidateRejection.OUTLET_LEVEL)
                .toList();
        assertFalse("diagnostics=" + tile.diagnosticCandidates(), level.isEmpty());
        for (HydrologyDiagnosticCandidate candidate : level) {
            assertTrue(
                    candidate.toString(),
                    candidate.projectedType() == HydrologyFeatureType.MOUTH
                            || candidate.projectedType() == HydrologyFeatureType.COASTAL_GROTTO
            );
            assertEquals(settings.seaLevel(), candidate.point().y());
            assertFalse(coast.sample(candidate.point().x(), candidate.point().z()).ocean());
        }
        assertEquals(level.size(), level.stream().map(HydrologyDiagnosticCandidate::id).distinct().count());
    }

    @Test
    public void fallbackAlternatesCoastalAndInlandTrials() {
        // An island whose coast is walled by a berm between the lattice columns: every mouth course
        // needs a cut deeper than the channel may make, so the surface routing falls back. The coast
        // offers more fallback mouths than the trial budget, so a sinkhole only gets its trial when
        // the fallback alternates coastal and inland candidates.
        HydrologyPlannerSettings settings = standardSettings(1D, 0D, true, true, List.of());
        HydrologyTerrainSampler island = (int x, int z) -> {
            if (x < -16 || x > 144 || z < -16 || z > 144) {
                return oceanTerrain();
            }
            boolean berm = (x > -13 && x < -3) || (x > 131 && x < 141)
                    || (z > -13 && z < -3) || (z > 131 && z < 141);
            boolean source = x >= 32 && x <= 96 && z >= 32 && z <= 96;
            return terrain(berm ? 170 : 90, 1D, false, true, source, source, false, false);
        };

        HydrologyTile tile = new HydrologyPlanner(885L, settings, island, solidCaveView()).plan(TILE);

        assertFalse("diagnostics=" + tile.diagnosticCandidates(), surfaceCourses(tile).isEmpty());
        assertTrue(tile.diagnosticCandidates().stream().anyMatch((HydrologyDiagnosticCandidate candidate) ->
                candidate.rejection() == HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED));
        for (RiverCourse course : surfaceCourses(tile)) {
            RiverOutlet outlet = tile.outlet(course.outletId().orElseThrow()).orElseThrow();
            assertFalse("outlet=" + outlet, outlet.directOcean());
            assertTrue(course.surfaceSinkholeContinuation());
        }
    }

    private HydrologyPlannerSettings mixedCoastSettings(int maximumCoastalPerTile) {
        HydrologyPlannerSettings base = standardSettings(2D, 0D, true, false, List.of());
        return withOutlets(base, HydrologyPlannerSettings.Outlets.of(
                true,
                new HydrologyPlannerSettings.Grotto(true, 4, 3, 3, 4096),
                base.outlets().inlandGrotto(),
                base.outlets().surfaceSinkholesEnabled(),
                12,
                base.outlets().mouthLevelingDistance(),
                base.outlets().maximumOceanApron(),
                base.outlets().maximumPerTile(),
                maximumCoastalPerTile
        ));
    }

    @Test
    public void configuredSurfaceRiverContinuesThroughOneContainedSinkholeCourse() {
        HydrologyPlannerSettings settings = standardSettings(0D, 0D, false, true, List.of());
        HydrologyTerrainSampler terrain = inlandTerrain(true, false);
        HydrologyPlanner planner = new HydrologyPlanner(7012L, settings, terrain, solidCaveView());

        HydrologyTile first = planner.plan(TILE);
        planner.plan(new HydrologyTileKey(1, 0));
        HydrologyTile repeated = planner.plan(TILE);

        assertEquals(first, repeated);
        assertEquals(first.diagnosticCandidates().toString(), 1, surfaceCourses(first).size());
        RiverCourse course = surfaceCourses(first).getFirst();
        assertTrue(course.surfaceSinkholeContinuation());
        RiverOutlet outlet = first.outlet(course.outletId().orElseThrow()).orElseThrow();
        assertEquals(HydrologyFeatureType.INLAND_GROTTO, outlet.type());
        assertFalse(outlet.directOcean());
        assertEquals(1L, outlet.landwardPoint().distanceSquared2D(outlet.connectionPoint()));

        HydraulicSegment sinkhole = course.segments().get(course.segments().size() - 2);
        HydraulicSegment grotto = course.segments().getLast();
        HydraulicSegment approach = course.segments().get(course.segments().size() - 3);
        assertEquals(HydrologyFeatureType.SINKHOLE, sinkhole.type());
        assertEquals(HydrologyFeatureType.INLAND_GROTTO, grotto.type());
        assertFalse(approach.type() == HydrologyFeatureType.SURFACE_POOL
                && approach.centerline().size() == 1);
        assertEquals(approach.end(), sinkhole.start());
        assertEquals(outlet.landwardPoint().x(), sinkhole.start().x());
        assertEquals(outlet.landwardPoint().z(), sinkhole.start().z());
        assertTrue(Math.abs(approach.width() - sinkhole.width()) <= 1);
        assertEquals(course.id(), sinkhole.courseId());
        assertEquals(course.id(), grotto.courseId());
        assertTrue(sinkhole.drop() > 0);
        assertFalse(sinkhole.fallingFluid());
        assertTrue(sinkhole.receivingPool());
        assertEquals(sinkhole.downstreamHeadY(), grotto.upstreamHeadY());

        HydrologyCavePlan cavePlan = first.cavePlan(course.id()).orElseThrow();
        assertTrue(cavePlan.accepted());
        assertEquals(course.id(), cavePlan.source().sourceId());
        Set<HydrologyCaveAction> actions = new HashSet<>(cavePlan.actions().values());
        assertFalse(actions.contains(HydrologyCaveAction.FALLING_FLUID));
        assertTrue(actions.contains(HydrologyCaveAction.WET_SOURCE));
        assertTrue(actions.contains(HydrologyCaveAction.DRY_AIR));

        HydrologyRenderSample render = first.renderAt(sinkhole.start().x(), sinkhole.start().z());
        assertTrue(render.hasFeature(HydrologyFeatureType.SINKHOLE));
        assertEquals(HydrologyFeatureType.SINKHOLE, render.primaryFeature().orElseThrow().type());
        HydrologyFeatureRef located = first.nearestFeature(
                HydrologyFeatureType.SINKHOLE,
                sinkhole.start().x(),
                sinkhole.start().z(),
                0
        ).orElseThrow();
        assertEquals(course.id(), located.courseId());
        assertEquals(sinkhole.id(), located.segmentId());
    }

    @Test
    public void disabledSurfaceSinkholesRejectSurfaceSourcesButKeepUndergroundSourcesEligible() {
        HydrologyPlannerSettings base = standardSettings(0D, 1D, false, true, List.of());
        HydrologyPlannerSettings.Outlets configured = base.outlets();
        HydrologyPlannerSettings settings = withOutlets(base, HydrologyPlannerSettings.Outlets.of(
                configured.oceanEnabled(),
                configured.coastalGrotto(),
                configured.inlandGrotto(),
                false,
                configured.coastalCliffMinimumHeight(),
                configured.mouthLevelingDistance(),
                configured.maximumOceanApron(),
                configured.maximumPerTile(),
                configured.maximumPerTile())
        );

        HydrologyTile tile = new HydrologyPlanner(
                7013L,
                settings,
                inlandTerrain(true, true),
                solidCaveView()
        ).plan(TILE);

        assertTrue(surfaceCourses(tile).isEmpty());
        assertFalse(courses(tile, RiverCourseType.UNDERGROUND).isEmpty());
        assertTrue(tile.diagnosticCandidates().stream().anyMatch(
                (HydrologyDiagnosticCandidate candidate) -> candidate.projectedType() == HydrologyFeatureType.SURFACE_POOL
                        && candidate.rejection() == HydrologyCandidateRejection.NO_LEGAL_OUTLET
        ));
        assertTrue(courses(tile, RiverCourseType.UNDERGROUND).stream().allMatch(
                (RiverCourse course) -> course.segments().getLast().type() == HydrologyFeatureType.INLAND_GROTTO
        ));
    }

    @Test
    public void directOceanOutletsTakePriorityOverConfiguredSurfaceSinkholes() {
        HydrologyPlannerSettings settings = standardSettings(2D, 0D, true, true, List.of());

        HydrologyTile tile = new HydrologyPlanner(7014L, settings, rollingCoast(112)).plan(TILE);

        assertFalse(surfaceCourses(tile).isEmpty());
        assertTrue(tile.outlets().stream().allMatch(RiverOutlet::directOcean));
        assertFalse(hasAny(tile, HydrologyFeatureType.SINKHOLE, HydrologyFeatureType.INLAND_GROTTO));
    }

    @Test
    public void rejectedOceanCourseFallsBackToOneContainedSurfaceSinkhole() {
        HydrologyPlannerSettings base = standardSettings(1D, 0D, true, true, List.of());
        HydrologyPlannerSettings.Surface surface = base.surface();
        HydrologyPlannerSettings settings = new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                new HydrologyPlannerSettings.Surface(
                        surface.enabled(),
                        surface.sources(),
                        surface.minimumWidth(),
                        surface.maximumWidth(),
                        surface.minimumDepth(),
                        surface.maximumDepth(),
                        surface.maximumIncision(),
                        surface.shoreWidth(),
                        HydrologyPlannerSettings.Banks.defaults()),
                base.hydraulics(),
                base.underground(),
                base.outlets(),
                base.geometry(),
                base.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
        HydrologyTerrainSampler terrain = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            boolean source = x == 0 && z == 0;
            int height = 120 + Math.floorDiv(Math.abs(x) + Math.abs(z - 96), 16);
            if (x >= 32 && x <= 96) {
                height += 64;
            }
            return terrain(height, 1D, false, true, source, source, false, false);
        };

        HydrologyTile tile = new HydrologyPlanner(70142L, settings, terrain, solidCaveView()).plan(TILE);

        assertEquals(tile.diagnosticCandidates().toString(), 1, surfaceCourses(tile).size());
        RiverCourse course = surfaceCourses(tile).getFirst();
        RiverOutlet outlet = tile.outlet(course.outletId().orElseThrow()).orElseThrow();
        assertFalse(outlet.directOcean());
        assertTrue(course.surfaceSinkholeContinuation());
        assertTrue(course.segments().stream().noneMatch(HydraulicSegment::fallingFluid));
    }

    @Test
    public void inlandOutletsServeOnlyComponentsWithoutAValidOceanOutlet() {
        HydrologyPlannerSettings settings = standardSettings(2D, 0D, true, true, List.of());
        HydrologyTerrainSampler terrain = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            if (x == 48) {
                return blockedTerrain();
            }
            boolean source = (x == 0 || x == 80) && z == 0;
            return terrain(120 - Math.floorDiv(x, 16), 1D, false, true, source, source, false, false);
        };

        HydrologyTile tile = new HydrologyPlanner(70140L, settings, terrain, solidCaveView()).plan(TILE);

        assertTrue(
                tile.diagnosticCandidates().toString(),
                tile.outlets().stream().anyMatch((RiverOutlet outlet) -> !outlet.directOcean())
        );
        RiverCourse course = surfaceCourses(tile).stream()
                .filter((RiverCourse candidate) -> tile.outlet(candidate.outletId().orElseThrow())
                        .map((RiverOutlet candidateOutlet) -> !candidateOutlet.directOcean())
                        .orElse(false))
                .findFirst()
                .orElseThrow();
        RiverOutlet outlet = tile.outlet(course.outletId().orElseThrow()).orElseThrow();
        assertFalse(outlet.directOcean());
        assertTrue(hasAny(tile, HydrologyFeatureType.SINKHOLE, HydrologyFeatureType.INLAND_GROTTO));
    }

    @Test
    public void sourceAdmissionRepresentsAConfiguredInlandDrainageComponent() {
        HydrologyPlannerSettings settings = standardSettings(1D, 0D, true, true, List.of());
        HydrologyTerrainSampler terrain = (int x, int z) -> {
            if (x >= 112) {
                return oceanTerrain();
            }
            if (x == 48) {
                return blockedTerrain();
            }
            boolean source = (x == 0 || x == 80) && z == 0;
            int height = x < 48 ? 90 : 130 - Math.floorDiv(x, 16);
            return terrain(height, 1D, false, true, source, false, false, false);
        };

        HydrologyTile tile = new HydrologyPlanner(70141L, settings, terrain, solidCaveView()).plan(TILE);

        assertEquals(1, surfaceCourses(tile).size());
        RiverCourse course = surfaceCourses(tile).getFirst();
        RiverOutlet outlet = tile.outlet(course.outletId().orElseThrow()).orElseThrow();
        assertFalse(
                "outlet=" + outlet.type() + " source=" + course.sourceNodeId()
                        + " diagnostics=" + tile.diagnosticCandidates(),
                outlet.directOcean()
        );
        assertTrue(hasAny(tile, HydrologyFeatureType.SINKHOLE, HydrologyFeatureType.INLAND_GROTTO));
    }

    @Test
    public void oneSinkholeContainmentHazardRejectsTheConflictingCandidateTransaction() {
        HydrologyPlannerSettings settings = standardSettings(0D, 0D, false, true, List.of());
        HydrologyTerrainSampler terrain = inlandTerrain(true, false);
        HydrologyTile accepted = new HydrologyPlanner(7015L, settings, terrain, solidCaveView()).plan(TILE);
        RiverCourse acceptedCourse = surfaceCourses(accepted).getFirst();
        HydrologyCavePlan acceptedPlan = accepted.cavePlan(acceptedCourse.id()).orElseThrow();
        CavePosition hazard = acceptedPlan.actions().entrySet().stream()
                .filter((Map.Entry<CavePosition, HydrologyCaveAction> entry) ->
                        entry.getValue() == HydrologyCaveAction.WET_SOURCE)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        HydrologyTile rejected = new HydrologyPlanner(
                7015L,
                settings,
                terrain,
                selectiveCaveView(Map.of(hazard, CaveVoxel.INCOMPATIBLE_FLUID))
        ).plan(TILE);

        assertTrue(surfaceCourses(rejected).stream().noneMatch(acceptedCourse::equals));
        assertTrue(rejected.cavePlans().stream().noneMatch(
                (HydrologyCavePlan plan) -> plan.actions().containsKey(hazard)
        ));
        assertTrue(rejected.diagnosticCandidates().stream().anyMatch(
                (HydrologyDiagnosticCandidate candidate) ->
                        candidate.rejection() == HydrologyCandidateRejection.CAVE_CONTAINMENT
        ));
    }

    @Test
    public void receivingSinkholePoolSealsGeneratedSurfaceConnectedCarving() {
        HydrologyPlannerSettings settings = standardSettings(0D, 0D, false, true, List.of());
        HydrologyTerrainSampler terrain = inlandTerrain(true, false);
        HydrologyTile accepted = new HydrologyPlanner(7016L, settings, terrain, solidCaveView()).plan(TILE);
        RiverCourse course = surfaceCourses(accepted).getFirst();
        HydraulicSegment sinkhole = course.segments().get(course.segments().size() - 2);
        HydraulicSegment grotto = course.segments().getLast();
        CavePosition surfaceLip = new CavePosition(
                sinkhole.start().x(),
                sinkhole.upstreamHeadY(),
                sinkhole.start().z()
        );
        CavePosition exposedPool = new CavePosition(
                grotto.start().x(),
                grotto.upstreamHeadY(),
                grotto.start().z()
        );
        assertEquals(HydrologyCaveAction.WET_SOURCE,
                accepted.cavePlan(course.id()).orElseThrow().actions().get(exposedPool));

        HydrologyTile intentionalOpening = new HydrologyPlanner(
                7016L,
                settings,
                terrain,
                caveView(Map.of(), Set.of(surfaceLip))
        ).plan(TILE);
        assertEquals(1, surfaceCourses(intentionalOpening).size());

        HydrologyTile sealed = new HydrologyPlanner(
                7016L,
                settings,
                terrain,
                caveView(Map.of(), Set.of(exposedPool))
        ).plan(TILE);

        assertEquals(1, surfaceCourses(sealed).size());
        RiverCourse sealedCourse = surfaceCourses(sealed).getFirst();
        assertTrue(sealed.cavePlan(sealedCourse.id()).orElseThrow()
                .baselinePreconditions().get(exposedPool).openToSurface());
    }

    @Test
    public void containedDeepPoolsUseIndependentHeightAndBoundedEllipsoidFootprints() {
        HydrologyPlannerSettings.DeepFluid deepFluid = new HydrologyPlannerSettings.DeepFluid(
                "deep_lava",
                true,
                4D,
                32,
                -180,
                -120,
                8,
                8,
                3,
                3,
                8,
                24,
                3,
                2,
                5,
                2048,
                3,
                true,
                true
        );
        HydrologyPlannerSettings settings = standardSettings(0D, 0D, false, false, List.of(deepFluid));
        HydrologyTerrainSampler highCaves = (int x, int z) -> deepTerrain(70);
        HydrologyTerrainSampler lowCaves = (int x, int z) -> deepTerrain(-40);
        HydrologyTile high = new HydrologyPlanner(1991L, settings, highCaves).plan(TILE);
        HydrologyTile low = new HydrologyPlanner(1991L, settings, lowCaves).plan(TILE);

        assertEquals(high.courses(), low.courses());
        List<RiverCourse> courses = courses(high, RiverCourseType.DEEP_FLUID);
        assertFalse(courses.isEmpty());
        for (RiverCourse course : courses) {
            HydraulicSegment pool = course.segments().getFirst();
            assertEquals(HydrologyFeatureType.DEEP_POOL, pool.type());
            assertTrue(course.segments().stream().anyMatch(
                    (HydraulicSegment segment) -> segment.type() == HydrologyFeatureType.DEEP_CHANNEL));
            assertTrue(pool.upstreamHeadY() >= deepFluid.minimumY());
            assertTrue(pool.upstreamHeadY() <= deepFluid.maximumY());
            int radius = pool.width() / 2;
            HydrologyColumnLayer center = layerForSegment(
                    high.columnAt(pool.start().x(), pool.start().z()).orElseThrow(),
                    pool.id()
            );
            HydrologyColumnLayer edge = null;
            int direction = pool.start().x() + radius < settings.routing().tileSize() ? 1 : -1;
            for (int distance = radius; distance >= 1 && edge == null; distance--) {
                HydrologyColumnSample sample = high.columnAt(
                        pool.start().x() + direction * distance,
                        pool.start().z()
                ).orElse(null);
                if (sample != null) {
                    edge = layerForSegmentOrNull(sample, pool.id());
                }
            }
            assertNotNull(edge);
            assertTrue(center.bedY() < edge.bedY());
            assertTrue(center.ceilingY() > edge.ceilingY());
            assertEquals("deep_lava", center.profileKey());
            assertEquals("flooded", center.biomeKey());
            assertTrue(hasRotationalAsymmetry(high, pool, radius));
            long poolVolume = 0L;
            long courseVolume = 0L;
            for (HydrologyColumnSample column : high.footprint().columns().values()) {
                for (HydrologyColumnLayer layer : column.layers()) {
                    if (layer.feature().segmentId() == pool.id()) {
                        poolVolume += layer.ceilingY() - layer.bedY() + 1L;
                    }
                    if (layer.feature().courseId() == course.id()) {
                        courseVolume += layer.ceilingY() - layer.bedY() + 1L;
                    }
                }
            }
            assertTrue(poolVolume <= deepFluid.maximumVolume());
            assertTrue(courseVolume <= deepFluid.maximumVolume());
        }
    }

    @Test
    public void identicalInputsProduceEqualPlansAcrossRequestOrder() {
        HydrologyPlanner planner = new HydrologyPlanner(
                99121L,
                standardSettings(3D, 2D, true, false, List.of()),
                rollingCoast(112)
        );

        HydrologyTile firstZero = planner.plan(new HydrologyTileKey(0, 0));
        HydrologyTile firstOne = planner.plan(new HydrologyTileKey(1, 0));
        HydrologyTile secondOne = planner.plan(new HydrologyTileKey(1, 0));
        HydrologyTile secondZero = planner.plan(new HydrologyTileKey(0, 0));

        assertEquals(firstZero, secondZero);
        assertEquals(firstOne, secondOne);
    }

    @Test
    public void reusedFinalTilesPreserveAdjacentPlanOutput() {
        HydrologyPlannerSettings settings = standardSettings(3D, 2D, true, false, List.of());
        HydrologyTerrainSampler terrain = rollingCoast(112);
        HydrologyPlanner baselinePlanner = new HydrologyPlanner(99122L, settings, terrain);
        List<HydrologyTileKey> reusedKeys = List.of(
                new HydrologyTileKey(-1, -1),
                new HydrologyTileKey(0, -1),
                new HydrologyTileKey(-1, 0),
                new HydrologyTileKey(0, 0)
        );
        ArrayList<HydrologyTile> reusedTiles = new ArrayList<>(reusedKeys.size());
        for (HydrologyTileKey key : reusedKeys) {
            reusedTiles.add(baselinePlanner.plan(key));
        }
        HydrologyTileKey adjacentKey = new HydrologyTileKey(1, 0);
        HydrologyTile baseline = baselinePlanner.plan(adjacentKey);
        HydrologyPlanner reusedPlanner = new HydrologyPlanner(99122L, settings, terrain);
        for (HydrologyTile tile : reusedTiles) {
            reusedPlanner.reuseResolvedTile(tile);
        }

        HydrologyTile reused = reusedPlanner.plan(adjacentKey);

        assertEquals(baseline, reused);
    }

    private HydrologyPlanner planner(
            long seed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler
    ) {
        return new HydrologyPlanner(seed, settings, sampler);
    }

    private boolean containsEdge(RiverCourse course, long edgeId) {
        for (DrainageEdge edge : course.drainageEdges()) {
            if (edge.id() == edgeId) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSurfaceEdge(HydrologyTile tile, long edgeId) {
        for (RiverCourse course : surfaceCourses(tile)) {
            if (containsEdge(course, edgeId)) {
                return true;
            }
        }
        return false;
    }

    private double segmentLength(HydraulicSegment segment) {
        double length = 0D;
        for (int pointIndex = 1; pointIndex < segment.centerline().size(); pointIndex++) {
            HydrologyPoint previous = segment.centerline().get(pointIndex - 1);
            HydrologyPoint current = segment.centerline().get(pointIndex);
            length += StrictMath.hypot(current.x() - previous.x(), current.z() - previous.z());
        }
        return length;
    }

    private int tieredTransitionHeight(int distance) {
        if (distance < 32) {
            return 132;
        }
        if (distance < 48) {
            return 124;
        }
        if (distance < 64) {
            return 120;
        }
        return distance < 80 ? 106 : 98;
    }

    private static HydrologyPlannerSettings standardSettings(
            double surfaceDensity,
            double undergroundDensity,
            boolean oceanEnabled,
            boolean inlandEnabled,
            List<HydrologyPlannerSettings.DeepFluid> deepFluids
    ) {
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(
                true,
                surfaceDensity,
                80,
                0,
                6,
                24
        );
        HydrologyPlannerSettings.Source undergroundSources = new HydrologyPlannerSettings.Source(
                true,
                undergroundDensity,
                Integer.MIN_VALUE,
                0,
                4,
                32
        );
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(128, 16, 512, 256, 0, 0, 0.5D, 12D, 0.5D, 0.1D, 1D, 0),
                new HydrologyPlannerSettings.Surface(
                        surfaceDensity > 0D || surfaceSources.maximumPerTile() > 0,
                        surfaceSources,
                        4,
                        18,
                        2,
                        4,
                        10,
                        1.5D,
                        HydrologyPlannerSettings.Banks.defaults()),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        undergroundDensity > 0D,
                        undergroundSources,
                        68,
                        82,
                        4,
                        12,
                        2,
                        4,
                        5,
                        9,
                        true,
                        0
                ),
                HydrologyPlannerSettings.Outlets.of(
                        oceanEnabled,
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        new HydrologyPlannerSettings.Grotto(inlandEnabled, 4, 3, 3, 4096),
                        inlandEnabled,
                        12,
                        32,
                        2,
                        4,
                        4
                ),
                stableGeometry(),
                deepFluids, List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private static HydrologyPlannerSettings.Geometry stableGeometry() {
        HydrologyPlannerSettings.ChannelShape stableChannel =
                HydrologyPlannerSettings.ChannelShape.of(2D, 0D, 0D, 11);
        return new HydrologyPlannerSettings.Geometry(
                new HydrologyPlannerSettings.Meanders(224, 72, 0D, 0D, 0D, 0, 75D),
                stableChannel,
                stableChannel,
                stableChannel,
                HydrologyPlannerSettings.Geometry.defaults().drops()
        );
    }

    private HydrologyPlannerSettings organicShapeSettings() {
        HydrologyPlannerSettings base = standardSettings(8D, 0D, true, false, List.of());
        HydrologyPlannerSettings.Surface surface = base.surface();
        HydrologyPlannerSettings.Outlets outlets = base.outlets();
        return new HydrologyPlannerSettings(
                base.seaLevel(),
                new HydrologyPlannerSettings.Routing(
                        512,
                        64,
                        1024,
                        2048, 64, 32,
                        1.5D,
                        24D,
                        2D,
                        0.2D,
                        1D,
                        0
                ),
                new HydrologyPlannerSettings.Surface(
                        true,
                        new HydrologyPlannerSettings.Source(true, 8D, 80, 0, 8, 128),
                        surface.minimumWidth(),
                        surface.maximumWidth(),
                        surface.minimumDepth(),
                        surface.maximumDepth(),
                        96,
                        surface.shoreWidth(),
                        HydrologyPlannerSettings.Banks.defaults()),
                new HydrologyPlannerSettings.Hydraulics(5),
                base.underground(),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        outlets.coastalGrotto(),
                        outlets.inlandGrotto(),
                        outlets.surfaceSinkholesEnabled(),
                        outlets.coastalCliffMinimumHeight(),
                        48,
                        outlets.maximumOceanApron(),
                        1,
                        1
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private HydrologyTerrainSampler organicShapeTerrain() {
        return (int x, int z) -> {
            if (x >= 448) {
                return oceanTerrain();
            }
            int height = 110
                    - Math.floorDiv(x, 10)
                    + Math.floorDiv(StrictMath.abs(z - 256), 32);
            boolean source = x >= 0 && x < 320;
            return terrain(height, 1D, false, false, source, false, false, false);
        };
    }

    private static HydrologyTerrainSampler rollingCoast(int coastX) {
        return (int x, int z) -> {
            if (x >= coastX) {
                return oceanTerrain();
            }
            int height = 118 - Math.floorDiv(x, 12) + (int) StrictMath.round(StrictMath.sin(z / 18D) * 2D);
            return terrain(height, 1D, false, true, x >= 0 && x <= 24, true, true, false);
        };
    }

    private HydrologyTerrainSampler optionalRollingCoast(int coastX) {
        return (int x, int z) -> {
            if (x >= coastX) {
                return oceanTerrain();
            }
            int height = 118 - Math.floorDiv(x, 12) + (int) StrictMath.round(StrictMath.sin(z / 18D) * 2D);
            return terrain(height, 1D, false, true, x >= 0 && x <= 24, false, true, false);
        };
    }

    private HydrologyTerrainSampler inlandTerrain(boolean surfaceSource, boolean undergroundSource) {
        return (int x, int z) -> {
            boolean source = x == 0 && z == 0;
            boolean outlet = x == 64 && z == 64;
            int height = 120 - Math.floorDiv(x + z, 16);
            return new HydrologyTerrainSample(
                    height,
                    1D,
                    false,
                    true,
                    72,
                    74,
                    true,
                    outlet,
                    surfaceSource && source,
                    surfaceSource && source,
                    undergroundSource && source,
                    undergroundSource && source,
                    0D,
                    surfaceSource && source ? 1D : 0D,
                    undergroundSource && source ? 1D : 0D,
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
                    List.of("alpha"), List.of(),
                    Double.NaN,
                    null,
                    Double.NaN,
                    true
            );
        };
    }

    private CaveVoxelView solidCaveView() {
        return caveView(Map.of(), Set.of());
    }

    private CaveVoxelView selectiveCaveView(Map<CavePosition, CaveVoxel> voxels) {
        return caveView(voxels, Set.of());
    }

    private CaveVoxelView caveView(Map<CavePosition, CaveVoxel> voxels, Set<CavePosition> surfaceOpenings) {
        return new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return position.y() > -4096 && position.y() < 4096;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                return voxels.getOrDefault(position, CaveVoxel.SOLID);
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return surfaceOpenings.contains(position);
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return false;
            }
        };
    }

    private static HydrologyTerrainSample oceanTerrain() {
        return new HydrologyTerrainSample(
                54,
                0D,
                true,
                false,
                30,
                32,
                false,
                false,
                false,
                false,
                false,
                false,
                0D,
                0D,
                0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                "ocean_parent",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("alpha", "beta"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true
        );
    }

    private HydrologyTerrainSample blockedTerrain() {
        return new HydrologyTerrainSample(
                100,
                0D,
                false,
                false,
                72,
                74,
                false,
                false,
                false,
                false,
                false,
                false,
                0D,
                0D,
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
                List.of("alpha"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true
        );
    }

    private static HydrologyTerrainSample terrain(
            int height,
            double slope,
            boolean ocean,
            boolean cave,
            boolean surfaceSource,
            boolean requiredSurface,
            boolean undergroundSource,
            boolean requiredUnderground
    ) {
        return new HydrologyTerrainSample(
                height,
                slope,
                ocean,
                cave,
                72,
                74,
                !ocean,
                !ocean,
                surfaceSource,
                requiredSurface,
                undergroundSource,
                requiredUnderground,
                0D,
                surfaceSource ? 1D : 0D,
                undergroundSource ? 1D : 0D,
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
                List.of("beta", "alpha"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true
        );
    }

    private HydrologyTerrainSample deepTerrain(int caveFloorY) {
        return new HydrologyTerrainSample(
                90,
                0D,
                false,
                true,
                caveFloorY,
                74,
                true,
                false,
                false,
                false,
                false,
                false,
                0D,
                0D,
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
                List.of("alpha"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true
        );
    }

    private HydrologyTerrainSample undergroundTerrain(int height, int caveFluidY, boolean source) {
        return new HydrologyTerrainSample(
                height,
                1D,
                false,
                true,
                caveFluidY - 4,
                caveFluidY,
                true,
                true,
                false,
                false,
                source,
                source,
                0D,
                0D,
                source ? 1D : 0D,
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
                List.of("alpha", "beta"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true
        );
    }

    private HydrologyPlannerSettings withSurfaceSources(
            HydrologyPlannerSettings settings,
            HydrologyPlannerSettings.Source sources
    ) {
        HydrologyPlannerSettings.Surface surface = settings.surface();
        return new HydrologyPlannerSettings(
                settings.seaLevel(),
                settings.routing(),
                new HydrologyPlannerSettings.Surface(
                        true,
                        sources,
                        surface.minimumWidth(),
                        surface.maximumWidth(),
                        surface.minimumDepth(),
                        surface.maximumDepth(),
                        surface.maximumIncision(),
                        surface.shoreWidth(),
                        HydrologyPlannerSettings.Banks.defaults()),
                settings.hydraulics(),
                settings.underground(),
                settings.outlets(),
                HydrologyPlannerSettings.Geometry.defaults(),
                settings.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private HydrologyPlannerSettings withUndergroundSources(
            HydrologyPlannerSettings settings,
            HydrologyPlannerSettings.Source sources
    ) {
        HydrologyPlannerSettings.Underground underground = settings.underground();
        return new HydrologyPlannerSettings(
                settings.seaLevel(),
                settings.routing(),
                settings.surface(),
                settings.hydraulics(),
                HydrologyPlannerSettings.Underground.of(
                        true,
                        sources,
                        underground.minimumFluidY(),
                        underground.maximumFluidY(),
                        underground.minimumWidth(),
                        underground.maximumWidth(),
                        underground.minimumDepth(),
                        underground.maximumDepth(),
                        underground.minimumHeadroom(),
                        underground.maximumHeadroom(),
                        underground.connectToExistingCaves(),
                        0
                ),
                settings.outlets(),
                HydrologyPlannerSettings.Geometry.defaults(),
                settings.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private HydrologyPlannerSettings withUndergroundFluidRange(
            HydrologyPlannerSettings settings,
            int minimumFluidY,
            int maximumFluidY
    ) {
        HydrologyPlannerSettings.Underground underground = settings.underground();
        return new HydrologyPlannerSettings(
                settings.seaLevel(),
                settings.routing(),
                settings.surface(),
                settings.hydraulics(),
                HydrologyPlannerSettings.Underground.of(
                        underground.enabled(),
                        underground.sources(),
                        minimumFluidY,
                        maximumFluidY,
                        underground.minimumWidth(),
                        underground.maximumWidth(),
                        underground.minimumDepth(),
                        underground.maximumDepth(),
                        underground.minimumHeadroom(),
                        underground.maximumHeadroom(),
                        underground.connectToExistingCaves(),
                        0
                ),
                settings.outlets(),
                HydrologyPlannerSettings.Geometry.defaults(),
                settings.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private HydrologyPlannerSettings withUndergroundWidth(
            HydrologyPlannerSettings settings,
            int width
    ) {
        HydrologyPlannerSettings.Underground underground = settings.underground();
        return new HydrologyPlannerSettings(
                settings.seaLevel(),
                settings.routing(),
                settings.surface(),
                settings.hydraulics(),
                HydrologyPlannerSettings.Underground.of(
                        underground.enabled(),
                        underground.sources(),
                        underground.minimumFluidY(),
                        underground.maximumFluidY(),
                        width,
                        width,
                        underground.minimumDepth(),
                        underground.maximumDepth(),
                        underground.minimumHeadroom(),
                        underground.maximumHeadroom(),
                        underground.connectToExistingCaves(),
                        0
                ),
                settings.outlets(),
                HydrologyPlannerSettings.Geometry.defaults(),
                settings.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private HydrologyPlanner plannerWithRoutingSampler(
            long seed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler terrain,
            AtomicInteger samples
    ) {
        HydrologyTerrainSampler countingTerrain = (int x, int z) -> {
            samples.incrementAndGet();
            return terrain.sample(x, z);
        };
        HydrologyRoutingTerrainSampler routingTerrain = new HydrologyRoutingTerrainSampler() {
            @Override
            public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
                HydrologyTerrainSample[] grid = new HydrologyTerrainSample[Math.multiplyExact(
                        request.width(),
                        request.width()
                )];
                for (int gridZ = 0; gridZ < request.width(); gridZ++) {
                    int z = request.minimumZ() + gridZ * request.spacing();
                    for (int gridX = 0; gridX < request.width(); gridX++) {
                        int x = request.minimumX() + gridX * request.spacing();
                        grid[gridZ * request.width() + gridX] = terrain.sample(x, z);
                    }
                }
                return grid;
            }

            @Override
            public NaturalClassification classifyNatural(int blockX, int blockZ) {
                HydrologyTerrainSample sample = terrain.sample(blockX, blockZ);
                return sample.ocean() ? NaturalClassification.OCEAN : NaturalClassification.LAND;
            }
        };
        return new HydrologyPlanner(
                seed,
                settings,
                countingTerrain,
                routingTerrain,
                request -> request.minimum(),
                -4096,
                footprint -> solidCaveView()
        );
    }

    private HydrologyPlannerSettings withOutlets(
            HydrologyPlannerSettings settings,
            HydrologyPlannerSettings.Outlets outlets
    ) {
        return new HydrologyPlannerSettings(
                settings.seaLevel(),
                settings.routing(),
                settings.surface(),
                settings.hydraulics(),
                settings.underground(),
                outlets,
                HydrologyPlannerSettings.Geometry.defaults(),
                settings.deepFluids(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled()
        );
    }

    private HydrologyColumnLayer layerForSegment(HydrologyColumnSample column, long segmentId) {
        for (HydrologyColumnLayer layer : column.layers()) {
            if (layer.feature().segmentId() == segmentId) {
                return layer;
            }
        }
        throw new AssertionError("Missing footprint layer for segment " + segmentId + ".");
    }

    private HydrologyColumnLayer layerForSegmentOrNull(HydrologyColumnSample column, long segmentId) {
        for (HydrologyColumnLayer layer : column.layers()) {
            if (layer.feature().segmentId() == segmentId) {
                return layer;
            }
        }
        return null;
    }

    private boolean hasNonCollinearInterior(List<HydrologyPoint> points) {
        if (points.size() < 3) {
            return false;
        }
        HydrologyPoint start = points.getFirst();
        HydrologyPoint end = points.getLast();
        long deltaX = end.x() - start.x();
        long deltaZ = end.z() - start.z();
        for (int index = 1; index < points.size() - 1; index++) {
            HydrologyPoint point = points.get(index);
            long localX = point.x() - start.x();
            long localZ = point.z() - start.z();
            if (deltaX * localZ - deltaZ * localX != 0L) {
                return true;
            }
        }
        return false;
    }

    private double maximumChordDeviationRatio(List<HydrologyPoint> points) {
        if (points.size() < 3) {
            return 0D;
        }
        HydrologyPoint start = points.getFirst();
        HydrologyPoint end = points.getLast();
        double chordX = end.x() - start.x();
        double chordZ = end.z() - start.z();
        double chordLength = StrictMath.hypot(chordX, chordZ);
        if (chordLength == 0D) {
            return 0D;
        }
        double maximumDeviation = 0D;
        for (int index = 1; index < points.size() - 1; index++) {
            HydrologyPoint point = points.get(index);
            double localX = point.x() - start.x();
            double localZ = point.z() - start.z();
            double deviation = StrictMath.abs(chordX * localZ - chordZ * localX) / chordLength;
            maximumDeviation = Math.max(maximumDeviation, deviation);
        }
        return maximumDeviation / chordLength;
    }

    private double maximumQuantizedStickLength(List<HydrologyPoint> points) {
        int previousDirection = 0;
        double currentLength = 0D;
        double maximumLength = 0D;
        for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
            HydrologyPoint previous = points.get(pointIndex - 1);
            HydrologyPoint point = points.get(pointIndex);
            int direction = quantizedStickDirection(point.x() - previous.x(), point.z() - previous.z());
            double length = StrictMath.hypot(point.x() - previous.x(), point.z() - previous.z());
            if (direction == 0) {
                previousDirection = 0;
                currentLength = 0D;
                continue;
            }
            currentLength = direction == previousDirection ? currentLength + length : length;
            previousDirection = direction;
            maximumLength = Math.max(maximumLength, currentLength);
        }
        return maximumLength;
    }

    private int quantizedStickDirection(int deltaX, int deltaZ) {
        if (deltaX == 0 && deltaZ != 0) {
            return deltaZ > 0 ? 1 : 2;
        }
        if (deltaZ == 0 && deltaX != 0) {
            return deltaX > 0 ? 3 : 4;
        }
        if (Math.abs(deltaX) != Math.abs(deltaZ) || deltaX == 0) {
            return 0;
        }
        if (deltaX > 0) {
            return deltaZ > 0 ? 5 : 6;
        }
        return deltaZ > 0 ? 7 : 8;
    }

    private boolean hasRotationalAsymmetry(
            HydrologyTile tile,
            HydraulicSegment segment,
            int radius
    ) {
        for (int deltaZ = -radius; deltaZ <= radius; deltaZ++) {
            for (int deltaX = -radius; deltaX <= radius; deltaX++) {
                boolean present = segmentPresent(tile, segment, deltaX, deltaZ);
                boolean rotated = segmentPresent(tile, segment, -deltaZ, deltaX);
                if (present != rotated) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean segmentPresent(
            HydrologyTile tile,
            HydraulicSegment segment,
            int deltaX,
            int deltaZ
    ) {
        HydrologyColumnSample column = tile.columnAt(
                segment.start().x() + deltaX,
                segment.start().z() + deltaZ
        ).orElse(null);
        return column != null && layerForSegmentOrNull(column, segment.id()) != null;
    }

    private HydraulicSegment segment(HydrologyTile tile, long segmentId) {
        for (RiverCourse course : tile.courses()) {
            for (HydraulicSegment segment : course.segments()) {
                if (segment.id() == segmentId) {
                    return segment;
                }
            }
        }
        throw new AssertionError("Missing hydraulic segment " + segmentId + ".");
    }

    private double distanceToCenterline(HydrologyPoint point, List<HydrologyPoint> centerline) {
        double minimum = Double.POSITIVE_INFINITY;
        for (HydrologyPoint center : centerline) {
            minimum = Math.min(minimum, StrictMath.hypot(point.x() - center.x(), point.z() - center.z()));
        }
        return minimum;
    }

    private List<RiverCourse> surfaceCourses(HydrologyTile tile) {
        return courses(tile, RiverCourseType.SURFACE);
    }

    private List<RiverCourse> courses(HydrologyTile tile, RiverCourseType type) {
        ArrayList<RiverCourse> selected = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == type) {
                selected.add(course);
            }
        }
        return List.copyOf(selected);
    }

    private boolean hasAny(HydrologyTile tile, HydrologyFeatureType... types) {
        Set<HydrologyFeatureType> expected = new HashSet<>(List.of(types));
        for (RiverCourse course : tile.courses()) {
            for (HydraulicSegment segment : course.segments()) {
                if (expected.contains(segment.type())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<HydraulicSegment> segments(HydrologyTile tile, HydrologyFeatureType type) {
        ArrayList<HydraulicSegment> selected = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            for (HydraulicSegment segment : course.segments()) {
                if (segment.type() == type) {
                    selected.add(segment);
                }
            }
        }
        return List.copyOf(selected);
    }

    private int firstSegment(RiverCourse course, HydrologyFeatureType... types) {
        Set<HydrologyFeatureType> expected = new HashSet<>(List.of(types));
        for (int index = 0; index < course.segments().size(); index++) {
            if (expected.contains(course.segments().get(index).type())) {
                return index;
            }
        }
        return -1;
    }

    private boolean bruteForceUndergroundHeads(
            int[] minimum,
            int[] maximum,
            int outlet,
            int index,
            int upstreamHead
    ) {
        if (index == minimum.length - 1) {
            return outlet >= minimum[index]
                    && outlet <= maximum[index]
                    && outlet <= upstreamHead;
        }
        int upper = Math.min(maximum[index], upstreamHead);
        for (int head = minimum[index]; head <= upper; head++) {
            if (bruteForceUndergroundHeads(minimum, maximum, outlet, index + 1, head)) {
                return true;
            }
        }
        return false;
    }
    @SuppressWarnings("unchecked")
    private HydrologyTile planWithSurfaceRouteMemos(HydrologyPlanner planner, SurfaceRouteMemos memos) throws Exception {
        Class<?> samplesClass = Class.forName(HydrologyPlanner.class.getName() + "$PlanningSamples");
        Constructor<?> constructor = samplesClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object samples = constructor.newInstance();
        Field routes = samplesClass.getDeclaredField("surfaceRoutes");
        routes.setAccessible(true);
        routes.set(samples, memos);
        Field scope = HydrologyPlanner.class.getDeclaredField("planningSamples");
        scope.setAccessible(true);
        ThreadLocal<Object> local = (ThreadLocal<Object>) scope.get(planner);
        local.set(samples);
        try {
            return planner.plan(TILE);
        } finally {
            local.remove();
        }
    }

    private static final class SurfaceRouteMemos extends IdentityHashMap<Object, HashMap<Object, List<HydrologyPoint>>> {
        private final boolean retain;
        private int hits;
        private int computations;

        private SurfaceRouteMemos(boolean retain) {
            this.retain = retain;
        }

        @Override
        public HashMap<Object, List<HydrologyPoint>> computeIfAbsent(Object key,
                Function<? super Object, ? extends HashMap<Object, List<HydrologyPoint>>> ignored) {
            return super.computeIfAbsent(key, unused -> new HashMap<>() {
                @Override
                public List<HydrologyPoint> computeIfAbsent(Object route,
                        Function<? super Object, ? extends List<HydrologyPoint>> computation) {
                    if (!retain) {
                        clear();
                    }
                    if (containsKey(route)) {
                        hits++;
                    }
                    return super.computeIfAbsent(route, missing -> {
                        computations++;
                        return computation.apply(missing);
                    });
                }
            });
        }
    }

    private HydrologyPlanner countedPlanner(CountingNaturalSampler sampler) {
        return new HydrologyPlanner(91L, standardSettings(4D, 0D, true, false, List.of()),
                sampler.delegate, sampler, HydrologyGeometrySampler.deterministic(sampler.delegate),
                -4096, footprint -> solidCaveView());
    }

    private double anchorScore(HydrologyPlanner planner, HydrologyTerrainSample terrain, int x, int z) throws Exception {
        Class<?> nodeType = HydrologyGridNode.class;
        Constructor<?> constructor = nodeType.getDeclaredConstructor(int.class, int.class, int.class,
                int.class, int.class, long.class, HydrologyTerrainSample.class);
        constructor.setAccessible(true);
        Object node = constructor.newInstance(0, 0, 0, 0, 0, 719L, terrain);
        Method method = HydrologyRouteGeometry.class.getDeclaredMethod("anchorScore", nodeType,
                int.class, int.class, int.class, int.class, double.class);
        method.setAccessible(true);
        return (double) method.invoke(planner.routeGeometry, node, x, z, 0, 0, 0.3D);
    }

    private List<?> routeCandidates(HydrologyPlanner planner) throws Exception {
        Class<?> directionType = RouteDirection.class;
        Constructor<?> directionConstructor = directionType.getDeclaredConstructor(double.class, double.class);
        directionConstructor.setAccessible(true);
        Object direction = directionConstructor.newInstance(1D, 0D);
        Class<?> positionType = RoutePosition.class;
        Constructor<?> positionConstructor = positionType.getDeclaredConstructor(double.class, double.class,
                double.class, double.class, directionType);
        positionConstructor.setAccessible(true);
        Object position = positionConstructor.newInstance(0D, 0D, 100D, 0D, direction);
        Method method = HydrologyRouteGeometry.class.getDeclaredMethod("routeCandidates", long.class, long.class,
                positionType, double.class, int.class, int.class, String.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(planner.routeGeometry, 1L, 2L, position, 0.5D, 4, 1, null);
    }

    private static final class CountingNaturalSampler implements HydrologyNaturalTerrainSampler {
        private final HydrologyTerrainSampler delegate;
        private int basisCalls;
        private int slopeCalls;

        private CountingNaturalSampler(HydrologyTerrainSampler delegate) {
            this.delegate = delegate;
        }

        @Override
        public HydrologyTerrainSample sampleBasis(int x, int z) {
            slopeCalls++;
            return delegate.sample(x, z);
        }

        @Override
        public HydrologyTerrainSample sampleBasisWithoutSlope(int x, int z) {
            basisCalls++;
            HydrologyTerrainSample sample = delegate.sample(x, z);
            return sample == null ? null : sample.withSlope(0D);
        }

        @Override
        public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
            throw new AssertionError("Point sampling fixture does not request a grid.");
        }

        @Override
        public NaturalClassification classifyNatural(int x, int z) {
            HydrologyTerrainSample sample = delegate.sample(x, z);
            return sample == null ? NaturalClassification.UNAVAILABLE
                    : sample.ocean() ? NaturalClassification.OCEAN : NaturalClassification.LAND;
        }
    }

}
