package art.arcane.iris.world.history;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class GenerationHistoryRuntimeRouterRoutingTest extends GenerationHistoryRuntimeRouterSupport {
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
    public void savedChunkMantlesShareRecordedStorageWithoutLoadingHistoricalRuntimes() throws Exception {
        Path world = temporaryFolder.newFolder("router-saved-chunk-world").toPath();
        GenerationHistory history = createThreeActivationHistory(world, "router-saved-chunk");
        int recordedChunks = history.explicitChunkCount();
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding active = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(active);
        AtomicReference<IrisEngine.GenerationRuntimeBinding> scoped = installScopeTracking(engine);
        EngineMantle activeEngineMantle = mock(EngineMantle.class);
        Mantle<Matter> activeMantle = mock(Mantle.class);
        when(engine.getMantle()).thenReturn(activeEngineMantle);
        when(activeEngineMantle.getMantle()).thenReturn(activeMantle);
        Map<File, IrisData> dataByPack = new HashMap<>();
        Map<Path, Mantle<Matter>> storageByPath = new HashMap<>();
        for (long activationId : List.of(1L, 2L)) {
            dataByPack.put(history.packRoot(activationId).toFile(), mock(IrisData.class));
            storageByPath.put(history.paths().activationMantleRoot(activationId), mock(Mantle.class));
        }

        try (MockedStatic<IrisData> data = mockStatic(IrisData.class);
             MockedStatic<IrisEngineMantle> storage = mockStatic(IrisEngineMantle.class);
             GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                     engine, history, (ignored, x, z) -> signature(x, z), runtimes)) {
            data.when(() -> IrisData.openRuntime(any(File.class)))
                    .thenAnswer(invocation -> dataByPack.get(invocation.getArgument(0, File.class)));
            storage.when(() -> IrisEngineMantle.createMantle(any(), any(), any()))
                    .thenAnswer(invocation -> storageByPath.get(invocation.getArgument(1, Path.class)));
            for (int chunkX = 0; chunkX < 3; chunkX++) {
                long activationId = chunkX + 1L;
                Mantle<Matter> expected = activationId == 3L ? activeMantle
                        : storageByPath.get(history.paths().activationMantleRoot(activationId));
                try (GenerationHistoryRuntimeRouter.SavedChunkMantle first = router.openSavedChunkMantle(chunkX, 0)) {
                    assertSame(expected, first.mantle());
                    assertNull(scoped.get());
                    try (GenerationHistoryRuntimeRouter.SavedChunkMantle second = router.openSavedChunkMantle(chunkX, 0)) {
                        assertSame(first.mantle(), second.mantle());
                    }
                    verify(expected, never()).close();
                }
                if (activationId != 3L) {
                    verify(expected).close();
                    IrisData savedData = dataByPack.get(history.packRoot(activationId).toFile());
                    verify(savedData).bindGenerationRegistryContract(history.resolveEpoch(chunkX, 0).registryContract());
                    verify(savedData).registerEngine(engine);
                    verify(savedData).unregisterEngine(engine);
                    verify(savedData).close();
                }
                try (GenerationHistoryRuntimeRouter.RuntimeStage stage = router.openStage(chunkX, 0)) {
                    assertEquals(3L, stage.activation().activationId());
                    assertSame(active, scoped.get());
                }
                assertTrue(history.semantics(chunkX, 0).isEmpty());
            }
            storage.verify(() -> IrisEngineMantle.createMantle(any(), any(), any()), times(2));
            assertEquals(recordedChunks, history.explicitChunkCount());
            assertEquals(3L, history.activeActivation().activationId());
            assertEquals(1L, history.resolveActivation(0, 0).activationId());
            assertEquals(2L, history.resolveActivation(1, 0).activationId());
            assertEquals(Set.of(3L), runtimes.bindings.keySet());
            verify(activeMantle, never()).close();
            verify(engine, never()).setDefaultGenerationRuntime(any());
        }
    }

    @Test
    public void savedChunkMantleOpensAfterItsGeneratorKernelIsNoLongerAvailable() throws Exception {
        Path world = temporaryFolder.newFolder("router-saved-old-kernel-world").toPath();
        Path pack = createPack("router-saved-old-kernel-pack", "alpha");
        GenerationKernelRegistry.Version oldVersion = new GenerationKernelRegistry.Version(1, 1, 1);
        GenerationKernelRegistry.Version currentVersion = new GenerationKernelRegistry.Version(2, 1, 1);
        GenerationKernelRegistry kernels = kernels(currentVersion);
        createHistory(world, pack, oldVersion, kernels);
        writeRegion(Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca"), new int[][]{{0, 0}});
        GenerationHistory history = GenerationHistory.open(world,
                new GenerationKernelRegistry(currentVersion, List.of(kernels.requireSupported(currentVersion))));
        history.stageCurrentKernel(256);
        promoteWithSignatures(history);
        assertEquals(oldVersion, history.resolveEpoch(0, 0).kernelVersion());
        IrisEngine engine = mock(IrisEngine.class);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding active = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(active);
        IrisData savedData = mock(IrisData.class);
        Mantle<Matter> savedStorage = mock(Mantle.class);
        try (MockedStatic<IrisData> data = mockStatic(IrisData.class);
             MockedStatic<IrisEngineMantle> storage = mockStatic(IrisEngineMantle.class);
             GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                     engine, history, (ignored, x, z) -> signature(x, z), runtimes)) {
            data.when(() -> IrisData.openRuntime(history.packRoot(1L).toFile())).thenReturn(savedData);
            storage.when(() -> IrisEngineMantle.createMantle(any(), any(), any())).thenReturn(savedStorage);
            try (GenerationHistoryRuntimeRouter.SavedChunkMantle saved = router.openSavedChunkMantle(0, 0)) {
                assertSame(savedStorage, saved.mantle());
            }
            verify(savedStorage).close();
            storage.verify(() -> IrisEngineMantle.createMantle(
                    any(), eq(history.paths().activationMantleRoot(1L)), any()));
            assertEquals(Set.of(2L), runtimes.bindings.keySet());
            assertEquals(0, runtimes.loadCount(1L));
            verify(engine, never()).openGenerationRuntimeScope(any());
        }
    }

    @Test
    public void savedChunkScopeRejectsReentrantCloseAndCutoverAfterThreadHandoff() throws Exception {
        Path world = temporaryFolder.newFolder("router-saved-scope-world").toPath();
        GenerationHistory history = createHistory(world, createPack("router-saved-scope-pack", "alpha"));
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.isStudio()).thenReturn(true);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine.GenerationRuntimeBinding active = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(active);
        AtomicReference<IrisEngine.GenerationRuntimeBinding> scoped = installScopeTracking(engine);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getMantle()).thenReturn(mock(Mantle.class));
        try (GenerationHistoryRuntimeRouter router = GenerationHistoryRuntimeRouter.attach(
                engine, history, (ignored, x, z) -> signature(x, z), runtimes);
             GenerationHistoryRuntimeRouter.SavedChunkMantle saved = router.openSavedChunkMantle(0, 0)) {
            saved.detachThread();
            try (GenerationHistoryRuntimeRouter.SavedChunkMantle.Scope ignored = saved.openScope()) {
                assertNull(scoped.get());
                assertThrows(IllegalStateException.class, router::close);
                assertThrows(IllegalStateException.class, saved::close);
                assertThrows(IllegalStateException.class, () -> router.beginStudioCutover(1L));
                assertThrows(IllegalStateException.class,
                        () -> engine.getGenerationSessions().transitionGate().beginTransition(1L));
            }
        }
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
}
