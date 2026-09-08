package art.arcane.iris.engine.history;

import art.arcane.iris.engine.IrisEngine;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class GenerationHistoryRuntimeRouterLifecycleTest extends GenerationHistoryRuntimeRouterSupport {
    @Test
    public void concurrentHistoricalRoutesShareTheCurrentRuntimeWithoutLoadingOldFactories() throws Exception {
        Path world = temporaryFolder.newFolder("router-deduplicated-load-world").toPath();
        GenerationHistory history = createThreeActivationHistory(world, "router-deduplicated-load");
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding active = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(active);
        installScopeTracking(engine);
        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine,
                history,
                (ignored, blockX, blockZ) -> signature(blockX, blockZ),
                runtimes
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            CompletableFuture<GenerationHistoryRuntimeRouter.RuntimeRoute> first = openRouteAsync(
                    router, executor, ready, start, 0, 0);
            CompletableFuture<GenerationHistoryRuntimeRouter.RuntimeRoute> second = openRouteAsync(
                    router, executor, ready, start, 0, 0);
            assertTrue(ready.await(5L, TimeUnit.SECONDS));
            start.countDown();
            GenerationHistoryRuntimeRouter.RuntimeRoute firstRoute = first.get(5L, TimeUnit.SECONDS);
            GenerationHistoryRuntimeRouter.RuntimeRoute secondRoute = second.get(5L, TimeUnit.SECONDS);

            assertEquals(0, runtimes.loadCount(1L));
            assertEquals(3L, firstRoute.activation().activationId());
            assertEquals(3L, secondRoute.activation().activationId());
            firstRoute.close();
            secondRoute.close();
        } finally {
            executor.shutdownNow();
        }
        router.close();
    }

    @Test
    public void closeRejectsNewRoutesAndAwaitsExistingLeases() throws Exception {
        Path world = temporaryFolder.newFolder("router-close-await-world").toPath();
        GenerationHistory history = createThreeActivationHistory(world, "router-close-await");
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding active = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(active);
        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes);
        GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(0, 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> closing = CompletableFuture.runAsync(router::close, executor);
            awaitClosed(router);
            assertThrows(IllegalStateException.class, () -> router.openRoute(2, 0));
            assertThrows(TimeoutException.class, () -> closing.get(100L, TimeUnit.MILLISECONDS));
            route.close();
            closing.get(5L, TimeUnit.SECONDS);
        } finally {
            route.close();
            executor.shutdownNow();
        }
        verify(engine).detachGenerationHistoryRuntimeRouter(router);
        verify(engine, never()).closeDetachedGenerationRuntime(active);
    }

    @Test
    public void closeCompletesWhileWorkersContinuouslyAcquireReadyRoutes() throws Exception {
        Path world = temporaryFolder.newFolder("router-contended-close-world").toPath();
        GenerationHistory history = createHistory(world, createPack("router-contended-close-pack", "alpha"));
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding active = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(active);
        ExecutorService executor = Executors.newFixedThreadPool(9);
        CountDownLatch routing = new CountDownLatch(8);
        CompletableFuture<?>[] workers = new CompletableFuture<?>[8];
        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes);
        try {
            for (int i = 0; i < workers.length; i++) {
                workers[i] = CompletableFuture.runAsync(() -> routeUntilClosed(router, routing), executor);
            }
            assertTrue(routing.await(5, TimeUnit.SECONDS));
            CompletableFuture.runAsync(router::close, executor).get(5, TimeUnit.SECONDS);
            CompletableFuture.allOf(workers).get(5, TimeUnit.SECONDS);
            verify(engine).detachGenerationHistoryRuntimeRouter(router);
            verify(engine, never()).closeDetachedGenerationRuntime(active);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            router.close();
        }
    }

    @Test
    public void closeLeavesTheSingleDefaultRuntimeOwnedByTheEngine() throws Exception {
        Path world = temporaryFolder.newFolder("router-close-current-world").toPath();
        GenerationHistory history = createThreeActivationHistory(world, "router-close-current");
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding initial = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(initial);
        installScopeTracking(engine);
        try (GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes)) {
            try (GenerationHistoryRuntimeRouter.RuntimeStage ignored = router.openStage(0, 0)) {
            }
            try (GenerationHistoryRuntimeRouter.RuntimeStage ignored = router.openStage(1, 0)) {
            }
        }
        assertEquals(Set.of(3L), runtimes.bindings.keySet());
        verify(engine, never()).closeDetachedGenerationRuntime(any());
    }

    @Test
    public void runtimeRouteScopesWorkAndCloseAcrossAsyncThreads() throws Exception {
        Path world = temporaryFolder.newFolder("router-async-world").toPath();
        Path pack = createPack("router-async-pack", "alpha");
        GenerationHistory history = createHistory(world, pack);
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding first = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(first);
        AtomicReference<IrisEngine.GenerationRuntimeBinding> scoped = installScopeTracking(engine);
        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine,
                history,
                (ignored, blockX, blockZ) -> signature(blockX, blockZ),
                runtimes
        );
        GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(-1, -2);
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> completionThread = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> work = CompletableFuture.runAsync(() -> {
                try (GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope ignored = route.openRuntimeScope()) {
                    assertSame(first, scoped.get());
                    assertThrows(IllegalStateException.class, route::close);
                }
            }, executor);
            CompletableFuture<Void> completion = work.whenCompleteAsync((ignored, failure) -> {
                completionThread.set(Thread.currentThread());
                route.close();
            }, executor);
            completion.get(5L, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertNotSame(caller, completionThread.get());
        assertEquals(-1, route.chunkX());
        assertEquals(-2, route.chunkZ());
        assertThrows(IllegalStateException.class, route::openRuntimeScope);
        router.close();
    }

    @Test
    public void liveStudioPromotionPreservesChunksAndRoutesExpansionAcrossRepeatedUpdates() throws Exception {
        Path world = temporaryFolder.newFolder("live-studio-world").toPath();
        Path packA = createPack("live-studio-a", "flat-lowland");
        Path packB = createPack("live-studio-b", "massive-mountains");
        Path packC = createPack("live-studio-c", "deep-ocean");
        GenerationHistory history = createHistory(world, packA);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{0, 0}});
        byte[] original = Files.readAllBytes(region);
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.isStudio()).thenReturn(true);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding first = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(first);
        AtomicReference<IrisEngine.GenerationRuntimeBinding> scoped = installScopeTracking(engine);
        try (GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes)) {
            try (GenerationHistoryRuntimeRouter.RuntimeStage stage = router.openStage(0, 0)) {
                assertEquals(1L, stage.activation().activationId());
            }
            stage(history, packB);
            try (GenerationHistoryRuntimeRouter.StudioCutover cutover = router.beginStudioCutover(5_000L)) {
                assertEquals(2L, cutover.promotePending().activationId());
            }
            assertArrayEquals(original, Files.readAllBytes(region));
            try (GenerationHistoryRuntimeRouter.RuntimeStage stage = router.openStage(0, 0)) {
                assertSame(runtimes.bindings.get(2L), scoped.get());
                assertEquals(2L, stage.activation().activationId());
            }
            try (GenerationHistoryRuntimeRouter.RuntimeStage stage = router.openStage(1, 0)) {
                assertSame(runtimes.bindings.get(2L), scoped.get());
                assertEquals(2L, stage.activation().activationId());
            }
            writeRegion(region, new int[][]{{0, 0}, {1, 0}});
            byte[] expanded = Files.readAllBytes(region);
            stage(history, packC);
            try (GenerationHistoryRuntimeRouter.StudioCutover cutover = router.beginStudioCutover(5_000L)) {
                assertEquals(3L, cutover.promotePending().activationId());
            }
            assertArrayEquals(expanded, Files.readAllBytes(region));
            assertEquals(1L, history.resolveActivation(0, 0).activationId());
            assertEquals(2L, history.resolveActivation(1, 0).activationId());
            assertEquals(3L, history.resolveActivation(2, 0).activationId());
        }
        GenerationHistory reopened = GenerationHistory.open(world);
        assertEquals(1L, reopened.resolveActivation(0, 0).activationId());
        assertEquals(2L, reopened.resolveActivation(1, 0).activationId());
        assertEquals(3L, reopened.resolveActivation(2, 0).activationId());
    }

    @Test
    public void liveStudioCutoverDrainsExistingRoutesAndBlocksNewRoutesUntilPublicationCompletes() throws Exception {
        Path world = temporaryFolder.newFolder("studio-drain-world").toPath();
        Path pack = createPack("studio-drain-pack", "alpha");
        GenerationHistory history = createHistory(world, pack);
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.isStudio()).thenReturn(true);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding initialBinding = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(initialBinding);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch cutoverRequested = new CountDownLatch(1);
        CountDownLatch cutoverAcquired = new CountDownLatch(1);
        CountDownLatch releaseCutover = new CountDownLatch(1);
        try (GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes)) {
            GenerationHistoryRuntimeRouter.RuntimeRoute initial = router.openRoute(0, 0);
            try {
                CompletableFuture<Void> cutover = CompletableFuture.runAsync(() -> {
                    cutoverRequested.countDown();
                    try (GenerationHistoryRuntimeRouter.StudioCutover ignored = router.beginStudioCutover(5_000L)) {
                        cutoverAcquired.countDown();
                        if (!releaseCutover.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to release the Studio cutover.");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                }, executor);
                assertTrue(cutoverRequested.await(5, TimeUnit.SECONDS));
                assertFalse(cutoverAcquired.await(100, TimeUnit.MILLISECONDS));
                initial.close();
                assertTrue(cutoverAcquired.await(5, TimeUnit.SECONDS));
                CompletableFuture<GenerationHistoryRuntimeRouter.RuntimeRoute> waiting =
                        CompletableFuture.supplyAsync(() -> openRouteUnchecked(router, 1, 0), executor);
                assertThrows(TimeoutException.class, () -> waiting.get(100, TimeUnit.MILLISECONDS));
                releaseCutover.countDown();
                cutover.get(5, TimeUnit.SECONDS);
                try (GenerationHistoryRuntimeRouter.RuntimeRoute route = waiting.get(5, TimeUnit.SECONDS)) {
                    assertEquals(1L, route.activation().activationId());
                }
            } finally {
                initial.close();
                releaseCutover.countDown();
            }
        } finally {
            releaseCutover.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void liveCutoverRejectsProductionWorldsAndNestedGenerationScopes() throws Exception {
        Path world = temporaryFolder.newFolder("studio-guard-world").toPath();
        Path pack = createPack("studio-guard-pack", "alpha");
        GenerationHistory history = createHistory(world, pack);
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding initialBinding = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(initialBinding);
        installScopeTracking(engine);
        try (GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes)) {
            assertThrows(IllegalStateException.class, () -> router.beginStudioCutover(5_000L));
            when(engine.isStudio()).thenReturn(true);
            try (GenerationHistoryRuntimeRouter.RuntimeStage ignored = router.openStage(0, 0)) {
                assertThrows(IllegalStateException.class, () -> router.beginStudioCutover(5_000L));
            }
            try (GenerationHistoryRuntimeRouter.StudioCutover cutover = router.beginStudioCutover(5_000L)) {
                assertThrows(IllegalStateException.class, () -> router.beginStudioCutover(5_000L));
                assertEquals(1L, cutover.promotePending().activationId());
            }
        }
    }
}
