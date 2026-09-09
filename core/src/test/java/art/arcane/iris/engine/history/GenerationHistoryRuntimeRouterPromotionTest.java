package art.arcane.iris.engine.history;

import art.arcane.iris.engine.IrisEngine;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class GenerationHistoryRuntimeRouterPromotionTest extends GenerationHistoryRuntimeRouterSupport {
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
                        (engine, plan, detached) -> { throw new AssertionError("Mock runtime factory owns this test."); }))));
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
}
