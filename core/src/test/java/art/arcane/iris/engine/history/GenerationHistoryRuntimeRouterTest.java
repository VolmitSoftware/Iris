package art.arcane.iris.engine.history;

import art.arcane.iris.core.pack.AtomicDirectoryPublisher;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.framework.GenerationSessionManager;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.Rule;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class GenerationHistoryRuntimeRouterTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void initializeMantleBlockState() throws Exception {
        PlatformBlockState air = mock(PlatformBlockState.class);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("AIR")).thenReturn(air);
            Class.forName(EngineMantle.class.getName());
        }
    }

    @Test
    public void promotionSamplesSavedBoundaryWithoutOpeningHistoricalRuntimeScopes() throws Exception {
        Path world = temporaryFolder.newFolder("router-promotion-world").toPath();
        Path packA = createPack("router-promotion-a", "alpha");
        Path packB = createPack("router-promotion-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        writeRegion(Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca"),
                new int[][]{{0, 0}});
        stage(history, packB);
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding first = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(first);
        AtomicReference<IrisEngine.GenerationRuntimeBinding> scoped = installScopeTracking(engine);
        int[] samples = new int[1];
        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attachAndPromotePending(
                engine,
                history,
                (routedEngine, blockX, blockZ) -> {
                    assertSame(engine, routedEngine);
                    assertNull(scoped.get());
                    samples[0]++;
                    return signature(blockX, blockZ);
                },
                256,
                runtimes
        );

        GenerationActivation activated = history.activeActivation();
        IrisEngine.GenerationRuntimeBinding second = runtimes.bindings.get(2L);

        assertEquals(2L, activated.activationId());
        assertEquals(history.boundary(2L).exposedBlockColumns().size(), samples[0]);
        verify(engine).setDefaultGenerationRuntime(second);
        try (GenerationHistoryRuntimeRouter.RuntimeStage ignored = router.openStage(0, 0)) {
            assertSame(second, scoped.get());
        }
        try (GenerationHistoryRuntimeRouter.RuntimeStage ignored = router.openStage(1, 0)) {
            assertSame(second, scoped.get());
        }
        router.close();
    }

    @Test
    public void repeatedPackActivationUsesOnlyItsCurrentMantleAndBinding() throws Exception {
        Path world = temporaryFolder.newFolder("router-repeat-world").toPath();
        Path packA = createPack("router-repeat-a", "alpha");
        Path packB = createPack("router-repeat-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{0, 0}});
        stage(history, packB);
        promoteWithSignatures(history);
        writeRegion(region, new int[][]{{0, 0}, {1, 0}});
        stage(history, packA);
        GenerationActivation third = promoteWithSignatures(history);
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding current = runtimes.binding(history, third);
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(current);
        AtomicReference<IrisEngine.GenerationRuntimeBinding> scoped = installScopeTracking(engine);
        try (GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes)) {
            for (int chunkX = 0; chunkX < 3; chunkX++) {
                try (GenerationHistoryRuntimeRouter.RuntimeStage stage = router.openStage(chunkX, 0)) {
                    assertSame(current, scoped.get());
                    assertEquals(3L, stage.activation().activationId());
                }
            }
            assertEquals(history.manifest().activation(1L).orElseThrow().epochId(), third.epochId());
            assertEquals(Set.of(3L), runtimes.bindings.keySet());
            assertEquals(history.paths().activationMantleRoot(3L), current.mantleStorageDirectory());
            assertEquals(1L, history.resolveActivation(0, 0).activationId());
            assertEquals(2L, history.resolveActivation(1, 0).activationId());
        }
    }

    @Test
    public void preloadAndRoutesDoNotConstructHistoricalBindings() throws Exception {
        Path world = temporaryFolder.newFolder("router-lazy-world").toPath();
        Path packA = createPack("router-lazy-a", "alpha");
        Path packB = createPack("router-lazy-b", "beta");
        Path packC = createPack("router-lazy-c", "gamma");
        GenerationHistory history = createHistory(world, packA);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{0, 0}});
        stage(history, packB);
        promoteWithSignatures(history);
        writeRegion(region, new int[][]{{0, 0}, {1, 0}});
        stage(history, packC);
        promoteWithSignatures(history);
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

        router.preloadActiveRuntimes();

        assertEquals(Set.of(3L), runtimes.bindings.keySet());
        try (GenerationHistoryRuntimeRouter.RuntimeStage ignored = router.openStage(0, 0)) {
            assertEquals(Set.of(3L), runtimes.bindings.keySet());
        }
        try (GenerationHistoryRuntimeRouter.RuntimeStage ignored = router.openStage(1, 0)) {
            assertEquals(Set.of(3L), runtimes.bindings.keySet());
        }
        router.close();
    }

    @Test
    public void historicalTerrainRoutesWorkWhenFrozenPackFixturesAreMissing() throws Exception {
        Path world = temporaryFolder.newFolder("router-archived-world").toPath();
        GenerationHistory history = createThreeActivationHistory(world, "router-archived");
        for (long activationId : List.of(1L, 2L)) {
            Path archivedPack = history.paths().packRoot(history.manifest().activation(activationId).orElseThrow().epochId());
            assertTrue(Files.isDirectory(archivedPack));
            AtomicDirectoryPublisher.deleteTree(archivedPack);
        }
        assertFalse(Files.exists(history.paths().packRoot(history.manifest().activation(1L).orElseThrow().epochId())));
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding current = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(current);
        try (GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes)) {
            for (int chunkX = 0; chunkX < 3; chunkX++) {
                try (GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(chunkX, 0)) {
                    assertEquals(3L, route.activation().activationId());
                }
            }
            assertEquals(Set.of(3L), runtimes.bindings.keySet());
            assertEquals(0, runtimes.loadCount(1L));
            assertEquals(0, runtimes.loadCount(2L));
        }
        assertEquals(history.manifest(), GenerationHistory.open(world).manifest());
    }

    @Test
    public void closeWaitsForAnInFlightPromotionLoad() throws Exception {
        Path world = temporaryFolder.newFolder("router-promotion-close-world").toPath();
        GenerationHistory history = createHistory(world, createPack("router-promotion-close-a", "alpha"));
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.isStudio()).thenReturn(true);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding initial = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(initial);
        stage(history, createPack("router-promotion-close-b", "beta"));
        runtimes.blockLoad(2L);
        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> promoting = CompletableFuture.runAsync(() -> {
                try (GenerationHistoryRuntimeRouter.StudioCutover cutover = router.beginStudioCutover(5_000L)) {
                    cutover.promotePending();
                } catch (IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }, executor);
            assertTrue(runtimes.loadStarted.await(5L, TimeUnit.SECONDS));
            CompletableFuture<Void> closing = CompletableFuture.runAsync(router::close, executor);
            awaitClosed(router);
            assertThrows(TimeoutException.class, () -> closing.get(100L, TimeUnit.MILLISECONDS));
            runtimes.releaseLoad.countDown();
            promoting.get(5L, TimeUnit.SECONDS);
            closing.get(5L, TimeUnit.SECONDS);
            assertEquals(1, runtimes.loadCount(2L));
            verify(engine).detachGenerationHistoryRuntimeRouter(router);
        } finally {
            runtimes.releaseLoad.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void failedOutgoingRuntimeRetirementReportsTheCommittedCutoverFailure() throws Exception {
        Path world = temporaryFolder.newFolder("router-retirement-failure-world").toPath();
        GenerationHistory history = createHistory(world, createPack("router-retirement-failure-a", "alpha"));
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding outgoing = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(outgoing);
        stage(history, createPack("router-retirement-failure-b", "beta"));
        doAnswer(invocation -> {
            throw new IllegalStateException("Mantle did not close");
        }).when(engine).closeDetachedGenerationRuntime(outgoing);
        assertThrows(IllegalStateException.class, () -> GenerationHistoryRuntimeRouter.attachAndPromotePending(
                engine, history, (ignored, x, z) -> signature(x, z), 256, runtimes));
        assertEquals(2L, history.activeActivation().activationId());
        verify(engine).setDefaultGenerationRuntime(runtimes.bindings.get(2L));
        assertEquals(0, runtimes.loadCount(1L));
    }

    @Test
    public void routeLeaseSurvivesNestedScopesUntilRouteCloses() throws Exception {
        Path world = temporaryFolder.newFolder("router-nested-lease-world").toPath();
        GenerationHistory history = createThreeActivationHistory(world, "router-nested-lease");
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
        GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(0, 0);

        try (GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope outer = route.openRuntimeScope();
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope inner = route.openRuntimeScope()) {
            assertThrows(IllegalStateException.class, route::close);
            verify(engine, never()).closeDetachedGenerationRuntime(active);
        }
        verify(engine, never()).closeDetachedGenerationRuntime(active);

        route.close();

        verify(engine, never()).closeDetachedGenerationRuntime(active);
        router.close();
    }

    @Test
    public void stageCloseReleasesItsRouteWhenRuntimeScopeCloseFails() throws Exception {
        Path world = temporaryFolder.newFolder("router-scope-close-failure-world").toPath();
        Path pack = createPack("router-scope-close-failure-pack", "alpha");
        GenerationHistory history = createHistory(world, pack);
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding active = runtimes.binding(history, history.activeActivation());
        IrisEngine.GenerationRuntimeScope runtimeScope = mock(IrisEngine.GenerationRuntimeScope.class);
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(active);
        when(engine.openGenerationRuntimeScope(active)).thenReturn(runtimeScope);
        doAnswer(ignored -> {
            throw new IllegalStateException("scope close failed");
        }).when(runtimeScope).close();
        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine,
                history,
                (ignored, blockX, blockZ) -> signature(blockX, blockZ),
                runtimes
        );
        GenerationHistoryRuntimeRouter.RuntimeStage stage = router.openStage(0, 0);

        IllegalStateException failure = assertThrows(IllegalStateException.class, stage::close);

        assertEquals("scope close failed", failure.getMessage());
        router.close();
        verify(engine).detachGenerationHistoryRuntimeRouter(router);
    }

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
        try (GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes)) {
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
    public void startupPromotionPinsNewDefaultAndRetiresOldDefault() throws Exception {
        Path world = temporaryFolder.newFolder("router-promotion-lease-world").toPath();
        Path packA = createPack("router-promotion-lease-a", "alpha");
        Path packB = createPack("router-promotion-lease-b", "beta");
        GenerationHistory history = createHistory(world, packA);
        writeRegion(Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca"),
                new int[][]{{0, 0}});
        stage(history, packB);
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding initial = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(initial);
        installScopeTracking(engine);
        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attachAndPromotePending(
                engine,
                history,
                (ignored, blockX, blockZ) -> signature(blockX, blockZ),
                256,
                runtimes
        );

        assertEquals(2L, history.activeActivation().activationId());
        verify(engine).setDefaultGenerationRuntime(runtimes.bindings.get(2L));
        verify(engine).closeDetachedGenerationRuntime(initial);
        router.close();
    }

    @Test
    public void attachAndLoadedRuntimeRejectKernelMismatches() throws Exception {
        Path baseWorld = temporaryFolder.newFolder("router-base-kernel-world").toPath();
        Path basePack = createPack("router-base-kernel-pack", "alpha");
        GenerationHistory baseHistory = createHistory(baseWorld, basePack);
        IrisEngine baseEngine = mock(IrisEngine.class);
        FakeRuntimeFactory baseRuntimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding mismatchedBase = baseRuntimes.binding(
                baseHistory,
                baseHistory.activeActivation(),
                new GenerationKernelRegistry.Version(2, 1, 1)
        );
        when(baseEngine.getActiveGenerationRuntimeBinding()).thenReturn(mismatchedBase);

        assertThrows(
                IOException.class,
                () -> GenerationHistoryRuntimeRouter.attach(
                        baseEngine,
                        baseHistory,
                        (ignored, blockX, blockZ) -> signature(blockX, blockZ),
                        baseRuntimes
                )
        );
        verify(baseEngine, never()).attachGenerationHistoryRuntimeRouter(any());

        Path loadedWorld = temporaryFolder.newFolder("router-loaded-kernel-world").toPath();
        Path loadedPackA = createPack("router-loaded-kernel-a", "alpha");
        Path loadedPackB = createPack("router-loaded-kernel-b", "beta");
        GenerationHistory loadedHistory = createHistory(loadedWorld, loadedPackA);
        stage(loadedHistory, loadedPackB);
        IrisEngine loadedEngine = mock(IrisEngine.class);
        FakeRuntimeFactory loadedRuntimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding correctBase = loadedRuntimes.binding(
                loadedHistory,
                loadedHistory.activeActivation()
        );
        when(loadedEngine.getActiveGenerationRuntimeBinding()).thenReturn(correctBase);
        installScopeTracking(loadedEngine);
        loadedRuntimes.loadedVersion = new GenerationKernelRegistry.Version(2, 1, 1);

        assertThrows(
                IOException.class,
                () -> GenerationHistoryRuntimeRouter.attachAndPromotePending(
                        loadedEngine,
                        loadedHistory,
                        (ignored, blockX, blockZ) -> signature(blockX, blockZ),
                        256,
                        loadedRuntimes
                )
        );
        verify(loadedEngine, never()).setDefaultGenerationRuntime(any());
        verify(loadedEngine).closeDetachedGenerationRuntime(loadedRuntimes.bindings.get(2L));
        assertEquals(2L, loadedHistory.activeActivation().activationId());
    }

    @Test
    public void coordinateScopesUseFloorDivAndCloseRejectsFurtherRoutes() throws Exception {
        Path world = temporaryFolder.newFolder("router-coordinate-world").toPath();
        Path pack = createPack("router-coordinate-pack", "alpha");
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

        try (GenerationHistoryRuntimeRouter.CoordinateScope scope = router.openCoordinateScope(-1, -17)) {
            assertEquals(-1, scope.blockX());
            assertEquals(-17, scope.blockZ());
            assertEquals(-1, scope.chunkX());
            assertEquals(-2, scope.chunkZ());
            assertEquals(1L, scope.activation().activationId());
            assertSame(first, scoped.get());
        }

        router.close();

        assertTrue(router.isClosed());
        verify(engine).detachGenerationHistoryRuntimeRouter(router);
        verify(engine, never()).closeDetachedGenerationRuntime(any());
        assertThrows(IllegalStateException.class, () -> router.openStage(-1, -2));
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
            CompletableFuture<Void> completion = work.whenComplete((ignored, failure) -> {
                completionThread.set(Thread.currentThread());
                route.close();
            });
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
    public void routeClaimsSealedSemanticsWhileItsRuntimeScopeIsOpen() throws Exception {
        Path world = temporaryFolder.newFolder("router-claim-world").toPath();
        Path pack = createPack("router-claim-pack", "alpha");
        GenerationHistory history = createHistory(world, pack);
        IrisEngine engine = mock(IrisEngine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey("alpha");
        IrisRegion region = new IrisRegion();
        region.setLoadKey("alpha");
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenReturn(biome);
        when(engine.getCaveBiome(anyInt(), anyInt())).thenReturn(biome);
        when(engine.getBiome(anyInt(), anyInt(), anyInt())).thenReturn(biome);
        when(engine.getBiomeOrMantle(anyInt(), anyInt(), anyInt())).thenReturn(biome);
        when(engine.getRegion(anyInt(), anyInt())).thenReturn(region);
        when(engine.getRegion(anyInt(), anyInt(), anyInt())).thenReturn(region);
        EngineMantle engineMantle = mock(EngineMantle.class);
        @SuppressWarnings("unchecked")
        Mantle<Matter> mantle = mock(Mantle.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getMantle()).thenReturn(mantle);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding first = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(first);
        installScopeTracking(engine);
        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine,
                history,
                (ignored, blockX, blockZ) -> signature(blockX, blockZ),
                runtimes
        );
        GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(4, -3);

        assertThrows(IllegalStateException.class, route::claimGeneratedSemantics);
        try (GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope ignored = route.openRuntimeScope()) {
            assertTrue(route.claimGeneratedSemantics());
            assertFalse(route.claimGeneratedSemantics());
        }
        route.close();

        ChunkGenerationSemantics semantics = history.semantics(4, -3).orElseThrow();
        assertTrue(semantics.sealed());
        assertEquals(1L, semantics.activationId());
        assertEquals("alpha", history.savedBiomes().get(4, -3).orElseThrow().biomeAt(0, 0, 0).biomeKey());
        router.close();
    }

    @Test
    public void coordinateScopeBorrowsMatchingRouteAndBypassesRawRuntimeScopes() throws Exception {
        Path world = temporaryFolder.newFolder("router-borrow-world").toPath();
        Path pack = createPack("router-borrow-pack", "alpha");
        GenerationHistory history = createHistory(world, pack);
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding first = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(first);
        installScopeTracking(engine);
        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine,
                history,
                (ignored, blockX, blockZ) -> signature(blockX, blockZ),
                runtimes
        );

        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(-1, -2);
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope ignored = route.openRuntimeScope()) {
            try (GenerationHistoryRuntimeRouter.CoordinateScope borrowed = router.openCoordinateScope(-1, -17)) {
                assertEquals(-1, borrowed.chunkX());
                assertEquals(-2, borrowed.chunkZ());
                assertSame(route.activation(), borrowed.activation());
                assertFalse(borrowed.claimGeneratedSemantics());
            }
            try (GenerationHistoryRuntimeRouter.CoordinateScope routed = router.openCoordinateScope(16, 0)) {
                assertEquals(1, routed.chunkX());
                assertEquals(0, routed.chunkZ());
                assertSame(route.activation(), routed.activation());
            }
        }
        verify(engine, times(2)).openGenerationRuntimeScope(first);

        when(engine.hasGenerationRuntimeScope()).thenReturn(true);
        try (GenerationHistoryRuntimeRouter.CoordinateScope bypassed = router.openCoordinateScope(31, 47)) {
            assertEquals(1, bypassed.chunkX());
            assertEquals(2, bypassed.chunkZ());
            assertThrows(IllegalStateException.class, bypassed::activation);
            assertFalse(bypassed.claimGeneratedSemantics());
        }
        verify(engine, times(2)).openGenerationRuntimeScope(first);
        router.close();
    }

    @Test
    public void attachPromotesSamePackWhenTheCurrentKernelChanges() throws Exception {
        Path world = temporaryFolder.newFolder("router-kernel-update-world").toPath();
        Path pack = createPack("router-kernel-update-pack", "alpha");
        GenerationKernelRegistry.Version versionOne = new GenerationKernelRegistry.Version(1, 1, 1);
        GenerationKernelRegistry.Version versionTwo = new GenerationKernelRegistry.Version(2, 1, 1);
        GenerationKernelRegistry kernels = kernels(versionTwo);
        GenerationHistory history = createHistory(world, pack, versionOne, kernels);
        history = GenerationHistory.open(world, new GenerationKernelRegistry(versionTwo,
                List.of(kernels.requireSupported(versionTwo))));
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding first = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(first);
        installScopeTracking(engine);

        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attachAndPromotePending(
                engine,
                history,
                (ignored, blockX, blockZ) -> signature(blockX, blockZ),
                64,
                runtimes
        );

        GenerationActivation active = history.activeActivation();
        assertEquals(2L, active.activationId());
        assertEquals(versionTwo, history.activeEpoch().kernelVersion());
        GenerationActivation initial = history.manifest().activation(1L).orElseThrow();
        assertEquals(history.manifest().epoch(initial.epochId()).orElseThrow().packFingerprint(),
                history.activeEpoch().packFingerprint());
        verify(engine).setDefaultGenerationRuntime(runtimes.bindings.get(2L));
        router.close();
    }

    @Test
    public void changedBuildRevisionWithTheSameAbiAutomaticallyCreatesANewActivation() throws Exception {
        Path world = temporaryFolder.newFolder("router-build-revision-world").toPath();
        Path pack = createPack("router-build-revision-pack", "alpha");
        GenerationKernelRegistry.Version version = new GenerationKernelRegistry.Version(1, 1, 1);
        GenerationHistory original = createHistory(world, pack, version, kernels(version));
        GenerationKernelRegistry upgraded = new GenerationKernelRegistry(version, List.of(
                new GenerationKernelRegistry.Kernel(1, "9".repeat(64), Map.of(
                        new GenerationKernelRegistry.AlgorithmVersion(1, 1),
                        (engine, plan) -> { throw new AssertionError("Mock runtime factory owns this test."); }))));
        GenerationHistory history = GenerationHistory.open(world, upgraded);
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding initial = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(initial);
        try (GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attachAndPromotePending(
                engine, history, (ignored, x, z) -> signature(x, z), 256, runtimes)) {
            assertEquals(2L, history.activeActivation().activationId());
            assertEquals(version, history.activeEpoch().kernelVersion());
            assertEquals("9".repeat(64), history.activeEpoch().kernelImplementationFingerprint());
            assertEquals(original.activeEpoch().packFingerprint(), history.activeEpoch().packFingerprint());
            assertEquals(0, runtimes.loadCount(1L));
            try (GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(0, 0)) {
                assertEquals(2L, route.activation().activationId());
            }
        }
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

    @Test
    public void pendingOldRevisionPromotesWithoutLoadingItsArchivedImplementation() throws Exception {
        Path world = temporaryFolder.newFolder("router-pending-kernel-world").toPath();
        Path packA = createPack("router-pending-kernel-a", "alpha");
        Path packB = createPack("router-pending-kernel-b", "beta");
        GenerationKernelRegistry.Version versionOne = new GenerationKernelRegistry.Version(1, 1, 1);
        GenerationKernelRegistry.Version versionTwo = new GenerationKernelRegistry.Version(2, 1, 1);
        GenerationKernelRegistry kernels = kernels(versionTwo);
        GenerationHistory history = createHistory(world, packA, versionOne, kernels);
        history.stageUpdate(
                packB,
                GenerationPackFingerprint.compute(packB, GenerationPackFingerprint.CURRENT_VERSION),
                contract(),
                GenerationRegistryContract.empty(),
                64,
                versionOne
        );
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding first = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(first);
        installScopeTracking(engine);

        GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attachAndPromotePending(
                engine,
                history,
                (ignored, blockX, blockZ) -> signature(blockX, blockZ),
                64,
                runtimes
        );

        assertEquals(3L, history.activeActivation().activationId());
        assertEquals(versionTwo, history.activeEpoch().kernelVersion());
        assertEquals(GenerationPackFingerprint.compute(packB, GenerationPackFingerprint.CURRENT_VERSION),
                history.activeEpoch().packFingerprint());
        assertEquals(0, runtimes.loadCount(2L));
        verify(engine).setDefaultGenerationRuntime(runtimes.bindings.get(3L));
        router.close();
    }

    private static void routeUntilClosed(GenerationHistoryRuntimeRouter router, CountDownLatch routing) {
        boolean started = false;
        while (!Thread.currentThread().isInterrupted()) {
            try (GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(0, 0)) {
                assertEquals(1L, route.activation().activationId());
                if (!started) {
                    started = true;
                    routing.countDown();
                }
            } catch (IOException failure) {
                throw new AssertionError(failure);
            } catch (IllegalStateException closed) {
                assertEquals("Generation-history runtime router is closed.", closed.getMessage());
                return;
            }
        }
    }

    private AtomicReference<IrisEngine.GenerationRuntimeBinding> installScopeTracking(IrisEngine engine) {
        AtomicReference<IrisEngine.GenerationRuntimeBinding> scoped = new AtomicReference<>();
        when(engine.openGenerationRuntimeScope(any())).thenAnswer(invocation -> {
            IrisEngine.GenerationRuntimeBinding binding = invocation.getArgument(0);
            scoped.set(binding);
            IrisEngine.GenerationRuntimeScope scope = mock(IrisEngine.GenerationRuntimeScope.class);
            doAnswer(ignored -> {
                scoped.compareAndSet(binding, null);
                return null;
            }).when(scope).close();
            return scope;
        });
        return scoped;
    }

    private CompletableFuture<GenerationHistoryRuntimeRouter.RuntimeRoute> openRouteAsync(
            GenerationHistoryRuntimeRouter router,
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            int chunkX,
            int chunkZ
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!start.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start concurrent route acquisition.");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted before concurrent route acquisition.", interrupted);
            }
            return openRouteUnchecked(router, chunkX, chunkZ);
        }, executor);
    }

    private static GenerationHistoryRuntimeRouter.RuntimeRoute openRouteUnchecked(
            GenerationHistoryRuntimeRouter router,
            int chunkX,
            int chunkZ
    ) {
        try {
            return router.openRoute(chunkX, chunkZ);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void awaitClosed(GenerationHistoryRuntimeRouter router) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!router.isClosed() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(router.isClosed());
    }

    private Path createPack(String name, String content) throws IOException {
        Path pack = temporaryFolder.newFolder(name).toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.writeString(pack.resolve("dimensions/main.json"), content);
        return pack;
    }

    private GenerationHistory createThreeActivationHistory(Path world, String prefix) throws IOException {
        Path packA = createPack(prefix + "-a", "alpha");
        Path packB = createPack(prefix + "-b", "beta");
        Path packC = createPack(prefix + "-c", "gamma");
        GenerationHistory history = createHistory(world, packA);
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{0, 0}});
        stage(history, packB);
        promoteWithSignatures(history);
        writeRegion(region, new int[][]{{0, 0}, {1, 0}});
        stage(history, packC);
        promoteWithSignatures(history);
        return history;
    }

    private static GenerationHistory createHistory(Path world, Path pack) throws IOException {
        return GenerationHistory.create(
                world,
                pack,
                GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION),
                42L,
                contract(),
                GenerationRegistryContract.empty()
        );
    }

    private static GenerationHistory createHistory(
            Path world,
            Path pack,
            GenerationKernelRegistry.Version version,
            GenerationKernelRegistry kernels
    ) throws IOException {
        return GenerationHistory.create(
                world,
                pack,
                GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION),
                42L,
                contract(),
                GenerationRegistryContract.empty(),
                version,
                kernels
        );
    }

    private static GenerationKernelRegistry kernels(GenerationKernelRegistry.Version current) {
        return new GenerationKernelRegistry(
                current,
                Set.of(
                        new GenerationKernelRegistry.Kernel(
                                1,
                                "1".repeat(64),
                                Map.of(
                                        new GenerationKernelRegistry.AlgorithmVersion(1, 1),
                                        (engine, transitionPlan) -> {
                                            throw new AssertionError("Mock runtime factory owns this test.");
                                        }
                                )
                        ),
                        new GenerationKernelRegistry.Kernel(
                                2,
                                "2".repeat(64),
                                Map.of(
                                        new GenerationKernelRegistry.AlgorithmVersion(1, 1),
                                        (engine, transitionPlan) -> {
                                            throw new AssertionError("Mock runtime factory owns this test.");
                                        }
                                )
                        )
                )
        );
    }

    private static GenerationActivation stage(GenerationHistory history, Path pack) throws IOException {
        return history.stageUpdate(
                pack,
                GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION),
                contract(),
                GenerationRegistryContract.empty(),
                256
        );
    }

    private static GenerationActivation promoteWithSignatures(GenerationHistory history) throws IOException {
        return history.promotePending(boundary -> GenerationHistoryRuntimeRouterTest::signature);
    }

    private static GenerationEpoch.DimensionContract contract() {
        return new GenerationEpoch.DimensionContract(
                "overworld",
                "iris:overworld_type",
                "NORMAL",
                "OVERWORLD",
                127,
                -64,
                384,
                384,
                1D,
                false,
                "none",
                0,
                "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA,
                "c".repeat(64)
        );
    }

    private static TerrainBoundarySignature signature(int blockX, int blockZ) {
        return new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(blockX, blockZ, 64, 63, OptionalInt.empty(), OptionalInt.empty()),
                new TerrainBoundarySignature.Samples(
                        new TerrainBoundarySignature.VerticalLayout(-64, 64, 2),
                        new TerrainBoundarySignature.BiomeEncoding(List.of("iris:test"), new short[]{0, 0})
                )
        , BoundaryColumnGeometry.empty());
    }

    private static void writeRegion(Path file, int[][] chunks) throws IOException {
        SavedTerrainTestRegion.write(file, chunks);
    }

    private static final class FakeRuntimeFactory
            implements GenerationHistoryRuntimeRouter.ActivationRuntimeFactory {
        private final Map<Long, IrisEngine.GenerationRuntimeBinding> bindings = new HashMap<>();
        private final Map<Long, Path> mantles = new HashMap<>();
        private final Map<Long, AtomicInteger> loadCounts = new HashMap<>();
        private GenerationKernelRegistry.Version loadedVersion;
        private long blockedActivation = -1L;
        private CountDownLatch loadStarted = new CountDownLatch(0);
        private CountDownLatch releaseLoad = new CountDownLatch(0);

        @Override
        public void validateBase(
                IrisEngine engine,
                GenerationHistory history,
                GenerationActivation activation,
                GenerationEpoch epoch,
                IrisEngine.GenerationRuntimeBinding binding
        ) {
            if (engine.getGenerationSessions() == null) {
                when(engine.getGenerationSessions()).thenReturn(new GenerationSessionManager(true));
            }
        }

        @Override
        public IrisEngine.GenerationRuntimeBinding load(
                IrisEngine engine,
                GenerationHistory history,
                GenerationActivation activation,
                GenerationEpoch epoch
        ) throws IOException {
            if (!epoch.kernelVersion().equals(history.currentKernelVersion())
                    || !history.usesCurrentGenerator()) {
                throw new AssertionError("Archived generator implementations must never load.");
            }
            synchronized (loadCounts) {
                loadCounts.computeIfAbsent(activation.activationId(), ignored -> new AtomicInteger()).incrementAndGet();
            }
            if (activation.activationId() == blockedActivation) {
                loadStarted.countDown();
                try {
                    if (!releaseLoad.await(5L, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to release the blocked runtime load.");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while loading a test runtime.", interrupted);
                }
            }
            GenerationKernelRegistry.Version version = loadedVersion == null
                    ? epoch.kernelVersion()
                    : loadedVersion;
            return binding(history, activation, version);
        }

        private void blockLoad(long activationId) {
            blockedActivation = activationId;
            loadStarted = new CountDownLatch(1);
            releaseLoad = new CountDownLatch(1);
        }

        private int loadCount(long activationId) {
            synchronized (loadCounts) {
                AtomicInteger count = loadCounts.get(activationId);
                return count == null ? 0 : count.get();
            }
        }

        private IrisEngine.GenerationRuntimeBinding binding(
                GenerationHistory history,
                GenerationActivation activation
        ) throws IOException {
            return binding(history, activation, requireEpoch(history, activation).kernelVersion());
        }

        private IrisEngine.GenerationRuntimeBinding binding(
                GenerationHistory history,
                GenerationActivation activation,
                GenerationKernelRegistry.Version version
        ) throws IOException {
            GenerationEpoch epoch = requireEpoch(history, activation);
            IrisEngine.GenerationRuntimeBinding binding = mock(IrisEngine.GenerationRuntimeBinding.class);
            Path mantle = history.paths().activationMantleRoot(activation.activationId());
            TransitionGenerationPlan plan = activation.isInitial()
                    ? null
                    : history.transitionPlan(activation.activationId());
            when(binding.kernelVersion()).thenReturn(version);
            when(binding.runtimeKernel()).thenReturn(new GenerationKernelRegistry.RuntimeKernel(
                    version,
                    epoch.kernelImplementationFingerprint(),
                    (engine, transitionPlan) -> mock(IrisComplex.class)
            ));
            when(binding.mantleStorageDirectory()).thenReturn(mantle);
            when(binding.transitionPlan()).thenReturn(plan);
            when(binding.runtimeId()).thenReturn(Math.toIntExact(activation.activationId()));
            bindings.put(activation.activationId(), binding);
            mantles.put(activation.activationId(), mantle);
            return binding;
        }

        private static GenerationEpoch requireEpoch(
                GenerationHistory history,
                GenerationActivation activation
        ) {
            return history.manifest().epoch(activation.epochId()).orElseThrow();
        }
    }
}
