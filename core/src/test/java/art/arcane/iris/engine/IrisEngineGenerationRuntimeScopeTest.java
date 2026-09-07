package art.arcane.iris.engine;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.GenerationRuntime.BiomeMaxes;
import art.arcane.iris.engine.framework.EngineEffects;
import art.arcane.iris.engine.framework.BiomeEnvironment;
import art.arcane.iris.engine.history.SavedBiomeRuntime;
import art.arcane.iris.engine.history.SavedBiomeUnavailableException;
import art.arcane.iris.engine.object.IrisRegion;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.framework.EngineStage;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.EngineWorldManager;
import art.arcane.iris.engine.history.GenerationKernelRegistry;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.framework.IrisEngineMode;
import art.arcane.iris.engine.framework.NativeStructureVolumeMemo;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.engine.history.GenerationAdmission;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimensionCarvingEntry;
import art.arcane.iris.engine.object.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.object.IrisRange;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisEngineGenerationRuntimeScopeTest {
    @BeforeClass
    public static void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformBlockState block = mock(PlatformBlockState.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenReturn(block);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void carvingResolverRestoresNestedRuntimeDefinitions() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture first = runtime(2, 2D, 2D, 2D);
        RuntimeFixture second = runtime(3, 3D, 3D, 3D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisDimensionCarvingEntry root = mock(IrisDimensionCarvingEntry.class);
        IrisDimensionCarvingEntry child = mock(IrisDimensionCarvingEntry.class);
        when(root.isEnabled()).thenReturn(true);
        when(root.getWorldYRange()).thenReturn(new IrisRange(-64, 320));
        when(root.getChildRecursionDepth()).thenReturn(1);
        when(root.getChildren()).thenReturn(new KList<>("child"));
        when(child.isEnabled()).thenReturn(true);
        CNG generator = mock(CNG.class);
        when(generator.noiseFast2D(19D, -3D)).thenReturn(1D);
        IrisBiome[] expected = new IrisBiome[3];
        RuntimeFixture[] fixtures = {active, first, second};
        for (int index = 0; index < fixtures.length; index++) {
            RuntimeFixture fixture = fixtures[index];
            IrisBiome parentBiome = mock(IrisBiome.class);
            IrisBiome childBiome = mock(IrisBiome.class);
            when(parentBiome.getRarity()).thenReturn(1);
            when(childBiome.getRarity()).thenReturn(1);
            when(root.getRealBiome(fixture.data)).thenReturn(parentBiome);
            when(child.getRealBiome(fixture.data)).thenReturn(childBiome);
            when(root.getChildrenGenerator(fixture.runtime.seedManager().getCarve() ^ 0x9E3779B97F4A7C15L,
                    fixture.data)).thenReturn(generator);
            when(fixture.dimension.getCarving()).thenReturn(new KList<>(root));
            when(fixture.dimension.getCarvingEntryIndex()).thenReturn(Map.of("child", child));
            expected[index] = childBiome;
        }
        IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();
        assertSame(expected[0], carvingBiome(engine, state));
        try (IrisEngine.GenerationRuntimeScope outer = engine.openGenerationRuntimeScope(
                detachedBinding(engine, first.runtime))) {
            assertSame(expected[1], carvingBiome(engine, state));
            try (IrisEngine.GenerationRuntimeScope inner = engine.openGenerationRuntimeScope(
                    detachedBinding(engine, second.runtime))) {
                assertSame(expected[2], carvingBiome(engine, state));
            }
            assertSame(expected[1], carvingBiome(engine, state));
        }
        assertSame(expected[0], carvingBiome(engine, state));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void naturalCaveSamplesKeepSelectedRuntimeAcrossChunkEdges() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture selected = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding binding = detachedBinding(engine, selected.runtime);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(router.biomes()).thenReturn(mock(SavedBiomeRuntime.class));
        setField(engine, "generationHistoryRuntimeRouter", router);
        doReturn(null).when(engine).getDimensionStackContext();
        doReturn(false).when(engine).answersFromNaturalTerrain(anyInt(), anyInt());
        doCallRealMethod().when(selected.complex).isNaturalTerrainContext();
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(selected.complex);
        when(context.isNaturalTerrain()).thenReturn(true);
        IrisBiome cave = mock(IrisBiome.class);
        when(cave.getLoadKey()).thenReturn("cave");
        IrisBiome surface = mock(IrisBiome.class);
        ProceduralStream<IrisBiome> caves = mock(ProceduralStream.class);
        ProceduralStream<IrisBiome> surfaces = mock(ProceduralStream.class);
        ProceduralStream<Double> height = mock(ProceduralStream.class);
        when(selected.complex.getCaveBiomeStream()).thenReturn(caves);
        when(selected.complex.getTrueBiomeStream()).thenReturn(surfaces);
        when(selected.complex.getHeightStream()).thenReturn(height);
        when(caves.get(19D, -3D)).thenReturn(cave);
        when(surfaces.get(19D, -3D)).thenReturn(surface);
        when(height.get(19D, -3D)).thenReturn(80D);

        try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(binding);
             IrisContext.Scope contextScope = IrisContext.open(engine, 73L, context)) {
            assertSame(cave, engine.getCaveBiome(19, -3));
            assertSame(cave, engine.getCaveBiome(19, 20, -3));
            assertSame(cave, engine.getCaveBiome(19, 20, -3, new IrisDimensionCarvingResolver.State()));
            assertSame(surface, engine.getSurfaceBiome(19, -3));
            assertSame(selected.complex, engine.getComplex());
            verify(router, never()).openCoordinateScope(anyInt(), anyInt());
            when(context.isNaturalTerrain()).thenReturn(false);
            assertSame(cave, engine.getCaveBiome(19, -3));
            verify(router, times(1)).openCoordinateScope(19, -3);
        }
        assertSame(active.complex, engine.getComplex());
        assertNull(IrisContext.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void mismatchedAndUnboundNaturalContextsKeepCoordinateRouting() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture selected = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(router.biomes()).thenReturn(mock(SavedBiomeRuntime.class));
        setField(engine, "generationHistoryRuntimeRouter", router);
        ProceduralStream<IrisBiome> caves = mock(ProceduralStream.class);
        IrisBiome cave = mock(IrisBiome.class);
        when(cave.getLoadKey()).thenReturn("cave");
        when(caves.get(19D, -3D)).thenReturn(cave);
        when(active.complex.getCaveBiomeStream()).thenReturn(caves);
        when(selected.complex.getCaveBiomeStream()).thenReturn(caves);
        doCallRealMethod().when(active.complex).isNaturalTerrainContext();
        doCallRealMethod().when(selected.complex).isNaturalTerrainContext();
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(active.complex);
        when(context.isNaturalTerrain()).thenReturn(true);

        try (IrisContext.Scope ignored = IrisContext.open(engine, 73L, context)) {
            engine.getCaveBiome(19, -3);
            try (IrisEngine.GenerationRuntimeScope runtimeScope = engine.openGenerationRuntimeScope(
                    detachedBinding(engine, selected.runtime))) {
                engine.getCaveBiome(19, -3);
            }
        }
        engine.getCaveBiome(19, -3);
        verify(router, times(3)).openCoordinateScope(19, -3);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void unboundContentContextSkipsSavedBiomesAndSelectsTheCoordinateRuntime() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture selected = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding binding = detachedBinding(engine, selected.runtime);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        when(router.biomes()).thenReturn(saved);
        setField(engine, "generationHistoryRuntimeRouter", router);
        doReturn(null).when(engine).getDimensionStackContext();
        doReturn(false).when(engine).answersFromNaturalTerrain(anyInt(), anyInt());
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(active.complex);
        when(context.isNaturalTerrain()).thenReturn(false);
        IrisBiome cave = mock(IrisBiome.class);
        when(cave.getLoadKey()).thenReturn("cave");
        IrisBiome surface = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        ProceduralStream<IrisBiome> caves = mock(ProceduralStream.class);
        ProceduralStream<IrisBiome> surfaces = mock(ProceduralStream.class);
        ProceduralStream<IrisRegion> regions = mock(ProceduralStream.class);
        when(selected.complex.getCaveBiomeStream()).thenReturn(caves);
        when(selected.complex.getTrueBiomeStream()).thenReturn(surfaces);
        when(selected.complex.getRegionStream()).thenReturn(regions);
        when(caves.get(19D, -3D)).thenReturn(cave);
        when(surfaces.get(19D, -3D)).thenReturn(surface);
        when(regions.get(19D, -3D)).thenReturn(region);
        when(router.openCoordinateScope(19, -3)).thenAnswer(invocation -> {
            assertSame(active.complex, engine.getComplex());
            IrisEngine.GenerationRuntimeScope runtimeScope = engine.openGenerationRuntimeScope(binding);
            GenerationHistoryRuntimeRouter.CoordinateScope coordinateScope = mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
            doAnswer(close -> {
                runtimeScope.close();
                return null;
            }).when(coordinateScope).close();
            return coordinateScope;
        });

        try (IrisContext.Scope ignored = IrisContext.open(engine, 73L, context)) {
            assertSame(cave, engine.getCaveBiome(19, -3));
            assertSame(surface, engine.getSurfaceBiome(19, -3));
            assertSame(region, engine.getRegion(19, -3));
            assertSame(region, engine.getRegion(19, 20, -3));
            assertSame(active.complex, engine.getComplex());
        }
        verify(router, times(4)).openCoordinateScope(19, -3);
        verifyNoInteractions(saved);
        assertNull(IrisContext.get());
    }

    @Test
    public void gameplayContextWithoutAChunkStillReadsSavedBiomes() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        when(active.target.getWorld().minHeight()).thenReturn(-64);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        when(router.biomes()).thenReturn(saved);
        setField(engine, "generationHistoryRuntimeRouter", router);
        BiomeEnvironment environment = new BiomeEnvironment(4L, mock(IrisBiome.class),
                mock(IrisRegion.class), mock(IrisDimension.class), mock(IrisData.class));
        when(saved.resolve(19, -64, -3, true)).thenReturn(Optional.of(environment));
        when(saved.resolveCaveBase(19, -3)).thenReturn(Optional.of(environment));

        try (IrisContext.Scope ignored = IrisContext.open(engine, 0L, null)) {
            assertSame(environment.biome(), engine.getCaveBiome(19, -3));
            assertSame(environment.biome(), engine.getSurfaceBiome(19, -3));
            assertSame(environment.region(), engine.getRegion(19, -3));
        }
        verify(saved).resolveCaveBase(19, -3);
        verify(saved, times(2)).resolve(19, -64, -3, true);
        verify(router, never()).openCoordinateScope(anyInt(), anyInt());
        assertNull(IrisContext.get());
    }

    @Test
    public void scopedGettersResolveDetachedGenerationStateAndWorldServicesStayActive() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 2D, 3D);
        RuntimeFixture detached = runtime(2, 4D, 5D, 6D);
        EngineEffects effects = mock(EngineEffects.class);
        EngineWorldManager worldManager = mock(EngineWorldManager.class);
        IrisEngine engine = engine(active.runtime, effects, worldManager);
        IrisEngine.GenerationRuntimeBinding binding = detachedBinding(engine, detached.runtime);

        try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(binding)) {
            assertSame(detached.target, engine.getTarget());
            assertSame(detached.data, engine.getData());
            assertSame(detached.dimension, engine.getDimension());
            assertSame(detached.runtime.seedManager(), engine.getSeedManager());
            assertSame(detached.complex, engine.getComplex());
            assertSame(detached.upperContext, engine.getUpperContext());
            assertSame(detached.dimensionStackContext, engine.getDimensionStackContext());
            assertSame(detached.mode, engine.getMode());
            assertSame(detached.mantle, engine.getMantle());
            assertSame(detached.hash32, engine.getHash32());
            assertEquals(2, engine.getCacheID());
            assertEquals(4D, engine.getMaxBiomeObjectDensity(), 0D);
            assertEquals(5D, engine.getMaxBiomeLayerDensity(), 0D);
            assertEquals(6D, engine.getMaxBiomeDecoratorDensity(), 0D);
            assertSame(effects, engine.getEffects());
            assertSame(worldManager, engine.getWorldManager());
        }

        assertSame(active.target, engine.getTarget());
        assertSame(active.data, engine.getData());
        assertSame(active.runtime.seedManager(), engine.getSeedManager());
        assertSame(active.mantle, engine.getMantle());
        assertEquals(1, engine.getCacheID());
    }

    @Test
    public void scopedKernelVersionProvidesTheAlgorithmDispatchKey() throws Exception {
        GenerationKernelRegistry.Version activeVersion = new GenerationKernelRegistry.Version(1, 1, 1);
        GenerationKernelRegistry.Version historicalVersion = new GenerationKernelRegistry.Version(2, 3, 4);
        RuntimeFixture active = runtime(1, 1D, 1D, 1D, activeVersion);
        RuntimeFixture historical = runtime(2, 1D, 1D, 1D, historicalVersion);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding binding = detachedBinding(engine, historical.runtime);

        assertEquals(activeVersion, engine.getGenerationKernelVersion());
        assertSame(active.runtime.runtimeKernel(), engine.getGenerationRuntimeKernel());
        try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(binding)) {
            assertEquals(historicalVersion, engine.getGenerationKernelVersion());
            assertSame(historical.runtime.runtimeKernel(), engine.getGenerationRuntimeKernel());
            assertNotSame(active.runtime.runtimeKernel(), engine.getGenerationRuntimeKernel());
        }
        assertEquals(activeVersion, engine.getGenerationKernelVersion());
    }

    @Test
    public void executableKernelFactoriesProduceDistinctRuntimeBindings() throws Exception {
        GenerationKernelRegistry.Version versionOne = new GenerationKernelRegistry.Version(1, 1, 1);
        GenerationKernelRegistry.Version versionTwo = new GenerationKernelRegistry.Version(2, 1, 1);
        IrisComplex complexOne = mock(IrisComplex.class);
        IrisComplex complexTwo = mock(IrisComplex.class);
        GenerationKernelRegistry kernels = new GenerationKernelRegistry(
                versionTwo,
                Set.of(
                        new GenerationKernelRegistry.Kernel(
                                1,
                                "1".repeat(64),
                                Map.of(
                                        new GenerationKernelRegistry.AlgorithmVersion(1, 1),
                                        (engine, transitionPlan) -> complexOne
                                )
                        ),
                        new GenerationKernelRegistry.Kernel(
                                2,
                                "2".repeat(64),
                                Map.of(
                                        new GenerationKernelRegistry.AlgorithmVersion(1, 1),
                                        (engine, transitionPlan) -> complexTwo
                                )
                        )
                )
        );
        GenerationKernelRegistry.RuntimeKernel kernelOne = kernels.select(versionOne);
        GenerationKernelRegistry.RuntimeKernel kernelTwo = kernels.select(versionTwo);
        IrisEngine factoryContext = mock(IrisEngine.class);
        RuntimeFixture first = runtime(
                1, 1D, 1D, 1D, kernelOne, kernelOne.createComplex(factoryContext, null));
        RuntimeFixture second = runtime(
                2, 1D, 1D, 1D, kernelTwo, kernelTwo.createComplex(factoryContext, null));
        IrisEngine engine = engine(first.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding firstBinding = new IrisEngine.GenerationRuntimeBinding(
                engine, first.runtime);
        IrisEngine.GenerationRuntimeBinding secondBinding = detachedBinding(engine, second.runtime);

        assertSame(kernelOne, firstBinding.runtimeKernel());
        assertSame(kernelTwo, secondBinding.runtimeKernel());
        assertSame(complexOne, engine.getComplex());
        try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(secondBinding)) {
            assertSame(kernelTwo, engine.getGenerationRuntimeKernel());
            assertSame(complexTwo, engine.getComplex());
        }
    }

    @Test
    public void burstWorkerReopensCapturedGenerationRuntimeScope() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture detached = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding binding = detachedBinding(engine, detached.runtime);
        EngineMode mode = new IrisEngineMode(engine) {
        };
        MultiBurst workerPool = new MultiBurst("Generation Runtime Scope Test", Thread.NORM_PRIORITY, () -> 2);
        when(detached.target.getBurster()).thenReturn(workerPool);
        AtomicReference<EngineTarget> observedTarget = new AtomicReference<>();
        AtomicReference<Thread> observedThread = new AtomicReference<>();
        EngineStage stage = (x, z, blocks, biomes, multicore, context) -> {
            observedTarget.set(engine.getTarget());
            observedThread.set(Thread.currentThread());
        };
        ChunkContext context = mock(ChunkContext.class);
        when(context.getGenerationSessionId()).thenReturn(17L);
        Thread caller = Thread.currentThread();

        try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(binding)) {
            mode.burst(stage).generate(0, 0, null, null, true, context);
        } finally {
            mode.close();
            workerPool.close();
        }

        assertSame(detached.target, observedTarget.get());
        assertNotSame(caller, observedThread.get());
    }

    @Test
    public void scopeRejectsForeignClosedAndOutOfOrderBindings() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture first = runtime(2, 2D, 2D, 2D);
        RuntimeFixture second = runtime(3, 3D, 3D, 3D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine other = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding foreign = new IrisEngine.GenerationRuntimeBinding(other, first.runtime);
        assertThrows(IllegalArgumentException.class, () -> engine.openGenerationRuntimeScope(foreign));

        IrisEngine.GenerationRuntimeBinding closed = new IrisEngine.GenerationRuntimeBinding(engine, first.runtime);
        assertThrows(IllegalStateException.class, () -> engine.openGenerationRuntimeScope(closed));

        IrisEngine.GenerationRuntimeBinding firstBinding = detachedBinding(engine, first.runtime);
        IrisEngine.GenerationRuntimeBinding secondBinding = detachedBinding(engine, second.runtime);
        IrisEngine.GenerationRuntimeScope outer = engine.openGenerationRuntimeScope(firstBinding);
        IrisEngine.GenerationRuntimeScope inner = engine.openGenerationRuntimeScope(secondBinding);
        assertThrows(IllegalStateException.class, outer::close);
        inner.close();
        outer.close();
        assertSame(active.target, engine.getTarget());
    }

    @Test
    public void closingDetachedRuntimeReleasesGenerationTargetAndData() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture detached = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding binding = detachedBinding(engine, detached.runtime);
        doAnswer(invocation -> {
            assertSame(detached.mantle, engine.getMantle());
            return null;
        }).when(detached.mode).close();

        engine.closeDetachedGenerationRuntime(binding);

        verify(detached.mode).close();
        verify(detached.complex).close();
        verify(detached.mantle).saveAllNow();
        verify(detached.mantle).close();
        verify(detached.data).unregisterEngine(engine);
        verify(detached.target).close();
        verify(detached.data).close();
        assertTrue(detached.hash32.isCancelled());
        engine.closeDetachedGenerationRuntime(binding);
    }

    @Test
    public void detachedRuntimeRetirementEvictsCachesOutsideLifecycleLock() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture detached = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding binding = detachedBinding(engine, detached.runtime);
        AtomicInteger retiredRuntime = new AtomicInteger(-1);
        AtomicBoolean lifecycleLockHeld = new AtomicBoolean(true);
        engine.addGenerationRuntimeRetirementListener(runtimeId -> {
            retiredRuntime.set(runtimeId);
            lifecycleLockHeld.set(Thread.holdsLock(engine.lifecycleLock));
        });
        doAnswer(invocation -> {
            assertEquals(2, retiredRuntime.get());
            return null;
        }).when(detached.mode).close();

        engine.closeDetachedGenerationRuntime(binding);

        assertEquals(2, binding.runtimeId());
        assertEquals(2, retiredRuntime.get());
        assertTrue(!lifecycleLockHeld.get());
    }

    @Test
    public void detachedRuntimeRejectsNewScopesAfterRetirementBegins() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture detached = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding binding = detachedBinding(engine, detached.runtime);
        AtomicReference<Throwable> rejected = new AtomicReference<>();
        engine.addGenerationRuntimeRetirementListener(runtimeId -> {
            try {
                engine.openGenerationRuntimeScope(binding);
            } catch (Throwable failure) {
                rejected.set(failure);
            }
        });

        engine.closeDetachedGenerationRuntime(binding);

        assertTrue(rejected.get() instanceof IllegalStateException);
        assertTrue(rejected.get().getMessage().contains("retiring"));
    }

    @Test
    public void closedDetachedScopeFallsBackToTheBaseRuntime() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture detached = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding binding = detachedBinding(engine, detached.runtime);

        try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(binding)) {
            assertSame(detached.target, engine.getTarget());
            engine.closeDetachedGenerationRuntime(binding);
            assertSame(active.target, engine.getTarget());
            assertSame(active.mantle, engine.getMantle());
        }
    }

    @Test
    public void defaultRuntimeSelectionKeepsThePreviousRuntimeRoutable() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture detached = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisEngine.GenerationRuntimeBinding activeBinding = new IrisEngine.GenerationRuntimeBinding(
                engine,
                active.runtime);
        IrisEngine.GenerationRuntimeBinding detachedRuntimeBinding = detachedBinding(engine, detached.runtime);

        engine.setDefaultGenerationRuntime(detachedRuntimeBinding);

        assertSame(detached.target, engine.getTarget());
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.closeDetachedGenerationRuntime(detachedRuntimeBinding));
        try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(activeBinding)) {
            assertSame(active.target, engine.getTarget());
        }

        engine.setDefaultGenerationRuntime(activeBinding);

        assertSame(active.target, engine.getTarget());
        try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(detachedRuntimeBinding)) {
            assertSame(detached.target, engine.getTarget());
        }
        engine.closeDetachedGenerationRuntime(detachedRuntimeBinding);
        assertThrows(
                IllegalStateException.class,
                () -> engine.openGenerationRuntimeScope(detachedRuntimeBinding));
    }

    @Test
    public void attachedHistoryRouterIsClearedWhenEngineShutdownBegins() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        GenerationHistoryRuntimeRouter router = historyRouter(engine);
        engine.attachGenerationHistoryRuntimeRouter(router);

        assertTrue(engine.beginShutdown());
        engine.closeAttachedGenerationHistoryRuntimeRouter();
        engine.closeAttachedGenerationHistoryRuntimeRouter();

        verify(router).close();
    }

    @Test
    public void attachedHistoryRejectsGenerationRuntimeHotload() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        GenerationHistoryRuntimeRouter router = historyRouter(engine);
        engine.attachGenerationHistoryRuntimeRouter(router);

        IllegalStateException failure = assertThrows(IllegalStateException.class, engine::hotloadComplex);

        assertTrue(failure.getMessage().contains("immutable Iris generation history"));
        assertSame(active.runtime, engine.runtime.generation());
        verify(active.complex, never()).close();
    }

    @Test
    public void detachedHistoryCannotFallBackToTheDefaultGenerationRuntime() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        assertNull(engine.openGenerationHistoryCoordinateScope(0, 0));
        GenerationHistoryRuntimeRouter router = historyRouter(engine);
        engine.attachGenerationHistoryRuntimeRouter(router);
        engine.detachGenerationHistoryRuntimeRouter(router);

        assertThrows(IllegalStateException.class, () -> engine.openGenerationHistoryCoordinateScope(0, 0));
        assertThrows(IllegalStateException.class, () -> engine.getHeight(0, 0));
    }

    @Test
    public void publishedRouterLookupDoesNotWaitForTheAttachmentMonitor() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        GenerationHistoryRuntimeRouter router = historyRouter(engine);
        engine.attachGenerationHistoryRuntimeRouter(router);
        FutureTask<Optional<GenerationHistoryRuntimeRouter>> read = new FutureTask<>(engine::getGenerationHistoryRuntimeRouter);
        Thread reader = new Thread(read, "History router lookup");

        try {
            synchronized (engine.generationHistoryRuntimeRouterLock) {
                reader.start();
                assertSame(router, read.get(5, TimeUnit.SECONDS).orElseThrow());
            }
        } finally {
            reader.join(5_000L);
            assertTrue("Router lookup did not finish", !reader.isAlive());
        }
    }

    @Test
    public void publishedCoordinateReadDoesNotWaitForTheAttachmentMonitor() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        GenerationHistoryRuntimeRouter router = historyRouter(engine);
        engine.attachGenerationHistoryRuntimeRouter(router);
        GenerationHistoryRuntimeRouter.CoordinateScope scope = mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
        when(router.openCoordinateScope(-17, 32)).thenReturn(scope);
        FutureTask<GenerationHistoryRuntimeRouter.CoordinateScope> read =
                new FutureTask<>(() -> engine.openGenerationHistoryCoordinateScope(-17, 32));
        Thread reader = new Thread(read, "History coordinate read");

        try {
            synchronized (engine.generationHistoryRuntimeRouterLock) {
                reader.start();
                assertSame(scope, read.get(5, TimeUnit.SECONDS));
            }
        } finally {
            reader.join(5_000L);
            assertTrue("Coordinate read did not finish", !reader.isAlive());
        }
        verify(router).openCoordinateScope(-17, 32);
    }

    @Test
    public void missingRouterReadWaitsForAttachmentPublication() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        GenerationHistoryRuntimeRouter router = historyRouter(engine);
        GenerationHistoryRuntimeRouter.CoordinateScope scope = mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
        when(router.openCoordinateScope(-17, 32)).thenReturn(scope);
        CountDownLatch started = new CountDownLatch(1);
        FutureTask<GenerationHistoryRuntimeRouter.CoordinateScope> read = new FutureTask<>(() -> {
            started.countDown();
            return engine.openGenerationHistoryCoordinateScope(-17, 32);
        });
        Thread reader = new Thread(read, "Pending history attachment");

        try {
            synchronized (engine.generationHistoryRuntimeRouterLock) {
                reader.start();
                assertTrue(started.await(5, TimeUnit.SECONDS));
                awaitBlocked(reader);
                engine.attachGenerationHistoryRuntimeRouter(router);
            }
            assertSame(scope, read.get(5, TimeUnit.SECONDS));
        } finally {
            reader.join(5_000L);
            assertTrue("Pending router read did not finish", !reader.isAlive());
        }
    }

    @Test
    public void detachedRouterReadRemainsFailClosedAcrossThreads() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        GenerationHistoryRuntimeRouter router = historyRouter(engine);
        engine.attachGenerationHistoryRuntimeRouter(router);
        CountDownLatch started = new CountDownLatch(1);
        FutureTask<GenerationHistoryRuntimeRouter.CoordinateScope> read = new FutureTask<>(() -> {
            started.countDown();
            return engine.openGenerationHistoryCoordinateScope(-17, 32);
        });
        Thread reader = new Thread(read, "Detached history read");

        try {
            synchronized (engine.generationHistoryRuntimeRouterLock) {
                engine.detachGenerationHistoryRuntimeRouter(router);
                reader.start();
                assertTrue(started.await(5, TimeUnit.SECONDS));
                awaitBlocked(reader);
            }
            ExecutionException failure = assertThrows(ExecutionException.class, () -> read.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertEquals("Iris generation-history runtime router is detached.", failure.getCause().getMessage());
            assertTrue(engine.getGenerationHistoryRuntimeRouter().isEmpty());
        } finally {
            reader.join(5_000L);
            assertTrue("Detached router read did not finish", !reader.isAlive());
        }
        verify(router, never()).openCoordinateScope(anyInt(), anyInt());
    }

    @Test
    public void transferredMantleIsNotClosedWithTheRetiredRuntime() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        EngineShutdownSequence shutdownSequence = new EngineShutdownSequence(engine);

        Throwable failure = shutdownSequence.closeRuntime(engine.runtime, active.mantle, null);

        assertNull(failure);
        verify(active.mantle, never()).saveAllNow();
        verify(active.mantle, never()).close();
    }

    @Test
    public void ownedAssemblyMantleIsSavedAndClosedOnlyOnce() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        EngineRuntimeBuilder.RuntimeAssembly assembly = new EngineRuntimeBuilder.RuntimeAssembly(
                2,
                active.target,
                Path.of("build", "test-mantles", "assembly"));
        assembly.mantle = mock(EngineMantle.class);
        assembly.ownsMantle = true;
        EngineShutdownSequence shutdownSequence = new EngineShutdownSequence(engine);

        assertNull(shutdownSequence.closeAssembly(assembly, null));
        assertNull(shutdownSequence.closeAssembly(assembly, null));

        verify(assembly.mantle).saveAllNow();
        verify(assembly.mantle).close();
    }

    @Test
    public void detachedRuntimeRejectsTheActiveMantleDirectory() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture detached = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisWorld activeWorld = active.target.getWorld();
        when(detached.target.getWorld()).thenReturn(activeWorld);
        setField(engine, "closing", new AtomicBoolean(false));
        engine.lifecycleState = IrisEngine.LifecycleState.RUNNING;

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.buildDetachedGenerationRuntime(detached.target, active.mantleStorageDirectory));

        verify(detached.data, never()).registerEngine(engine);
    }

    @Test
    public void failedDetachedBuildReleasesTransferredTargetOwnership() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture detached = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        IrisWorld activeWorld = active.target.getWorld();
        when(detached.target.getWorld()).thenReturn(activeWorld);
        setField(engine, "closing", new AtomicBoolean(false));
        engine.lifecycleState = IrisEngine.LifecycleState.RUNNING;
        EngineRuntimeBuilder runtimeBuilder = mock(EngineRuntimeBuilder.class);
        setField(engine, "runtimeBuilder", runtimeBuilder);
        IllegalStateException failure = new IllegalStateException("build failed");
        Path storageDirectory = Path.of("build", "test-mantles", "failed-detached").toAbsolutePath();
        when(runtimeBuilder.buildDetachedGenerationRuntime(detached.target, storageDirectory)).thenThrow(failure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> engine.buildDetachedGenerationRuntime(detached.target, storageDirectory));

        assertSame(failure, thrown);
        verify(detached.data).registerEngine(engine);
        verify(detached.data).unregisterEngine(engine);
        verify(detached.target).close();
        verify(detached.data).close();
    }

    @Test
    public void savedBiomesAnswerWithoutOpeningGenerationRoutes() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        when(active.target.getWorld().minHeight()).thenReturn(-64);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        when(router.biomes()).thenReturn(saved);
        setField(engine, "generationHistoryRuntimeRouter", router);
        BiomeEnvironment environment = new BiomeEnvironment(4L, mock(IrisBiome.class),
                mock(IrisRegion.class), mock(IrisDimension.class), mock(IrisData.class));
        when(saved.resolve(19, -44, -3, false)).thenReturn(Optional.of(environment));
        when(saved.resolve(19, -64, -3, true)).thenReturn(Optional.of(environment));
        when(saved.resolveCaveBase(19, -3)).thenReturn(Optional.of(environment));

        assertSame(environment, engine.getBiomeEnvironment(19, 20, -3));
        assertSame(environment, engine.getBiomeOrMantleEnvironment(19, 20, -3));
        assertSame(environment, engine.getSurfaceBiomeEnvironment(19, -3));
        assertSame(environment.biome(), engine.getBiome(19, 20, -3));
        assertSame(environment.biome(), engine.getBiomeOrMantle(19, 20, -3));
        assertSame(environment.biome(), engine.getCaveOrMantleBiome(19, 20, -3));
        assertSame(environment.biome(), engine.getCaveBiome(19, 20, -3));
        assertSame(environment.biome(), engine.getCaveBiome(19, -3));
        assertSame(environment.biome(), engine.getSurfaceBiome(19, -3));
        assertSame(environment.region(), engine.getRegion(19, -3));
        assertSame(environment.region(), engine.getRegion(19, 20, -3));
        verify(router, never()).openCoordinateScope(anyInt(), anyInt());
    }

    @Test
    public void unavailableSavedBiomesNeverUseTheActiveGenerator() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        when(router.biomes()).thenReturn(saved);
        setField(engine, "generationHistoryRuntimeRouter", router);
        SavedBiomeUnavailableException failure = new SavedBiomeUnavailableException("Missing historical biome", false);
        when(saved.resolve(19, 20, -3, false)).thenThrow(failure);

        assertSame(failure, assertThrows(SavedBiomeUnavailableException.class,
                () -> engine.getBiome(19, 20, -3)));
        verify(router, never()).openCoordinateScope(anyInt(), anyInt());
    }

    @Test
    public void definitionScopesRestoreAndLeaveGenerationRuntimeUnchanged() throws Exception {
        RuntimeFixture active = runtime(1, 1D, 1D, 1D);
        RuntimeFixture selected = runtime(2, 2D, 2D, 2D);
        IrisEngine engine = engine(active.runtime, mock(EngineEffects.class), mock(EngineWorldManager.class));
        BiomeEnvironment first = new BiomeEnvironment(4L, mock(IrisBiome.class),
                mock(IrisRegion.class), mock(IrisDimension.class), mock(IrisData.class));
        BiomeEnvironment second = new BiomeEnvironment(5L, mock(IrisBiome.class),
                mock(IrisRegion.class), mock(IrisDimension.class), mock(IrisData.class));

        try (BiomeEnvironment.Scope outer = engine.openBiomeEnvironmentScope(first)) {
            assertSame(first.data(), engine.getData());
            assertSame(first.dimension(), engine.getDimension());
            assertSame(active.complex, engine.getComplex());
            try (BiomeEnvironment.Scope inner = engine.openBiomeEnvironmentScope(second)) {
                assertSame(second.data(), engine.getData());
                assertThrows(IllegalStateException.class, outer::close);
                CompletableFuture.runAsync(() -> assertThrows(IllegalStateException.class, inner::close))
                        .get(5, TimeUnit.SECONDS);
            }
            assertSame(first.data(), engine.getData());
            try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(
                    detachedBinding(engine, selected.runtime))) {
                assertSame(selected.data, engine.getData());
                assertSame(selected.dimension, engine.getDimension());
                assertSame(selected.complex, engine.getComplex());
            }
            assertSame(first.data(), engine.getData());
        }
        assertSame(active.data, engine.getData());
        assertSame(active.dimension, engine.getDimension());
    }

    private static IrisBiome carvingBiome(IrisEngine engine, IrisDimensionCarvingResolver.State state) {
        IrisDimensionCarvingEntry root = IrisDimensionCarvingResolver.resolveRootEntry(engine, 80, state);
        IrisDimensionCarvingEntry child = IrisDimensionCarvingResolver.resolveFromRoot(engine, root, 19, -3, state);
        return IrisDimensionCarvingResolver.resolveEntryBiome(engine, child, state);
    }

    private static GenerationHistoryRuntimeRouter historyRouter(IrisEngine engine) {
        GenerationHistory history = mock(GenerationHistory.class);
        when(history.retainRuntime()).thenReturn(mock(GenerationAdmission.RuntimeLease.class));
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(router.biomes()).thenReturn(mock(SavedBiomeRuntime.class));
        when(router.engine()).thenReturn(engine);
        when(router.history()).thenReturn(history);
        return router;
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (thread.getState() != Thread.State.BLOCKED && thread.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        assertEquals(Thread.State.BLOCKED, thread.getState());
    }

    private static IrisEngine engine(
            GenerationRuntime generationRuntime,
            EngineEffects effects,
            EngineWorldManager worldManager
    ) throws Exception {
        IrisEngine engine = mock(IrisEngine.class, CALLS_REAL_METHODS);
        setField(engine, "lifecycleLock", new Object());
        setField(engine, "generationHistoryRuntimeRouterLock", new Object());
        setField(engine, "runtimeAssembly", new ThreadLocal<EngineRuntimeBuilder.RuntimeAssembly>());
        setField(engine, "biomeEnvironmentScopes", new ThreadLocal<Object>());
        setField(engine, "generationRuntimeScopes", new GenerationRuntimeScopeState());
        Set<GenerationRuntime> detached = Collections.synchronizedSet(
                Collections.newSetFromMap(new IdentityHashMap<GenerationRuntime, Boolean>()));
        setField(engine, "detachedGenerationRuntimes", detached);
        Set<GenerationRuntime> retiring = Collections.synchronizedSet(
                Collections.newSetFromMap(new IdentityHashMap<GenerationRuntime, Boolean>()));
        setField(engine, "retiringGenerationRuntimes", retiring);
        setField(engine, "generationRuntimeRetirementListeners", new CopyOnWriteArraySet<IntConsumer>());
        setField(engine, "shutdownSequence", new EngineShutdownSequence(engine));
        setField(engine, "hotloader", new EngineHotloader(engine));
        setField(engine, "nativeStructureVolumeMemo", new NativeStructureVolumeMemo());
        setField(engine, "closing", new AtomicBoolean(false));
        engine.lifecycleState = IrisEngine.LifecycleState.RUNNING;
        engine.runtime = new EngineRuntime(generationRuntime, effects, worldManager);
        engine.publishedTarget = generationRuntime.target();
        return engine;
    }

    @SuppressWarnings("unchecked")
    private static IrisEngine.GenerationRuntimeBinding detachedBinding(
            IrisEngine engine,
            GenerationRuntime runtime
    ) throws Exception {
        Field field = IrisEngine.class.getDeclaredField("detachedGenerationRuntimes");
        field.setAccessible(true);
        Set<GenerationRuntime> detached = (Set<GenerationRuntime>) field.get(engine);
        detached.add(runtime);
        return new IrisEngine.GenerationRuntimeBinding(engine, runtime);
    }

    private static RuntimeFixture runtime(
            int cacheId,
            double objectDensity,
            double layerDensity,
            double decoratorDensity
    ) throws IOException {
        return runtime(
                cacheId,
                objectDensity,
                layerDensity,
                decoratorDensity,
                GenerationKernelRegistry.standard().current()
        );
    }

    private static RuntimeFixture runtime(
            int cacheId,
            double objectDensity,
            double layerDensity,
            double decoratorDensity,
            GenerationKernelRegistry.Version kernelVersion
    ) throws IOException {
        GenerationKernelRegistry.RuntimeKernel runtimeKernel = runtimeKernel(kernelVersion);
        return runtime(
                cacheId,
                objectDensity,
                layerDensity,
                decoratorDensity,
                runtimeKernel,
                mock(IrisComplex.class)
        );
    }

    private static RuntimeFixture runtime(
            int cacheId,
            double objectDensity,
            double layerDensity,
            double decoratorDensity,
            GenerationKernelRegistry.RuntimeKernel runtimeKernel,
            IrisComplex complex
    ) {
        IrisWorld world = mock(IrisWorld.class);
        IrisData data = mock(IrisData.class);
        IrisDimension dimension = mock(IrisDimension.class);
        EngineTarget target = mock(EngineTarget.class);
        when(target.getWorld()).thenReturn(world);
        when(target.getDimension()).thenReturn(dimension);
        when(target.getData()).thenReturn(data);
        when(target.getBurster()).thenReturn(MultiBurst.burst);
        UpperDimensionContext upperContext = mock(UpperDimensionContext.class);
        DimensionStackContext dimensionStackContext = mock(DimensionStackContext.class);
        EngineMode mode = mock(EngineMode.class);
        EngineMantle mantle = mock(EngineMantle.class);
        Path mantleStorageDirectory = Path.of("build", "test-mantles", Integer.toString(cacheId)).toAbsolutePath();
        CompletableFuture<Long> hash32 = new CompletableFuture<>();
        GenerationRuntime runtime = new GenerationRuntime(
                cacheId,
                target,
                data,
                dimension,
                runtimeKernel.version(),
                runtimeKernel,
                mock(SeedManager.class),
                null,
                complex,
                upperContext,
                dimensionStackContext,
                mode,
                mantle,
                mantleStorageDirectory,
                hash32,
                new BiomeMaxes(objectDensity, layerDensity, decoratorDensity));
        return new RuntimeFixture(
                runtime,
                target,
                data,
                dimension,
                complex,
                upperContext,
                dimensionStackContext,
                mode,
                mantle,
                mantleStorageDirectory,
                hash32);
    }

    private static GenerationKernelRegistry.RuntimeKernel runtimeKernel(
            GenerationKernelRegistry.Version version
    ) throws IOException {
        if (GenerationKernelRegistry.standard().supports(
                version.generatorAbi(),
                version.rngVersion(),
                version.seedDerivationVersion())) {
            return GenerationKernelRegistry.standard().select(version);
        }
        return new GenerationKernelRegistry.RuntimeKernel(
                version,
                "f".repeat(64),
                (engine, transitionPlan) -> mock(IrisComplex.class)
        );
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = IrisEngine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record RuntimeFixture(
            GenerationRuntime runtime,
            EngineTarget target,
            IrisData data,
            IrisDimension dimension,
            IrisComplex complex,
            UpperDimensionContext upperContext,
            DimensionStackContext dimensionStackContext,
            EngineMode mode,
            EngineMantle mantle,
            Path mantleStorageDirectory,
            CompletableFuture<Long> hash32
    ) {
    }
}
